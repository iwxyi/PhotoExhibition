package com.photoexhibition.service;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelManagementService {

    private static final Set<String> FACE_DETECTION_KEYS = Set.of("face_detection");
    private static final Set<String> FACE_EMBEDDING_KEYS = Set.of("face_recognition");
    private static final Set<String> AI_ANALYSIS_KEYS = Set.of("image_classification", "saliency_detection", "scene_recognition", "emotion_analysis");
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

    private final ConcurrentHashMap<String, TaskSnapshot> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> latestTaskByModel = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ValidationSnapshot> latestValidationByModel = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor rebuildExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new ThreadFactory() {
        private final AtomicInteger threadCounter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "model-rebuild-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    });

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
        models.add(buildModel("scene_recognition", "场景识别模型", "Scene Recognition", sceneRecognitionService.getModelPath(), sceneRecognitionService.isEnabled(), sceneRecognitionService.isModelLoaded(),
            List.of("在线下载", "验证后启用", "重建场景分析与 AI 评分")));
        models.add(buildModel("emotion_analysis", "情绪分析模型", "Emotion Analysis", emotionAnalysisService.getModelPath(), emotionAnalysisService.isEnabled(), emotionAnalysisService.isModelLoaded(),
            List.of("在线下载", "验证后启用", "重建情绪分析与 AI 评分")));
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

    public Map<String, Object> triggerRebuild(String key, boolean includeMissingItems, boolean forceRebuild) {
        if (!includeMissingItems && !forceRebuild) {
            throw new RuntimeException("请至少选择一种重建策略：尝试无数据项 或 彻底重建");
        }
        ModelRuntime runtime = getRuntime(key);
        String taskId = key + "-" + UUID.randomUUID();
        TaskSnapshot task = new TaskSnapshot(taskId, key, runtime.name, includeMissingItems, forceRebuild);
        tasks.put(taskId, task);
        latestTaskByModel.put(key, taskId);

        CompletableFuture.runAsync(() -> runTask(task), rebuildExecutor);

        return Map.of(
            "taskId", taskId,
            "modelKey", key,
            "message", "重建任务已启动",
            "task", task.toMap()
        );
    }

    public Map<String, Object> getTask(String taskId) {
        TaskSnapshot task = tasks.get(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        return task.toMap();
    }

    public Map<String, Object> getTaskOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> recentTasks = tasks.values().stream()
            .sorted((left, right) -> right.createdAt.compareTo(left.createdAt))
            .limit(8)
            .map(TaskSnapshot::toMap)
            .collect(java.util.stream.Collectors.toList());
        long runningCount = tasks.values().stream()
            .filter(task -> task != null && !task.complete)
            .count();

        result.put("threadType", "MODEL_REBUILD");
        result.put("label", "模型重建线程");
        result.put("runningTaskCount", runningCount);
        result.put("activeThreads", rebuildExecutor.getActiveCount());
        result.put("queuedTasks", rebuildExecutor.getQueue().size());
        result.put("completedTaskCount", rebuildExecutor.getCompletedTaskCount());
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
        String latestTaskId = latestTaskByModel.get(key);
        model.put("latestTask", latestTaskId == null ? null : Optional.ofNullable(tasks.get(latestTaskId)).map(TaskSnapshot::toMap).orElse(null));
        model.put("taskHistory", listTaskHistory(key, 5));
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
        return tasks.values().stream()
            .filter(task -> modelKey.equals(task.modelKey))
            .sorted((left, right) -> right.createdAt.compareTo(left.createdAt))
            .limit(limit)
            .map(TaskSnapshot::toMap)
            .collect(java.util.stream.Collectors.toList());
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

    private void runTask(TaskSnapshot task) {
        task.status = "RUNNING";
        task.startedAt = LocalDateTime.now();
        try {
            if (FACE_DETECTION_KEYS.contains(task.modelKey)) {
                rebuildFaceDetection(task);
            } else if (FACE_EMBEDDING_KEYS.contains(task.modelKey)) {
                rebuildFaceEmbeddings(task);
            } else if (AI_ANALYSIS_KEYS.contains(task.modelKey)) {
                rebuildAiAnalysis(task);
            } else if (BACKGROUND_REMOVAL_KEYS.contains(task.modelKey)) {
                rebuildBackgroundRemoval(task);
            } else {
                throw new RuntimeException("暂不支持该模型的重建任务");
            }
            task.status = "SUCCESS";
            task.message = "重建完成";
        } catch (Exception e) {
            task.status = "FAILED";
            task.message = "重建失败: " + e.getMessage();
            task.logs.add(task.message);
            log.error("模型重建失败: {}", task.taskId, e);
        } finally {
            task.finishedAt = LocalDateTime.now();
            task.complete = true;
        }
    }

    private void rebuildFaceDetection(TaskSnapshot task) {
        pagePhotos(photo -> {
            boolean hasExistingFaces = !faceRepository.findByPhotoId(photo.getId()).isEmpty();
            if (!task.includeMissingItems && !hasExistingFaces) {
                task.skipped++;
                return;
            }
            if (!task.forceRebuild && hasExistingFaces) {
                task.skipped++;
                return;
            }
            Path resolved = resolveLocalPhoto(photo);
            if (resolved == null || !Files.exists(resolved)) {
                task.failed++;
                task.logs.add("跳过不存在文件的照片 #" + photo.getId());
                return;
            }
            photoScanService.rescanFacesForPhoto(photo.getId());
            task.processed++;
        }, task);
    }

    private void rebuildFaceEmbeddings(TaskSnapshot task) {
        pagePhotos(photo -> {
            boolean hasExistingFaces = !faceRepository.findByPhotoId(photo.getId()).isEmpty();
            if (!hasExistingFaces) {
                if (!task.includeMissingItems) {
                    task.skipped++;
                    return;
                }
                photoScanService.rescanFacesForPhoto(photo.getId());
                hasExistingFaces = !faceRepository.findByPhotoId(photo.getId()).isEmpty();
            }
            if (!hasExistingFaces) {
                task.skipped++;
                return;
            }
            photoScanService.rebuildFaceEmbeddingsForPhoto(photo.getId());
            task.processed++;
        }, task);
    }

    private void rebuildAiAnalysis(TaskSnapshot task) {
        pagePhotos(photo -> {
            try {
                Path imagePath = resolveLocalPhoto(photo);
                if (imagePath == null || !Files.exists(imagePath)) {
                    task.skipped++;
                    return;
                }
                int faceCount = faceRepository.findByPhotoId(photo.getId()).size();
                smartTagService.applySmartTags(imagePath.toFile(), photo, faceCount, true, Set.of());
                photoAIScoringService.rescorePhoto(photo);
                task.processed++;
            } catch (Exception e) {
                task.failed++;
                task.logs.add("照片 #" + photo.getId() + " 失败: " + e.getMessage());
            }
        }, task);
    }

    private void rebuildBackgroundRemoval(TaskSnapshot task) {
        if (task.forceRebuild) {
            Map<String, Object> clearResult = photoScanService.clearBackgroundCache();
            task.logs.add(String.valueOf(clearResult.getOrDefault("message", "已清理旧抠图缓存")));
        }
        pagePhotos(photo -> {
            boolean hasRemoved = photo.getBackgroundRemovedPath() != null && !photo.getBackgroundRemovedPath().isBlank();
            if (!task.includeMissingItems && !hasRemoved) {
                task.skipped++;
                return;
            }
            if (!task.forceRebuild && hasRemoved) {
                task.skipped++;
                return;
            }
            try {
                Path imagePath = resolveLocalPhoto(photo);
                if (imagePath == null || !Files.exists(imagePath)) {
                    task.skipped++;
                    return;
                }
                photoScanService.removeBackgroundForPhoto(photo.getId(), task.forceRebuild);
                task.processed++;
            } catch (Exception e) {
                task.failed++;
                task.logs.add("照片 #" + photo.getId() + " 抠图失败: " + e.getMessage());
            }
        }, task);
    }

    private Path resolveLocalPhoto(Photo photo) {
        if (photo == null || photo.getOriginalPath() == null || photo.getOriginalPath().isBlank()) {
            return null;
        }
        return userPathService.tryResolveLocalStoredPhotoPath(photo.getOriginalPath()).orElse(null);
    }

    private void pagePhotos(PhotoConsumer consumer, TaskSnapshot task) {
        int page = 0;
        Page<Photo> result;
        do {
            result = photoRepository.findAll(PageRequest.of(page, 100));
            task.total = (int) result.getTotalElements();
            for (Photo photo : result.getContent()) {
                consumer.accept(photo);
            }
            page++;
        } while (result.hasNext());
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
            case "scene_recognition":
                return new ModelRuntime("scene_recognition", "场景识别模型", sceneRecognitionService.getModelPath(), sceneRecognitionService::isModelLoaded, sceneRecognitionService::reloadModel);
            case "emotion_analysis":
                return new ModelRuntime("emotion_analysis", "情绪分析模型", emotionAnalysisService.getModelPath(), emotionAnalysisService::isModelLoaded, emotionAnalysisService::reloadModel);
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

    @PreDestroy
    public void shutdown() {
        rebuildExecutor.shutdownNow();
    }

    @FunctionalInterface
    private interface PhotoConsumer {
        void accept(Photo photo);
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

    private static class TaskSnapshot {
        final String taskId;
        final String modelKey;
        final String modelName;
        final boolean includeMissingItems;
        final boolean forceRebuild;
        volatile String status = "PENDING";
        volatile String message = "等待执行";
        volatile boolean complete = false;
        volatile int total = 0;
        volatile int processed = 0;
        volatile int skipped = 0;
        volatile int failed = 0;
        volatile LocalDateTime createdAt = LocalDateTime.now();
        volatile LocalDateTime startedAt;
        volatile LocalDateTime finishedAt;
        final List<String> logs = java.util.Collections.synchronizedList(new ArrayList<>());

        private TaskSnapshot(String taskId, String modelKey, String modelName, boolean includeMissingItems, boolean forceRebuild) {
            this.taskId = taskId;
            this.modelKey = modelKey;
            this.modelName = modelName;
            this.includeMissingItems = includeMissingItems;
            this.forceRebuild = forceRebuild;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", taskId);
            data.put("modelKey", modelKey);
            data.put("modelName", modelName);
            data.put("includeMissingItems", includeMissingItems);
            data.put("forceRebuild", forceRebuild);
            data.put("status", status);
            data.put("message", message);
            data.put("complete", complete);
            data.put("total", total);
            data.put("processed", processed);
            data.put("skipped", skipped);
            data.put("failed", failed);
            data.put("createdAt", createdAt);
            data.put("startedAt", startedAt);
            data.put("finishedAt", finishedAt);
            data.put("logs", new ArrayList<>(logs));
            return data;
        }
    }
}
