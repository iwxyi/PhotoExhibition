package com.photoexhibition.service;

import ai.onnxruntime.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.Face;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.PhotoAIScoring;
import com.photoexhibition.repository.PhotoAIScoringRepository;
import com.photoexhibition.repository.PhotoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI图片评分服务
 * 基于多个AI模型对图片进行综合质量评分和分析
 */
@Slf4j
@Service
public class PhotoAIScoringService implements AutoCloseable {

    @Value("${ai.scoring.enabled:false}")
    private boolean enabled;

    @Value("${ai.scoring.version:1.0}")
    private String scoringVersion;

    @Value("${ai.scoring.technical-weight:0.40}")
    private double technicalWeight;

    @Value("${ai.scoring.composition-weight:0.35}")
    private double compositionWeight;

    @Value("${ai.scoring.appeal-weight:0.25}")
    private double appealWeight;

    @Autowired
    private PhotoAIScoringRepository scoringRepository;

    @Autowired
    private ImageClassificationService classificationService;

    @Autowired
    private SaliencyDetectionService saliencyService;

    @Autowired
    private ColorAnalysisService colorAnalysisService;

    @Autowired
    private FaceService faceService;

    @Autowired
    private SceneRecognitionService sceneRecognitionService;

    @Autowired
    private EmotionAnalysisService emotionAnalysisService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PhotoRepository photoRepository;

    private OrtEnvironment env;
    private boolean onnxAvailable = false;

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("AI评分服务已启用，将在首次使用时初始化ONNX Runtime");
            // 延迟初始化，避免在应用启动时立即加载ONNX库
        } else {
            log.info("AI评分服务已禁用，将使用基础评分模式");
        }
    }

    /**
     * 在应用启动时初始化ONNX环境，不抛出异常
     */
    private void initializeOnnxEnvironment() {
        try {
            // 尝试初始化ONNX环境一次
            if (this.env == null) {
                log.debug("尝试初始化ONNX Runtime环境...");

                // 尝试手动提取并加载ONNX库（针对macOS ARM64）
                tryManualLibraryLoad();

                this.env = OrtEnvironment.getEnvironment();
                this.onnxAvailable = true;
                log.info("ONNX Runtime环境初始化成功，AI增强评分功能已启用");
            }
        } catch (NoClassDefFoundError e) {
            log.error("ONNX Java类库缺失 - 无法加载ONNX Runtime类库");
            log.error("错误详情: {}", e.getMessage());
            log.error("解决方案: 请检查Maven依赖是否正确下载了onnxruntime JAR包");
            this.env = null;
            this.onnxAvailable = false;
        } catch (UnsatisfiedLinkError e) {
            String osArch = System.getProperty("os.arch", "unknown");
            String osName = System.getProperty("os.name", "unknown");
            String javaLibPath = System.getProperty("java.library.path", "unknown");

            log.error("ONNX运行时库缺失 - 无法加载平台特定的二进制库");
            log.error("错误详情: {}", e.getMessage());
            log.error("系统信息 - OS: {}, 架构: {}", osName, osArch);
            log.error("Java库路径: {}", javaLibPath);
            log.error("已尝试手动加载库文件，但仍失败");
            log.error("当前将使用基础评分模式，AI增强功能不可用");
            this.env = null;
            this.onnxAvailable = false;
        } catch (Exception e) {
            log.error("ONNX Runtime初始化异常 - 未知错误");
            log.error("错误类型: {}", e.getClass().getSimpleName());
            log.error("错误详情: {}", e.getMessage());
            log.error("完整堆栈跟踪:", e);
            log.error("当前将使用基础评分模式，AI增强功能不可用");
            this.env = null;
            this.onnxAvailable = false;
        }
    }

    /**
     * 手动尝试加载ONNX库（针对macOS ARM64的特殊处理）
     */
    private void tryManualLibraryLoad() {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            String osArch = System.getProperty("os.arch", "");

            if (osName.contains("mac") && "aarch64".equals(osArch)) {
                log.debug("检测到macOS ARM64，尝试手动加载ONNX库...");

                // 尝试加载系统级ONNX库（如果已编译安装）
                String customLibPath = System.getProperty("onnx.custom.lib.path");
                if (customLibPath != null && !customLibPath.isEmpty()) {
                    log.debug("使用自定义ONNX库路径: {}", customLibPath);
                    System.load(customLibPath + "/libonnxruntime.dylib");
                    log.debug("成功加载自定义ONNX库");
                    return;
                }

                // 尝试从JAR文件中提取库
                String libraryName = "onnxruntime4j_jni";
                String resourcePath = "ai/onnxruntime/native/osx-aarch64/lib" + libraryName + ".dylib";

                // 使用ONNX Runtime的内置方法加载
                System.loadLibrary(libraryName);
                log.debug("成功加载ONNX库: {}", libraryName);
            }
        } catch (Exception e) {
            log.debug("手动库加载失败，继续使用自动加载: {}", e.getMessage());
        }
    }


    /**
     * 确保ONNX环境可用，如果不可用则记录警告但不抛出异常
     */
    private boolean ensureOnnxEnvironment() {
        if (!enabled) {
            throw new OnnxConfigurationException("AI评分功能已禁用，请在配置文件中启用ai.scoring.enabled=true");
        }
        // 如果已经初始化过，直接返回状态
        if (this.env != null || onnxAvailable) {
            return onnxAvailable;
        }

        // 延迟初始化ONNX环境
        if (enabled) {
            log.debug("首次使用时初始化ONNX Runtime...");
            initializeOnnxEnvironment();
        }

        return onnxAvailable;
    }

    @PreDestroy
    @Override
    public void close() {
        if (env != null) {
            try {
                env.close();
            } catch (Exception e) {
                log.warn("Error closing ONNX environment", e);
            }
        }
    }

    /**
     * 对照片进行AI评分
     */
    // 注意：移除@Transactional注解，让它在调用方的事务中运行，避免LazyInitializationException
    public PhotoAIScoring scorePhoto(Photo photo) throws OnnxConfigurationException {
        if (!enabled) {
            log.debug("AI scoring is disabled, skipping AI scoring for photo {}", photo.getId());
            return null; // Return null if disabled, no record created
        }

        long startTime = System.currentTimeMillis();

        try {
            // 检查是否已有评分记录
            Optional<PhotoAIScoring> existingScoring = scoringRepository.findByPhotoId(photo.getId());
            if (existingScoring.isPresent() && !needsRescoring(existingScoring.get())) {
                log.debug("Photo {} already has valid AI scoring", photo.getId());
                return existingScoring.get();
            }

            // 检查ONNX环境可用性（支持降级评分）
            boolean onnxReady = ensureOnnxEnvironment();
            log.debug("Processing photo {} with {} scoring", photo.getId(), onnxReady ? "enhanced" : "basic");

            // 创建评分记录
            PhotoAIScoring scoring = existingScoring.orElse(new PhotoAIScoring());
            scoring.setPhotoId(photo.getId());
            scoring.setScoreVersion(scoringVersion);
            scoring.setScoringStatus(PhotoAIScoring.AIScoringStatus.IN_PROGRESS);
            scoring.setScoredAt(LocalDateTime.now());

            // 执行AI分析
            ScoringResult result = performAIScoring(photo);

            // 更新评分结果
            updateScoringWithResult(scoring, result);

            // 计算最终得分
            scoring.calculateOverallScore();
            scoring.setScoringStatus(PhotoAIScoring.AIScoringStatus.COMPLETED);
            scoring.setProcessingTimeMs((int) (System.currentTimeMillis() - startTime));

            // 保存最终结果
            PhotoAIScoring finalScoring = scoringRepository.save(scoring);

            log.info("AI评分完成 - 照片ID: {}, 总体评分: {} (技术: {}, 构图: {}, 吸引力: {}, 耗时: {}ms)",
                    photo.getId(),
                    String.format("%.2f", finalScoring.getOverallScore()),
                    String.format("%.2f", finalScoring.getTechnicalScore()),
                    String.format("%.2f", finalScoring.getCompositionScore()),
                    String.format("%.2f", finalScoring.getAppealScore()),
                    finalScoring.getProcessingTimeMs());

            return finalScoring;

        } catch (Exception e) {
            log.error("Failed to score photo {}", photo.getId(), e);

            // 清理可能存在的失败记录
            try {
                scoringRepository.deleteByPhotoId(photo.getId());
            } catch (Exception cleanupException) {
                log.warn("Failed to cleanup scoring record for photo {}: {}", photo.getId(), cleanupException.getMessage());
            }

            return null;
        }
    }

    /**
     * 执行AI评分的核心逻辑
     */
    private ScoringResult performAIScoring(Photo inputPhoto) throws IOException {
        File imageFile = new File(inputPhoto.getOriginalPath());
        if (!imageFile.exists()) {
            throw new IOException("Image file not found: " + inputPhoto.getOriginalPath());
        }

        // 重新查询完整的Photo对象，避免懒加载问题
        // 这里我们需要一个能加载faces和tags的查询方法
        Photo photo = loadCompletePhoto(inputPhoto.getId());
        if (photo == null) {
            throw new IOException("Photo not found in database: " + inputPhoto.getId());
        }

        ScoringResult result = new ScoringResult();
        result.modelsUsed = new HashMap<>();

        // 1. 技术质量评分
        result.technicalScore = calculateTechnicalScore(photo, imageFile);
        result.technicalAnalysis = generateTechnicalAnalysis(photo, imageFile);
        result.modelsUsed.put("color_analysis", true); // Color analysis is part of technical score
        log.debug("技术质量评分: {} (分辨率: {}x{}, ISO: {}, 光圈: {}, 快门: {})",
                String.format("%.2f", result.technicalScore), photo.getWidth(), photo.getHeight(),
                photo.getIso(), photo.getAperture(), photo.getShutterSpeed());

        // 2. 构图美学评分
        result.compositionScore = calculateCompositionScore(photo, imageFile);
        result.compositionAnalysis = generateCompositionAnalysis(photo, imageFile);
        result.modelsUsed.put("saliency", onnxAvailable); // 只有ONNX可用时才算使用了saliency模型
        log.debug("构图美学评分: {} (焦点位置: {}%, 宽高比: {})",
                String.format("%.2f", result.compositionScore),
                photo.getFocusX() != null ? String.format("%.1f", photo.getFocusX()) : "N/A",
                photo.getWidth() != null && photo.getHeight() != null ?
                String.format("%.2f", (double) photo.getWidth() / photo.getHeight()) : "N/A");

        // 3. 主题吸引力评分
        result.appealScore = calculateAppealScore(photo, imageFile);
        result.appealAnalysis = generateAppealAnalysis(photo, imageFile);
        result.modelsUsed.put("classification", onnxAvailable); // 只有ONNX可用时才算使用了classification模型
        result.modelsUsed.put("face_detection", true); // 人脸检测可能不依赖ONNX
        int faceCount = 0;
        try {
            var faces = faceService.getFacesByPhoto(photo.getId());
            faceCount = faces != null ? faces.size() : 0;
        } catch (Exception e) {
            // 忽略人脸计数错误
        }
        log.debug("主题吸引力评分: {} (标签数量: {}, 人脸数量: {})",
                String.format("%.2f", result.appealScore),
                photo.getTags() != null ? photo.getTags().size() : 0,
                faceCount);

        // 4. AI增强分析：场景识别和情感分析
        try {
            // 场景识别
            var sceneResult = sceneRecognitionService.recognizeScene(imageFile);
            result.sceneAnalysis = sceneResult;

            if (sceneResult != null && !sceneResult.scenes.isEmpty()) {
                var primaryScene = sceneResult.scenes.get(0);
                log.debug("场景识别结果: {} (置信度: {}%, 候选: {})",
                        primaryScene.scene,
                        String.format("%.1f", sceneResult.confidence * 100),
                        sceneResult.scenes.size());
            } else {
                log.debug("场景识别结果: 未识别到场景");
            }

            // 情感分析
            var emotionResult = emotionAnalysisService.analyzeEmotion(imageFile, photo.getId());
            result.emotionAnalysis = emotionResult;

            if (emotionResult != null && emotionResult.primaryEmotion != null) {
                log.debug("情感分析结果: {} (置信度: {}%, 候选: {})",
                        emotionResult.primaryEmotion,
                        String.format("%.1f", emotionResult.emotions.get(0).confidence * 100),
                        emotionResult.emotions.size());
            } else {
                log.debug("情感分析结果: 未识别到情感");
            }

            // 更新Photo实体的AI分析字段
            updatePhotoWithAIAnalysis(photo, sceneResult, emotionResult);

            result.modelsUsed.put("scene_recognition", true);
            result.modelsUsed.put("emotion_analysis", true);
        } catch (Exception e) {
            log.warn("AI增强分析失败: {}", e.getMessage());
            result.modelsUsed.put("scene_recognition", false);
            result.modelsUsed.put("emotion_analysis", false);
        }

        // 5. 生成优点和不足
        result.strengths = analyzeStrengths(photo, result);
        result.weaknesses = analyzeWeaknesses(photo, result);

        // 6. 生成改进建议
        result.improvementSuggestions = generateImprovementSuggestions(result);

        // 记录分析结果汇总
        log.debug("分析结果汇总 - 优点: {}项, 不足: {}项, 建议: {}项",
                result.strengths.size(),
                result.weaknesses.size(),
                result.improvementSuggestions.size());

        return result;
    }


    /**
     * 从数据库加载完整的Photo对象（包括faces和tags），避免懒加载问题
     */
    private Photo loadCompletePhoto(Long photoId) {
        // 通过 join fetch 一次性加载关联集合，避免在非事务/异步线程里触发懒加载
        return photoRepository.findByIdWithFacesAndTags(photoId).orElse(null);
    }

    /**
     * 重新评分照片
     */
    // 注意：移除@Transactional注解，让它在调用方的事务中运行
    public PhotoAIScoring rescorePhoto(Photo photo) {
        // 删除现有的评分记录
        scoringRepository.deleteByPhotoId(photo.getId());
        // 重新评分
        return scorePhoto(photo);
    }

    /**
     * 获取照片的AI评分
     */
    public PhotoAIScoring getPhotoAIScoring(Long photoId) {
        return scoringRepository.findByPhotoId(photoId).orElse(null);
    }


    /**
     * 计算技术质量评分 (0-100)
     */
    private double calculateTechnicalScore(Photo photo, File imageFile) throws IOException {
        double score = 30.0; // 基础分

        BufferedImage img = ImageIO.read(imageFile);
        if (img == null) return score;

        // 分辨率评分
        int pixels = img.getWidth() * img.getHeight();
        if (pixels > 20000000) score += 25;      // 2000万像素以上
        else if (pixels > 10000000) score += 20; // 1000万像素以上
        else if (pixels > 5000000) score += 15;  // 500万像素以上
        else if (pixels > 2000000) score += 10;  // 200万像素以上

        // EXIF信息完整性
        if (photo.getCameraModel() != null) score += 5;
        if (photo.getAperture() != null) score += 5;
        if (photo.getShutterSpeed() != null) score += 5;
        if (photo.getIso() != null) {
            Integer iso = photo.getIso();
            if (iso < 400) score += 5;      // ISO低，画质好
            else if (iso < 1600) score += 3; // ISO中等
            else if (iso < 6400) score += 1; // ISO较高
            // ISO过高会降低画质
        }

        // 色彩分析
        if (photo.getColorPalette() != null) score += 5;

        return Math.min(80.0, Math.max(20.0, score));
    }

    /**
     * 更新Photo实体中的AI分析结果
     */
    private void updatePhotoWithAIAnalysis(Photo photo,
            SceneRecognitionService.SceneRecognitionResult sceneResult,
            EmotionAnalysisService.EmotionAnalysisResult emotionResult) {
        try {
            // 更新场景分析
            if (sceneResult != null && !sceneResult.scenes.isEmpty()) {
                photo.setSceneAnalysis(objectMapper.writeValueAsString(sceneResult.scenes));
                photo.setPrimaryScene(sceneResult.scenes.get(0).scene);
                photo.setSceneConfidence(sceneResult.confidence);
            }

            // 更新情感分析
            if (emotionResult != null && !emotionResult.emotions.isEmpty()) {
                photo.setEmotionAnalysis(objectMapper.writeValueAsString(emotionResult.emotions));
                photo.setPrimaryEmotion(emotionResult.primaryEmotion);
                photo.setEmotionConfidence(emotionResult.emotions.get(0).confidence);
            }

            // 保存更新
            photoRepository.save(photo);
        } catch (Exception e) {
            log.warn("更新Photo AI分析字段失败: {}", e.getMessage());
        }
    }

    /**
     * 计算构图美学评分 (0-100)
     * 支持降级评分：ONNX可用时使用AI增强，否则使用基础规则评分
     */
    private double calculateCompositionScore(Photo photo, File imageFile) throws IOException {
        double score = 35.0; // 基础分

        // 尝试使用AI显著性检测（如果ONNX可用）
        if (onnxAvailable) {
            try {
                double[] saliencyMap = saliencyService.detectSaliency(imageFile,
                        photo.getWidth() != null ? photo.getWidth() : 1024,
                        photo.getHeight() != null ? photo.getHeight() : 768);
                if (saliencyMap != null && saliencyMap.length > 0) {
                    // 基于显著性分布计算构图平衡性
                    score += 15.0; // AI构图加分
                }
            } catch (Exception e) {
                log.warn("Saliency detection failed for photo {}, falling back to basic scoring: {}", photo.getId(), e.getMessage());
                // 继续使用基础评分
            }
        }

        // 黄金比例检查
        if (photo.getWidth() != null && photo.getHeight() != null) {
            double ratio = (double) photo.getWidth() / photo.getHeight();
            double goldenRatio = 1.618;
            double ratioDiff = Math.abs(ratio - goldenRatio);
            if (ratioDiff < 0.2) score += 10; // 接近黄金比例
            else if (ratioDiff < 0.5) score += 5; // 较为协调
        }

        // 焦点位置分析
        if (photo.getFocusX() != null && photo.getFocusY() != null) {
            // 检查焦点是否在黄金分割点附近
            double focusX = photo.getFocusX();
            double focusY = photo.getFocusY();

            // 黄金分割点位置
            double goldenPointsX = 0.382; // 或 0.618
            double goldenPointsY = 0.382;

            boolean nearGoldenX = (Math.abs(focusX / 100.0 - goldenPointsX) < 0.1 || Math.abs(focusX / 100.0 - (1 - goldenPointsX)) < 0.1);
            boolean nearGoldenY = (Math.abs(focusY / 100.0 - goldenPointsY) < 0.1 || Math.abs(focusY / 100.0 - (1 - goldenPointsY)) < 0.1);

            if (nearGoldenX && nearGoldenY) score += 15; // 焦点在黄金分割点附近
            else if (nearGoldenX || nearGoldenY) score += 5; // 焦点在黄金分割线附近
        }

        return Math.min(80.0, Math.max(20.0, score));
    }

    /**
     * 计算主题吸引力评分 (0-100)
     * 支持降级评分：ONNX可用时使用AI增强，否则使用基础规则评分
     */
    private double calculateAppealScore(Photo photo, File imageFile) throws IOException {
        double score = 40.0; // 基础分

        // 尝试使用AI图像分类（如果ONNX可用）
        if (onnxAvailable) {
            List<ImageClassificationService.ClassificationResult> classifications = Collections.emptyList();
            try {
                classifications = classificationService.classify(imageFile);
                score += calculateThemeAppeal(classifications);
            } catch (Exception e) {
                log.warn("Image classification failed for photo {}, falling back to basic scoring: {}", photo.getId(), e.getMessage());
                // 继续使用基础评分
            }
        }

        // 人脸检测
        try {
            List<com.photoexhibition.entity.Face> faces = faceService.getFacesByPhoto(photo.getId());
            if (faces != null && !faces.isEmpty()) {
                score += Math.min(20, faces.size() * 5); // 人脸数量越多，吸引力越高
            }
        } catch (Exception e) {
            log.warn("Face detection/retrieval failed for photo {}: {}", photo.getId(), e.getMessage());
            // 不抛出异常，人脸检测失败不影响整体吸引力评分
        }

        // 标签丰富度
        if (photo.getTags() != null && !photo.getTags().isEmpty()) {
            score += Math.min(15, photo.getTags().size() * 3);
        }

        return Math.min(80.0, Math.max(20.0, score));
    }

    private double calculateThemeAppeal(List<ImageClassificationService.ClassificationResult> classifications) {
        double appeal = 0.0;
        for (ImageClassificationService.ClassificationResult res : classifications) {
            String label = res.getLabel().toLowerCase();
            float confidence = res.getConfidence();

            if (label.contains("人") || label.contains("肖像") || label.contains("婚礼")) {
                appeal += confidence * 20;
            } else if (label.contains("风景") || label.contains("自然") || label.contains("城市")) {
                appeal += confidence * 10;
            } else if (label.contains("动物") || label.contains("美食")) {
                appeal += confidence * 5;
            }
        }
        return Math.min(25.0, appeal);
    }


    /**
     * 生成技术质量详细分析
     */
    private Map<String, Object> generateTechnicalAnalysis(Photo photo, File imageFile) {
        Map<String, Object> analysis = new HashMap<>();

        if (photo.getWidth() != null && photo.getHeight() != null) {
            int pixels = photo.getWidth() * photo.getHeight();
            analysis.put("resolution", pixels);
            analysis.put("resolution_quality", pixels > 10000000 ? "excellent" :
                                                pixels > 5000000 ? "good" : "average");
        }

        if (photo.getIso() != null) {
            analysis.put("iso", photo.getIso());
            analysis.put("noise_level", photo.getIso() < 400 ? "low" :
                                       photo.getIso() < 1600 ? "medium" : "high");
        }

        return analysis;
    }

    /**
     * 生成构图美学详细分析
     */
    private Map<String, Object> generateCompositionAnalysis(Photo photo, File imageFile) {
        Map<String, Object> analysis = new HashMap<>();

        if (photo.getWidth() != null && photo.getHeight() != null) {
            double ratio = (double) photo.getWidth() / photo.getHeight();
            analysis.put("aspect_ratio", ratio);
            analysis.put("golden_ratio_proximity", Math.abs(ratio - 1.618));
        }

        if (photo.getFocusX() != null && photo.getFocusY() != null) {
            analysis.put("focus_position", Map.of("x", photo.getFocusX(), "y", photo.getFocusY()));
            analysis.put("rule_of_thirds_compliance", isRuleOfThirdsCompliant(photo.getFocusX(), photo.getFocusY()));
        }

        return analysis;
    }

    /**
     * 生成主题吸引力详细分析
     */
    private Map<String, Object> generateAppealAnalysis(Photo photo, File imageFile) {
        Map<String, Object> analysis = new HashMap<>();

        // 主题类型分析 - tags是EAGER加载的，应该不会出现懒加载问题
        if (photo.getTags() != null && !photo.getTags().isEmpty()) {
            List<String> themes = photo.getTags().stream()
                    .map(tag -> tag.getName())
                    .collect(Collectors.toList());
            analysis.put("themes", themes);
        }

        // 人脸数量 - 直接查询数据库，避免懒加载问题
        try {
            List<Face> faces = faceService.getFacesByPhoto(photo.getId());
            if (faces != null && !faces.isEmpty()) {
                analysis.put("face_count", faces.size());
            }
        } catch (Exception e) {
            // 忽略人脸查询错误
            log.debug("Failed to get face count for photo {}: {}", photo.getId(), e.getMessage());
        }

        return analysis;
    }

    /**
     * 分析图片优点
     */
    private List<String> analyzeStrengths(Photo photo, ScoringResult result) {
        List<String> strengths = new ArrayList<>();

        // 技术质量优点
        if (result.technicalScore >= 80) {
            strengths.add("技术质量优秀");
        } else if (result.technicalScore >= 70) {
            strengths.add("技术质量良好");
        }

        // 构图优点
        if (result.compositionScore >= 80) {
            strengths.add("构图精美");
        } else if (result.compositionScore >= 70) {
            strengths.add("构图协调");
        }

        // 主题优点
        if (result.appealScore >= 80) {
            strengths.add("主题吸引人");
        } else if (result.appealScore >= 70) {
            strengths.add("内容有趣");
        }

        // 特殊优点
        if (photo.getWidth() != null && photo.getHeight() != null) {
            int pixels = photo.getWidth() * photo.getHeight();
            if (pixels > 20000000) {
                strengths.add("超高分辨率");
            }
        }

        // 检查是否包含人物元素 - 直接查询数据库，避免懒加载问题
        try {
            List<Face> faces = faceService.getFacesByPhoto(photo.getId());
            if (faces != null && !faces.isEmpty()) {
                strengths.add("包含人物元素");
            }
        } catch (Exception e) {
            // 忽略人脸查询错误
            log.debug("Failed to check faces for photo {}: {}", photo.getId(), e.getMessage());
        }

        return strengths;
    }

    /**
     * 分析图片不足
     */
    private List<String> analyzeWeaknesses(Photo photo, ScoringResult result) {
        List<String> weaknesses = new ArrayList<>();

        // 技术质量不足
        if (result.technicalScore < 60) {
            weaknesses.add("技术质量有待提升");
        }
        if (photo.getIso() != null && photo.getIso() > 1600) {
            weaknesses.add("噪点较多");
        }

        // 构图不足
        if (result.compositionScore < 60) {
            weaknesses.add("构图可优化");
        }

        // 主题不足
        if (result.appealScore < 60) {
            weaknesses.add("主题表现力一般");
        }

        return weaknesses;
    }

    /**
     * 生成改进建议
     */
    private List<String> generateImprovementSuggestions(ScoringResult result) {
        List<String> suggestions = new ArrayList<>();

        if (result.technicalScore < 70) {
            suggestions.add("尝试使用三脚架减少抖动");
            suggestions.add("调整ISO设置以减少噪点");
        }

        if (result.compositionScore < 70) {
            suggestions.add("运用三分法构图原则");
            suggestions.add("注意主体的黄金分割位置");
        }

        if (result.appealScore < 70) {
            suggestions.add("尝试不同的拍摄角度");
            suggestions.add("利用光线增强氛围感");
        }

        return suggestions;
    }

    /**
     * 检查是否需要重新评分
     */
    private boolean needsRescoring(PhotoAIScoring scoring) {
        return scoring.getScoringStatus() == PhotoAIScoring.AIScoringStatus.FAILED ||
               !scoringVersion.equals(scoring.getScoreVersion());
    }

    /**
     * 检查三分法合规性
     */
    private boolean isRuleOfThirdsCompliant(double focusX, double focusY) {
        double[] thirds = {1/3.0, 2/3.0};
        return Arrays.stream(thirds).anyMatch(t -> Math.abs(focusX - t) < 0.1) &&
               Arrays.stream(thirds).anyMatch(t -> Math.abs(focusY - t) < 0.1);
    }

    /**
     * 更新评分记录
     */
    private void updateScoringWithResult(PhotoAIScoring scoring, ScoringResult result) throws IOException {
        scoring.setTechnicalScore(result.technicalScore);
        scoring.setCompositionScore(result.compositionScore);
        scoring.setAppealScore(result.appealScore);
        scoring.setTechnicalWeight(technicalWeight);
        scoring.setCompositionWeight(compositionWeight);
        scoring.setAppealWeight(appealWeight);

        // 存储详细分析结果
        scoring.setTechnicalAnalysis(objectMapper.writeValueAsString(result.technicalAnalysis));
        scoring.setCompositionAnalysis(objectMapper.writeValueAsString(result.compositionAnalysis));
        scoring.setAppealAnalysis(objectMapper.writeValueAsString(result.appealAnalysis));

        // 存储优点和不足
        scoring.setStrengths(objectMapper.writeValueAsString(result.strengths));
        scoring.setWeaknesses(objectMapper.writeValueAsString(result.weaknesses));
        scoring.setImprovementSuggestions(objectMapper.writeValueAsString(result.improvementSuggestions));

        // 存储使用的模型信息
        Map<String, Object> modelsUsed = new HashMap<>();
        modelsUsed.put("classification", classificationService != null);
        modelsUsed.put("saliency", saliencyService != null);
        modelsUsed.put("color_analysis", colorAnalysisService != null);
        scoring.setModelsUsed(objectMapper.writeValueAsString(modelsUsed));
    }

    /**
     * 评分结果内部类
     */
    private static class ScoringResult {
        double technicalScore;
        double compositionScore;
        double appealScore;
        Map<String, Object> technicalAnalysis;
        Map<String, Object> compositionAnalysis;
        Map<String, Object> appealAnalysis;
        SceneRecognitionService.SceneRecognitionResult sceneAnalysis;
        EmotionAnalysisService.EmotionAnalysisResult emotionAnalysis;
        List<String> strengths;
        List<String> weaknesses;
        List<String> improvementSuggestions;
        Map<String, Boolean> modelsUsed;
    }
}
