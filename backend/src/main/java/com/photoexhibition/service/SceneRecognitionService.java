package com.photoexhibition.service;

import ai.onnxruntime.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
import java.util.stream.Collectors;

/**
 * 场景识别服务
 * 专门用于识别照片中的场景和事件类型（如婚礼、毕业典礼、生日派对等）
 */
@Slf4j
@Service
public class SceneRecognitionService implements AutoCloseable {

    @Value("${ai.scene-recognition.enabled:false}")
    private boolean enabled;

    @Value("${ai.scene-recognition.model-path:./models/scene_recognition.onnx}")
    private String modelPath;

    @Value("${ai.scene-recognition.confidence-threshold:0.3}")
    private double confidenceThreshold;

    private OrtEnvironment env;
    private OrtSession session;
    private List<String> sceneLabels;
    private ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (enabled) {
            initializeSceneRecognition();
        }
    }

    /**
     * 初始化场景识别模型
     */
    private void initializeSceneRecognition() {
        try {
            File modelFile = new File(modelPath);
            if (!modelFile.exists()) {
                log.info("场景识别模型文件不存在: {}，将使用基于规则的方法", modelPath);
                return;
            }

            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            session = env.createSession(modelPath, opts);

            loadSceneLabels();
            log.info("场景识别模型已加载: {}", modelPath);
        } catch (Exception e) {
            log.warn("场景识别模型加载失败，将使用基于规则的方法: {}", e.getMessage());
        }
    }

    /**
     * 识别照片场景
     */
    public SceneRecognitionResult recognizeScene(File imageFile) {
        SceneRecognitionResult result = new SceneRecognitionResult();

        if (!enabled || session == null) {
            // 使用基于规则的方法
            result.scenes = ruleBasedSceneRecognition(imageFile);
            result.confidence = 0.5f; // 规则方法的默认置信度
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

                // 获取识别结果
                result.scenes = getTopScenes(probabilities);
                result.confidence = result.scenes.isEmpty() ? 0.0f :
                    result.scenes.get(0).confidence;

            } finally {
                input.close();
            }
        } catch (Exception e) {
            log.warn("场景识别失败，使用基于规则的方法: {}", e.getMessage());
            result.scenes = ruleBasedSceneRecognition(imageFile);
            result.confidence = 0.5f;
        }

        return result;
    }

    /**
     * 基于规则的场景识别（当AI模型不可用时使用）
     */
    private List<SceneResult> ruleBasedSceneRecognition(File imageFile) {
        List<SceneResult> scenes = new ArrayList<>();

        try {
            // 这里可以基于文件名、路径等信息进行规则判断
            String fileName = imageFile.getName().toLowerCase();
            String fullPath = imageFile.getAbsolutePath().toLowerCase();

            log.debug("场景识别分析文件: {} (路径: {})", fileName, fullPath);

            // 检查路径中的主分类目录
            if (fullPath.contains("/人像/")) {
                scenes.add(new SceneResult("人像摄影", 0.9f));
            } else if (fullPath.contains("/风景/") || fullPath.contains("/landscape/")) {
                scenes.add(new SceneResult("风景摄影", 0.9f));
            } else if (fullPath.contains("/活动/") || fullPath.contains("/event/")) {
                scenes.add(new SceneResult("活动", 0.8f));
            } else if (fullPath.contains("/扫街/") || fullPath.contains("/街头/")) {
                scenes.add(new SceneResult("街头摄影", 0.8f));
            } else if (fullPath.contains("/游玩/") || fullPath.contains("/旅行/")) {
                scenes.add(new SceneResult("旅行", 0.8f));
            }

            // 检查路径中的具体场景关键词
            if (fullPath.contains("/婚礼/") || fullPath.contains("/wedding/")) {
                scenes.add(new SceneResult("婚礼", 0.9f));
            } else if (fullPath.contains("/婚纱/") || fullPath.contains("/weddingdress/")) {
                scenes.add(new SceneResult("婚纱摄影", 0.85f));
            }

            if (fullPath.contains("/毕业/") || fullPath.contains("/graduation/")) {
                scenes.add(new SceneResult("毕业典礼", 0.9f));
            }

            if (fullPath.contains("/生日/") || fullPath.contains("/birthday/")) {
                scenes.add(new SceneResult("生日派对", 0.8f));
            }

            if (fullPath.contains("/聚会/") || fullPath.contains("/party/") ||
                fullPath.contains("/派对/")) {
                scenes.add(new SceneResult("聚会", 0.7f));
            }

            if (fullPath.contains("/运动/") || fullPath.contains("/sports/") ||
                fullPath.contains("/运动会/")) {
                scenes.add(new SceneResult("运动", 0.8f));
            }

            if (fullPath.contains("/音乐/") || fullPath.contains("/music/") ||
                fullPath.contains("/演唱会/") || fullPath.contains("/concert/")) {
                scenes.add(new SceneResult("音乐会", 0.8f));
            }

            if (fullPath.contains("/会议/") || fullPath.contains("/meeting/")) {
                scenes.add(new SceneResult("会议", 0.7f));
            }

            if (fullPath.contains("/展览/") || fullPath.contains("/exhibition/") ||
                fullPath.contains("/展会/")) {
                scenes.add(new SceneResult("展览", 0.7f));
            }

            if (fullPath.contains("/节日/") || fullPath.contains("/festival/") ||
                fullPath.contains("/庆典/") || fullPath.contains("/celebration/")) {
                scenes.add(new SceneResult("节日庆典", 0.8f));
            }

            if (fullPath.contains("/家庭/") || fullPath.contains("/family/")) {
                scenes.add(new SceneResult("家庭聚餐", 0.7f));
            }

            if (fullPath.contains("/朋友/") || fullPath.contains("/friends/")) {
                scenes.add(new SceneResult("朋友聚会", 0.7f));
            }

            if (fullPath.contains("/公司/") || fullPath.contains("/company/") ||
                fullPath.contains("/企业/") || fullPath.contains("/business/")) {
                scenes.add(new SceneResult("公司活动", 0.7f));
            }

            if (fullPath.contains("/户外/") || fullPath.contains("/outdoor/")) {
                scenes.add(new SceneResult("户外活动", 0.7f));
            }

            if (fullPath.contains("/室内/") || fullPath.contains("/indoor/")) {
                scenes.add(new SceneResult("室内活动", 0.6f));
            }

            if (fullPath.contains("/艺术/") || fullPath.contains("/art/")) {
                scenes.add(new SceneResult("艺术摄影", 0.7f));
            }

            if (fullPath.contains("/商业/") || fullPath.contains("/commercial/")) {
                scenes.add(new SceneResult("商业摄影", 0.7f));
            }

            // 检查文件名中的关键词（作为补充）
            if (fileName.contains("婚礼") || fileName.contains("wedding")) {
                scenes.add(new SceneResult("婚礼", 0.8f));
            }

            if (fileName.contains("毕业") || fileName.contains("graduation")) {
                scenes.add(new SceneResult("毕业典礼", 0.8f));
            }

            if (fileName.contains("生日") || fileName.contains("birthday")) {
                scenes.add(new SceneResult("生日派对", 0.7f));
            }

            if (fileName.contains("旅行") || fileName.contains("travel") ||
                fileName.contains("旅游") || fileName.contains("tour")) {
                scenes.add(new SceneResult("旅行", 0.6f));
            }

            if (fileName.contains("聚会") || fileName.contains("party") ||
                fileName.contains("派对")) {
                scenes.add(new SceneResult("聚会", 0.6f));
            }

            if (fileName.contains("运动") || fileName.contains("sports")) {
                scenes.add(new SceneResult("运动", 0.7f));
            }

            if (fileName.contains("音乐") || fileName.contains("music") ||
                fileName.contains("演唱会") || fileName.contains("concert")) {
                scenes.add(new SceneResult("音乐会", 0.7f));
            }

            // 如果没有识别到特定场景，返回通用场景
            if (scenes.isEmpty()) {
                log.debug("未识别到特定场景关键词，返回默认场景: 日常照片");
                scenes.add(new SceneResult("日常照片", 0.4f));
            } else {
                log.debug("基于规则识别到场景: {}", scenes.stream()
                    .map(s -> s.scene + "(" + String.format("%.2f", s.confidence) + ")")
                    .collect(Collectors.toList()));
            }

        } catch (Exception e) {
            log.warn("基于规则的场景识别失败: {}", e.getMessage());
            scenes.add(new SceneResult("未知场景", 0.1f));
        }

        return scenes;
    }

    /**
     * 加载场景标签
     */
    private void loadSceneLabels() {
        sceneLabels = Arrays.asList(
            "婚礼", "婚纱摄影", "毕业典礼", "生日派对", "旅行", "聚会", "活动",
            "运动", "音乐会", "会议", "展览", "节日庆典", "家庭聚餐", "朋友聚会",
            "公司活动", "户外活动", "室内活动", "风景摄影", "人像摄影", "艺术摄影",
            "商业摄影", "日常照片", "街头摄影", "纪实摄影", "静物摄影"
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
     * 获取top场景结果
     */
    private List<SceneResult> getTopScenes(float[] probabilities) {
        List<SceneResult> results = new ArrayList<>();

        // 创建索引数组并排序
        Integer[] indices = new Integer[probabilities.length];
        for (int i = 0; i < probabilities.length; i++) {
            indices[i] = i;
        }

        Arrays.sort(indices, (a, b) -> Float.compare(probabilities[b], probabilities[a]));

        // 获取置信度高于阈值的结果
        for (int i = 0; i < Math.min(3, indices.length); i++) {
            int idx = indices[i];
            float confidence = probabilities[idx];
            if (confidence >= confidenceThreshold) {
                String label = idx < sceneLabels.size() ? sceneLabels.get(idx) : "未知场景";
                results.add(new SceneResult(label, confidence));
            }
        }

        return results;
    }

    @PreDestroy
    @Override
    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * 场景识别结果
     */
    public static class SceneRecognitionResult {
        public List<SceneResult> scenes = new ArrayList<>();
        public float confidence;
    }

    /**
     * 单个场景结果
     */
    public static class SceneResult {
        public String scene;
        public float confidence;

        public SceneResult(String scene, float confidence) {
            this.scene = scene;
            this.confidence = confidence;
        }
    }
}
