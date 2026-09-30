package com.photoexhibition.service;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.ProcessingStatus;
import com.photoexhibition.entity.BackgroundJob;
import com.photoexhibition.entity.BackgroundJobResourceLane;
import com.photoexhibition.entity.BackgroundJobStatus;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.repository.BackgroundJobRepository;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class ModelManagementService {

    private static final Set<String> FACE_DETECTION_KEYS = Set.of("face_detection");
    private static final Set<String> FACE_EMBEDDING_KEYS = Set.of("face_recognition");
    private static final Set<String> AI_ANALYSIS_KEYS = Set.of("image_classification", "saliency_detection");
    private static final Set<String> BACKGROUND_REMOVAL_KEYS = Set.of("background_removal");

    private final FaceDetectionService faceDetectionService;
    private final FaceEmbeddingService faceEmbeddingService;
    private final ImageClassificationService imageClassificationService;
    private final SaliencyDetectionService saliencyDetectionService;
    private final SceneRecognitionService sceneRecognitionService;
    private final EmotionAnalysisService emotionAnalysisService;
    private final BackgroundRemovalService backgroundRemovalService;
    private final PhotoScanService photoScanService;
    private final PhotoAIScoringService photoAIScoringService;
    private final SmartTagService smartTagService;
    private final PhotoRepository photoRepository;
    private final FaceRepository faceRepository;
    private final UserPathService userPathService;

    @Autowired @Lazy
    private BackgroundJobService backgroundJobService;
    @Autowired
    private BackgroundJobRepository backgroundJobRepository;

    private final ConcurrentHashMap<String, ValidationSnapshot> latestValidationByModel = new ConcurrentHashMap<>();

    public List<Map<String, Object>> listModels() {
        List<Map<String, Object>> models = new ArrayList<>();
        models.add(buildModel("face_detection", "人脸检测模型", "Face Detection", faceDetectionService.getModelPath(), faceDetectionService.isEnabled(), faceDetectionService.isModelLoaded(),
            List.of("在线下载", "验证后启用", "尝试无人脸照片补检", "彻底重建并尽量继承人物绑定")));
        models.add(buildModel("face_recognition", "人脸特征模型", "Face Recognition", faceEmbeddingService.getModelPath(), true, faceEmbeddingService.isModelLoaded(),
            List.of("在线下载", "验证后启用", "重建 embedding", "可联动补做人脸检测")));
        models.add(buildModel("image_classification", "图像分类模型", "Image Classification", imageClassificationService.getModelPath(), imageClassificationService.isEnabled(), imageClassificationService.isModelLoaded(),
            List.of("在线下载", "验证后启用", "重建智能标签", "联动 AI 评分重算")));
        models.add(buildModel("saliency_detection", "显著性检测模型", "Saliency Detection", saliencyDetectionService.getModelPath(), saliencyDetectionService.isEnabled(), saliencyDetectionService.isModelLoaded(),
            List.of("在线下载", "验证后启用", "重建 AI 分析与构图相关结果")));
        models.add(buildModel("background_removal", "背景移除模型", "Background Removal", backgroundRemovalService.getModelPath(), backgroundRemovalService.isEnabled(), backgroundRemovalService.isModelAvailable(),
            List.of("在线下载", "验证后启用", "补跑未抠图照片", "彻底重建抠图缓存")));
        models.sort(Comparator.comparing(item -> String.valueOf(item.get("key"))));
        return models;
    }

    public Map<String, Object> downloadModel(String key, String sourceUrl) {
        ModelRuntime runtime = getRuntime(key);
        URI uri = validateDownloadUrl(sourceUrl);
        Path target = resolveModelPath(runtime.modelPath);
        Path tempFile = null;
        try {
            Files.createDirectories(target.getParent());
            tempFile = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".downloading");
            DownloadSnapshot download = downloadTo(uri.toURL(), tempFile);
            ValidationSnapshot validation = validateOnnxFile(tempFile);
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            boolean activated = runtime.reloader.reload();

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("key", runtime.key);
            resp.put("name", runtime.name);
            resp.put("path", userPathService.toDisplayPath(target.toString(), true));
            resp.put("sizeBytes", download.sizeBytes);
            resp.put("sha256", download.sha256);
            resp.put("validated", true);
            resp.put("validation", validation.toMap());
            resp.put("activated", activated);
            resp.put("active", runtime.loader.loaded());
            resp.put("message", activated ? "模型下载、验证并启用成功" : "模型下载并验证成功，但启用失败，请检查运行日志");
            latestValidationByModel.put(runtime.key, validation);
            return resp;
        } catch (Exception e) {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
            throw new RuntimeException("模型下载失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> reloadModel(String key) {
        ModelRuntime runtime = getRuntime(key);
        boolean loaded = runtime.reloader.reload();
        return Map.of(
            "key", runtime.key,
            "name", runtime.name,
            "active", loaded,
            "message", loaded ? "模型已重新加载" : "模型重载失败，请检查文件与日志"
        );
    }

    /** Execute one already scanned photo; the durable dispatcher owns retries and progress. */
    public void rebuildPhoto(String modelKey, Long photoId, boolean includeMissingItems,
                             boolean forceRebuild, boolean preserveBindings) {
        String key = getRuntime(modelKey).key;
        Photo photo = photoRepository.findById(photoId)
            .orElseThrow(() -> new IllegalArgumentException("照片不存在"));
        if (photo.getProcessingStatus() != ProcessingStatus.COMPLETED) {
            throw new IllegalStateException("照片尚未完成扫描");
        }
        if (FACE_DETECTION_KEYS.contains(key)) {
            boolean hasFaces = !faceRepository.findByPhotoId(photoId).isEmpty();
            if ((!includeMissingItems && !hasFaces) || (!forceRebuild && hasFaces)) return;
            checkRebuildResult(photoScanService.rescanFacesForPhoto(photoId, preserveBindings));
        } else if (FACE_EMBEDDING_KEYS.contains(key)) {
            boolean hasFaces = !faceRepository.findByPhotoId(photoId).isEmpty();
            if ((hasFaces && forceRebuild && !preserveBindings) || (!hasFaces && includeMissingItems)) {
                // Face detection already regenerates embeddings for newly detected faces.
                checkRebuildResult(photoScanService.rescanFacesForPhoto(photoId, preserveBindings));
            } else if (hasFaces) {
                checkRebuildResult(photoScanService.rebuildFaceEmbeddingsForPhoto(photoId));
            }
        } else if (AI_ANALYSIS_KEYS.contains(key)) {
            Path imagePath = resolveLocalPhoto(photo);
            if (imagePath == null || !Files.exists(imagePath)) throw new IllegalStateException("源文件不存在");
            int faceCount = faceRepository.findByPhotoId(photoId).size();
            smartTagService.applySmartTags(imagePath.toFile(), photo, faceCount, true, Set.of());
            photoAIScoringService.rescorePhoto(photo);
        } else if (BACKGROUND_REMOVAL_KEYS.contains(key)) {
            boolean hasRemoved = photo.getBackgroundRemovedPath() != null && !photo.getBackgroundRemovedPath().isBlank();
            if ((!includeMissingItems && !hasRemoved) || (!forceRebuild && hasRemoved)) return;
            checkRebuildResult(photoScanService.removeBackgroundForPhoto(photoId, forceRebuild));
        }
    }

    private void checkRebuildResult(Map<String, Object> result) {
        if (result == null || result.containsKey("error")) {
            throw new IllegalStateException(result == null ? "重建未返回结果" : String.valueOf(result.get("error")));
        }
    }

    public Map<String, Object> triggerRebuild(UserAccount operator, String key, boolean includeMissingItems,
                                               boolean forceRebuild, boolean preserveBindings) {
        if (!includeMissingItems && !forceRebuild) {
            throw new RuntimeException("请至少选择一种重建策略：尝试无数据项 或 彻底重建");
        }
        ModelRuntime runtime = getRuntime(key);
        Map<Long, List<Long>> byOwner = new LinkedHashMap<>();
        int unscanned = 0;
        int unowned = 0;
        int page = 0;
        Page<Photo> photos;
        do {
            photos = photoRepository.findAll(PageRequest.of(page++, 100, Sort.by("id")));
            for (Photo photo : photos.getContent()) {
                if (photo.getProcessingStatus() != ProcessingStatus.COMPLETED) { unscanned++; continue; }
                if (photo.getUserId() == null) { unowned++; continue; }
                if (!eligibleForRebuild(runtime.key, photo, includeMissingItems, forceRebuild)) continue;
                byOwner.computeIfAbsent(photo.getUserId(), ignored -> new ArrayList<>()).add(photo.getId());
            }
        } while (photos.hasNext());
        List<Map<String, Object>> jobs = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : byOwner.entrySet()) {
            jobs.add(backgroundJobService.enqueuePhotoJob(operator, entry.getKey(),
                modelJobType(runtime.key), runtime.key.equals("background_removal")
                    ? BackgroundJobResourceLane.LOCAL_GPU_AI : BackgroundJobResourceLane.LOCAL_CPU_AI,
                entry.getValue(), forceRebuild, true, 100, "1", null,
                Map.of("modelKey", runtime.key, "includeMissingItems", includeMissingItems,
                    "forceRebuild", forceRebuild, "preserveBindings", preserveBindings)));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("modelKey", runtime.key);
        response.put("jobs", jobs);
        response.put("notScannedItems", unscanned);
        response.put("unownedItems", unowned);
        response.put("message", jobs.isEmpty() ? "没有满足条件的已扫描照片" : "已按账号创建 " + jobs.size() + " 个重建任务");
        if (!jobs.isEmpty()) {
            Map<String, Object> task = getTask("model-job-" + jobs.get(0).get("id"));
            response.put("taskId", task.get("taskId"));
            response.put("task", task);
        }
        return response;
    }

    private String modelJobType(String key) {
        return "MODEL_REBUILD_" + key.toUpperCase(java.util.Locale.ROOT);
    }

    private boolean eligibleForRebuild(String key, Photo photo, boolean includeMissing, boolean force) {
        if (FACE_DETECTION_KEYS.contains(key)) {
            boolean hasFaces = !faceRepository.findByPhotoId(photo.getId()).isEmpty();
            return (hasFaces && force) || (!hasFaces && includeMissing);
        }
        if (FACE_EMBEDDING_KEYS.contains(key)) {
            return includeMissing || !faceRepository.findByPhotoId(photo.getId()).isEmpty();
        }
        if (BACKGROUND_REMOVAL_KEYS.contains(key)) {
            boolean hasResult = photo.getBackgroundRemovedPath() != null && !photo.getBackgroundRemovedPath().isBlank();
            return (hasResult && force) || (!hasResult && includeMissing);
        }
        return true;
    }

    public Map<String, Object> getTask(String taskId) {
        if (taskId != null && taskId.startsWith("model-job-")) {
            long id = Long.parseLong(taskId.substring("model-job-".length()));
            BackgroundJob job = backgroundJobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
            if (!job.getJobType().startsWith("MODEL_REBUILD_")) throw new IllegalArgumentException("任务类型不匹配");
            return toModelTask(job);
        }
        throw new IllegalArgumentException("任务不存在");
    }

    public Map<String, Object> getTaskOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> recentTasks = new ArrayList<>();
        for (String key : List.of("face_detection", "face_recognition", "image_classification",
            "saliency_detection", "background_removal")) {
            recentTasks.addAll(listTaskHistory(key, 8));
        }
        recentTasks.sort((left, right) -> {
            LocalDateTime l = (LocalDateTime) left.get("createdAt");
            LocalDateTime r = (LocalDateTime) right.get("createdAt");
            return java.util.Comparator.nullsLast(java.util.Comparator.<LocalDateTime>naturalOrder()).compare(r, l);
        });
        if (recentTasks.size() > 8) recentTasks = new ArrayList<>(recentTasks.subList(0, 8));
        long runningCount = backgroundJobRepository.countByJobTypeStartingWithAndStatusIn(
            "MODEL_REBUILD_", List.of(BackgroundJobStatus.RUNNING));
        long queuedCount = backgroundJobRepository.countByJobTypeStartingWithAndStatusIn(
            "MODEL_REBUILD_", List.of(BackgroundJobStatus.QUEUED, BackgroundJobStatus.WAITING_DEPENDENCY));
        long completedCount = backgroundJobRepository.countByJobTypeStartingWithAndStatusIn(
            "MODEL_REBUILD_", List.of(BackgroundJobStatus.SUCCEEDED, BackgroundJobStatus.PARTIAL_SUCCESS,
                BackgroundJobStatus.FAILED, BackgroundJobStatus.SKIPPED, BackgroundJobStatus.CANCELED));

        result.put("threadType", "MODEL_REBUILD");
        result.put("label", "模型重建线程");
        result.put("runningTaskCount", runningCount);
        result.put("activeThreads", runningCount);
        result.put("queuedTasks", queuedCount);
        result.put("completedTaskCount", completedCount);
        result.put("recentTasks", recentTasks);
        return result;
    }

    private Map<String, Object> buildModel(String key, String name, String code, String configuredPath, boolean enabled, boolean loaded, List<String> rebuildNotes) {
        Path resolved = resolveModelPath(configuredPath);
        boolean fileExists = Files.exists(resolved);
        long sizeBytes = fileExists ? safeFileSize(resolved) : 0L;
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("key", key);
        model.put("name", name);
        model.put("code", code);
        model.put("configuredPath", configuredPath);
        model.put("resolvedPath", userPathService.toDisplayPath(resolved.toString(), true));
        model.put("fileExists", fileExists);
        model.put("sizeBytes", sizeBytes);
        model.put("enabled", enabled);
        model.put("active", loaded);
        model.put("rebuildNotes", rebuildNotes);
        model.put("latestValidation", Optional.ofNullable(latestValidationByModel.get(key)).map(ValidationSnapshot::toMap).orElse(null));
        List<Map<String, Object>> history = listTaskHistory(key, 5);
        model.put("latestTask", history.isEmpty() ? null : history.get(0));
        model.put("taskHistory", history);
        return model;
    }

    public Map<String, Object> getModelSummary() {
        List<Map<String, Object>> models = listModels();
        long missingFileCount = models.stream().filter(model -> !Boolean.TRUE.equals(model.get("fileExists"))).count();
        long inactiveCount = models.stream().filter(model -> !Boolean.TRUE.equals(model.get("active"))).count();
        return Map.of(
            "modelCount", models.size(),
            "missingFileCount", missingFileCount,
            "inactiveModelCount", inactiveCount,
            "healthy", missingFileCount == 0 && inactiveCount == 0,
            "models", models
        );
    }

    private List<Map<String, Object>> listTaskHistory(String modelKey, int limit) {
        return backgroundJobRepository.findTop8ByJobTypeOrderByCreatedAtDesc(modelJobType(modelKey)).stream()
            .limit(limit).map(this::toModelTask).collect(java.util.stream.Collectors.toList());
    }

    private Map<String, Object> toModelTask(BackgroundJob job) {
        Map<String, Object> data = new LinkedHashMap<>();
        String key = job.getJobType().substring("MODEL_REBUILD_".length()).toLowerCase(java.util.Locale.ROOT);
        data.put("taskId", "model-job-" + job.getId());
        data.put("modelKey", key);
        data.put("modelName", getRuntime(key).name);
        try {
            Map<?, ?> options = new com.fasterxml.jackson.databind.ObjectMapper().readValue(job.getParametersJson(), Map.class);
            data.put("includeMissingItems", options.get("includeMissingItems"));
            data.put("forceRebuild", options.get("forceRebuild"));
            data.put("preserveBindings", options.get("preserveBindings"));
        } catch (Exception exception) {
            throw new IllegalStateException("任务参数无法读取", exception);
        }
        BackgroundJobStatus status = job.getStatus();
        data.put("status", status == BackgroundJobStatus.SUCCEEDED ? "SUCCESS" : status.name());
        data.put("message", job.getErrorSummary() == null ? status.name() : job.getErrorSummary());
        data.put("complete", status.isTerminal());
        data.put("total", job.getTotalItems());
        data.put("processed", job.getSucceededItems());
        data.put("skipped", job.getSkippedItems());
        data.put("failed", job.getFailedItems());
        data.put("createdAt", job.getCreatedAt());
        data.put("startedAt", job.getStartedAt());
        data.put("finishedAt", job.getFinishedAt());
        data.put("logs", job.getErrorSummary() == null ? List.of() : List.of(job.getErrorSummary()));
        return data;
    }

    private long safeFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception e) {
            return 0L;
        }
    }

    private URI validateDownloadUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new RuntimeException("下载 URL 不能为空");
        }
        URI uri = URI.create(sourceUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new RuntimeException("仅支持 http/https 下载 URL");
        }
        return uri;
    }

    private DownloadSnapshot downloadTo(URL url, Path target) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(300000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "PhotoExhibition-ModelManager/1.0");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new RuntimeException("下载返回异常状态码: " + status);
        }

        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        AtomicLong size = new AtomicLong();
        try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
                digest.update(buffer, 0, len);
                size.addAndGet(len);
            }
        } finally {
            connection.disconnect();
        }
        return new DownloadSnapshot(size.get(), toHex(digest.digest()));
    }

    private ValidationSnapshot validateOnnxFile(Path path) throws Exception {
        if (!Files.exists(path) || Files.size(path) <= 0) {
            throw new RuntimeException("下载后的模型文件为空");
        }
        OrtEnvironment environment = OrtEnvironment.getEnvironment();
        try (OrtSession session = environment.createSession(path.toString(), new OrtSession.SessionOptions())) {
            return new ValidationSnapshot(
                new ArrayList<>(session.getInputNames()),
                new ArrayList<>(session.getOutputNames()),
                Files.size(path),
                LocalDateTime.now()
            );
        }
    }

    private Path resolveLocalPhoto(Photo photo) {
        if (photo == null || photo.getOriginalPath() == null || photo.getOriginalPath().isBlank()) {
            return null;
        }
        return userPathService.tryResolveLocalStoredPhotoPath(photo.getOriginalPath()).orElse(null);
    }

    private ModelRuntime getRuntime(String key) {
        switch (normalizeKey(key)) {
            case "face_detection":
                return new ModelRuntime("face_detection", "人脸检测模型", faceDetectionService.getModelPath(), faceDetectionService::isModelLoaded, faceDetectionService::reloadModel);
            case "face_recognition":
                return new ModelRuntime("face_recognition", "人脸特征模型", faceEmbeddingService.getModelPath(), faceEmbeddingService::isModelLoaded, faceEmbeddingService::reloadModel);
            case "image_classification":
                return new ModelRuntime("image_classification", "图像分类模型", imageClassificationService.getModelPath(), imageClassificationService::isModelLoaded, imageClassificationService::reloadModel);
            case "saliency_detection":
                return new ModelRuntime("saliency_detection", "显著性检测模型", saliencyDetectionService.getModelPath(), saliencyDetectionService::isModelLoaded, saliencyDetectionService::reloadModel);
            case "background_removal":
                return new ModelRuntime("background_removal", "背景移除模型", backgroundRemovalService.getModelPath(), backgroundRemovalService::isModelAvailable, backgroundRemovalService::reloadModel);
            default:
                throw new RuntimeException("未知模型类型: " + key);
        }
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    private Path resolveModelPath(String configuredPath) {
        Path path = Path.of(configuredPath == null ? "" : configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path candidate = cwd.resolve(path).normalize();
        if (Files.exists(candidate) || candidate.getParent() != null && Files.exists(candidate.getParent())) {
            return candidate;
        }
        return cwd.resolve("backend").resolve(path).normalize();
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte item : bytes) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }

    @FunctionalInterface
    private interface ModelLoader {
        boolean loaded();
    }

    @FunctionalInterface
    private interface ModelReloader {
        boolean reload();
    }

    private static class ModelRuntime {
        final String key;
        final String name;
        final String modelPath;
        final ModelLoader loader;
        final ModelReloader reloader;

        private ModelRuntime(String key, String name, String modelPath, ModelLoader loader, ModelReloader reloader) {
            this.key = key;
            this.name = name;
            this.modelPath = modelPath;
            this.loader = loader;
            this.reloader = reloader;
        }
    }

    private static class DownloadSnapshot {
        final long sizeBytes;
        final String sha256;

        private DownloadSnapshot(long sizeBytes, String sha256) {
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
        }
    }

    private static class ValidationSnapshot {
        final List<String> inputs;
        final List<String> outputs;
        final long sizeBytes;
        final LocalDateTime validatedAt;

        private ValidationSnapshot(List<String> inputs, List<String> outputs, long sizeBytes, LocalDateTime validatedAt) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.sizeBytes = sizeBytes;
            this.validatedAt = validatedAt;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("inputs", inputs);
            data.put("outputs", outputs);
            data.put("sizeBytes", sizeBytes);
            data.put("validatedAt", validatedAt);
            return data;
        }
    }

}
