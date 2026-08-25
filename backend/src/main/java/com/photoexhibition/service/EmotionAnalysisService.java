package com.photoexhibition.service;

import ai.onnxruntime.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.List;

/**
 * 情感分析服务
 * 基于图像特征分析照片传达的情感色彩
 */
@Slf4j
@Service
public class EmotionAnalysisService implements AutoCloseable {

    @Value("${ai.emotion-analysis.enabled:false}")
    private boolean enabled;

    @Value("${ai.emotion-analysis.model-path:./models/emotion_analysis.onnx}")
    private String modelPath;

    @Value("${ai.emotion-analysis.confidence-threshold:0.3}")
    private double confidenceThreshold;

    @Autowired
    private FaceService faceService;

    private OrtEnvironment env;
    private OrtSession session;
    private List<String> emotionLabels;

    @PostConstruct
    public void init() {
        if (enabled) {
            initializeEmotionAnalysis();
        }
    }

    /**
     * 初始化情感分析模型
     */
    private void initializeEmotionAnalysis() {
        try {
            File modelFile = new File(modelPath);
            if (!modelFile.exists()) {
                log.info("情感分析模型文件不存在: {}，将使用基于规则的方法", sanitizePath(modelPath));
                return;
            }

            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            session = env.createSession(modelPath, opts);

            loadEmotionLabels();
            log.info("情感分析模型已加载: {}", sanitizePath(modelPath));
        } catch (Exception e) {
            log.warn("情感分析模型加载失败，将使用基于规则的方法: {}", e.getMessage());
        }
    }

    /**
     * 分析照片情感
     */
    public EmotionAnalysisResult analyzeEmotion(File imageFile, Long photoId) {
        EmotionAnalysisResult result = new EmotionAnalysisResult();

        if (!enabled || session == null) {
            // 使用基于规则的方法
            result.emotions = ruleBasedEmotionAnalysis(imageFile, photoId);
            result.primaryEmotion = result.emotions.isEmpty() ? "中性" :
                result.emotions.get(0).emotion;
            return result;
        }

        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
                return result;
            }

            // 预处理图像
            int inputSize = 224;
            BufferedImage resized = resizeImage(img, inputSize);
            float[] inputTensor = preprocessImage(resized, inputSize);

            // 运行推理
            OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputTensor),
                    new long[]{1, 3, inputSize, inputSize});

            try (OrtSession.Result inferenceResult = session.run(
                    Collections.singletonMap(session.getInputNames().iterator().next(), input))) {

                // 解析输出
                Object output = inferenceResult.get(0).getValue();
                float[] probabilities = extractProbabilities(output);

                // 获取情感分析结果
                result.emotions = getTopEmotions(probabilities);
                result.primaryEmotion = result.emotions.isEmpty() ? "未知" :
                    result.emotions.get(0).emotion;

            } finally {
                input.close();
            }
        } catch (Exception e) {
            log.warn("情感分析失败，使用基于规则的方法: {}", e.getMessage());
            result.emotions = ruleBasedEmotionAnalysis(imageFile, photoId);
            result.primaryEmotion = result.emotions.isEmpty() ? "中性" :
                result.emotions.get(0).emotion;
        }

        return result;
    }

    /**
     * 基于规则的情感分析（当AI模型不可用时使用）
     */
    private List<EmotionResult> ruleBasedEmotionAnalysis(File imageFile, Long photoId) {
        List<EmotionResult> emotions = new ArrayList<>();

        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
                emotions.add(new EmotionResult("中性", 0.5f));
                return emotions;
            }

            // 基于颜色分析情感
            Color dominantColor = analyzeDominantColor(img);

            if (dominantColor != null) {

                // 暖色调通常传达温暖、愉悦的情感
                if (isWarmColor(dominantColor)) {
                    emotions.add(new EmotionResult("温暖", 0.7f));
                    emotions.add(new EmotionResult("愉悦", 0.6f));
                }
                // 冷色调通常传达冷静、安宁的情感
                else if (isCoolColor(dominantColor)) {
                    emotions.add(new EmotionResult("冷静", 0.6f));
                    emotions.add(new EmotionResult("安宁", 0.5f));
                }
                // 明亮色彩通常传达活力和快乐
                else if (isBrightColor(dominantColor)) {
                    emotions.add(new EmotionResult("活力", 0.6f));
                    emotions.add(new EmotionResult("快乐", 0.5f));
                }
            }

            // 基于人脸分析情感
            try {
                List<com.photoexhibition.entity.Face> faces = faceService.getFacesByPhoto(photoId);
                if (faces != null && !faces.isEmpty()) {
                    // 如果有人脸，通常传达社交、亲密的情感
                    emotions.add(new EmotionResult("亲密", 0.6f));
                    emotions.add(new EmotionResult("社交", 0.5f));
                }
            } catch (Exception e) {
                // 人脸分析失败不影响整体结果
            }

            // 检查文件名中的情感关键词
            String fileName = imageFile.getName().toLowerCase();
            if (fileName.contains("笑") || fileName.contains("happy") ||
                fileName.contains("smile")) {
                emotions.add(new EmotionResult("快乐", 0.8f));
            }
            if (fileName.contains("爱") || fileName.contains("love")) {
                emotions.add(new EmotionResult("爱", 0.8f));
            }
            if (fileName.contains("悲伤") || fileName.contains("sad")) {
                emotions.add(new EmotionResult("悲伤", 0.7f));
            }

            // 如果没有识别到特定情感，返回中性
            if (emotions.isEmpty()) {
                emotions.add(new EmotionResult("中性", 0.5f));
            }

            // 按置信度排序
            emotions.sort((a, b) -> Float.compare(b.confidence, a.confidence));

        } catch (Exception e) {
            log.warn("基于规则的情感分析失败: {}", e.getMessage());
            emotions.add(new EmotionResult("未知", 0.1f));
        }

        return emotions;
    }

    /**
     * 判断是否为暖色调
     */
    private boolean isWarmColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float hue = hsb[0] * 360; // 转换为度数

        // 暖色调范围：红色到黄色 (0-60度)
        return hue >= 0 && hue <= 60;
    }

    /**
     * 判断是否为冷色调
     */
    private boolean isCoolColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float hue = hsb[0] * 360;

        // 冷色调范围：蓝色到青色 (180-300度)
        return hue >= 180 && hue <= 300;
    }

    /**
     * 判断是否为明亮色彩
     */
    private boolean isBrightColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float brightness = hsb[2];
        float saturation = hsb[1];

        // 高亮度高饱和度
        return brightness > 0.7 && saturation > 0.3;
    }

    /**
     * 加载情感标签
     */
    private void loadEmotionLabels() {
        emotionLabels = Arrays.asList(
            "快乐", "悲伤", "愤怒", "恐惧", "惊讶", "厌恶", "爱", "温暖",
            "冷静", "活力", "安宁", "孤独", "兴奋", "平静", "怀念", "希望",
            "失望", "感激", "骄傲", "尴尬", "羡慕", "满足", "焦虑", "放松",
            "中性", "亲密", "社交", "孤独", "神秘", "宁静"
        );
    }

    /**
     * 调整图像大小
     */
    private BufferedImage resizeImage(BufferedImage img, int targetSize) {
        Image scaled = img.getScaledInstance(targetSize, targetSize, Image.SCALE_SMOOTH);
        BufferedImage result = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        return result;
    }

    /**
     * 预处理图像
     */
    private float[] preprocessImage(BufferedImage img, int size) {
        float[] tensor = new float[3 * size * size];
        int idx = 0;

        float[] mean = {0.485f, 0.456f, 0.406f};
        float[] std = {0.229f, 0.224f, 0.225f};

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = img.getRGB(x, y);
                float r = ((rgb >> 16) & 0xff) / 255.0f;
                float g = ((rgb >> 8) & 0xff) / 255.0f;
                float b = (rgb & 0xff) / 255.0f;

                tensor[idx] = (r - mean[0]) / std[0];
                tensor[idx + size * size] = (g - mean[1]) / std[1];
                tensor[idx + 2 * size * size] = (b - mean[2]) / std[2];
                idx++;
            }
        }

        return tensor;
    }

    /**
     * 提取概率数组
     */
    private float[] extractProbabilities(Object output) {
        if (output instanceof float[][]) {
            return ((float[][]) output)[0];
        } else if (output instanceof float[]) {
            return (float[]) output;
        }
        return new float[0];
    }

    /**
     * 获取top情感结果
     */
    private List<EmotionResult> getTopEmotions(float[] probabilities) {
        List<EmotionResult> results = new ArrayList<>();

        Integer[] indices = new Integer[probabilities.length];
        for (int i = 0; i < probabilities.length; i++) {
            indices[i] = i;
        }

        Arrays.sort(indices, (a, b) -> Float.compare(probabilities[b], probabilities[a]));

        for (int i = 0; i < Math.min(3, indices.length); i++) {
            int idx = indices[i];
            float confidence = probabilities[idx];
            if (confidence >= confidenceThreshold) {
                String label = idx < emotionLabels.size() ? emotionLabels.get(idx) : "未知";
                results.add(new EmotionResult(label, confidence));
            }
        }

        return results;
    }

    /**
     * 简单的主色调分析
     */
    private Color analyzeDominantColor(BufferedImage img) {
        try {
            int width = img.getWidth();
            int height = img.getHeight();

            // 采样一些像素来确定主色调
            List<Color> samples = new ArrayList<>();
            int sampleStep = Math.max(1, Math.min(width, height) / 50); // 采样步长

            for (int x = 0; x < width; x += sampleStep) {
                for (int y = 0; y < height; y += sampleStep) {
                    int rgb = img.getRGB(x, y);
                    samples.add(new Color(rgb));
                }
            }

            if (samples.isEmpty()) {
                return null;
            }

            // 计算平均颜色作为主色调的近似
            int totalR = 0, totalG = 0, totalB = 0;
            for (Color color : samples) {
                totalR += color.getRed();
                totalG += color.getGreen();
                totalB += color.getBlue();
            }

            return new Color(
                totalR / samples.size(),
                totalG / samples.size(),
                totalB / samples.size()
            );

        } catch (Exception e) {
            return null;
        }
    }

    @PreDestroy
    @Override
    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (Exception ignored) {
        }
        session = null;
        env = null;
    }

    private String sanitizePath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        String normalized = path.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    /**
     * 情感分析结果
     */
    public static class EmotionAnalysisResult {
        public List<EmotionResult> emotions = new ArrayList<>();
        public String primaryEmotion;
    }

    /**
     * 单个情感结果
     */
    public static class EmotionResult {
        public String emotion;
        public float confidence;

        public EmotionResult(String emotion, float confidence) {
            this.emotion = emotion;
            this.confidence = confidence;
        }
    }

    public String getModelPath() {
        return modelPath;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public synchronized boolean isModelLoaded() {
        if (enabled && session == null) {
            initializeEmotionAnalysis();
        }
        return enabled && session != null;
    }

    public synchronized boolean reloadModel() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (Exception ignored) {
        }
        session = null;
        env = null;
        if (enabled) {
            initializeEmotionAnalysis();
        }
        return enabled && session != null;
    }
}
