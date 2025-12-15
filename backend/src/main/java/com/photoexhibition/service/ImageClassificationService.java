package com.photoexhibition.service;

import ai.onnxruntime.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于ONNX模型的图像分类服务
 * 支持场景识别、物体识别等智能标签生成
 * 如果模型未配置，将回退到基于规则的方法
 */
@Slf4j
@Service
public class ImageClassificationService implements AutoCloseable {

    @Value("${smart-tag.classification.enabled:false}")
    private boolean enabled;

    @Value("${smart-tag.classification.model-path:./models/image_classification.onnx}")
    private String modelPath;

    @Value("${smart-tag.classification.confidence-threshold:0.3}")
    private double confidenceThreshold;

    @Value("${smart-tag.classification.top-k:5}")
    private int topK;

    @Value("${smart-tag.classification.strict-top-k:false}")
    private boolean strictTopK;

    @Value("${smart-tag.classification.language:zh}")
    private String language;

    private OrtEnvironment env;
    private OrtSession session;
    private List<String> classLabels;
    private Map<String, String> chineseLabelMap;

    /**
     * 对图像进行分类，返回标签列表
     * 如果模型未启用或加载失败，返回空列表（会回退到基于规则的方法）
     */
    public List<ClassificationResult> classify(File imageFile) {
        if (!enabled) {
            return Collections.emptyList();
        }

        try {
            ensureSession();
            if (session == null) {
                return Collections.emptyList();
            }

            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
                return Collections.emptyList();
            }

            // 预处理图像（根据模型要求调整尺寸，常见为224x224或299x299）
            int inputSize = 224; // ImageNet标准尺寸
            BufferedImage resized = resizeAndCrop(img, inputSize);
            float[] inputTensor = preprocessImage(resized, inputSize);

            // 运行推理
            OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputTensor),
                    new long[]{1, 3, inputSize, inputSize});

            try (OrtSession.Result result = session.run(
                    Collections.singletonMap(session.getInputNames().iterator().next(), input))) {

                // 解析输出（可能是logits或softmax后的概率分布）
                Object output = result.get(0).getValue();
                float[] logits = null;

                if (output instanceof float[][]) {
                    logits = ((float[][]) output)[0];
                } else if (output instanceof float[]) {
                    logits = (float[]) output;
                }

                if (logits == null || logits.length == 0) {
                    return Collections.emptyList();
                }

                // 检查是否需要softmax（如果值不在[0,1]范围内，可能是logits）
                float[] probabilities = applySoftmaxIfNeeded(logits);

                // 获取top-k结果
                return getTopKResults(probabilities);
            } finally {
                input.close();
            }
        } catch (Exception e) {
            log.debug("图像分类失败: {}，将回退到基于规则的方法", imageFile.getName(), e);
        }

        return Collections.emptyList();
    }

    /**
     * 确保模型会话已加载
     */
    private void ensureSession() {
        if (session != null) return;

        try {
            java.io.File modelFile = new java.io.File(modelPath);
            if (!modelFile.exists()) {
                log.debug("图像分类模型文件不存在: {}，将使用基于规则的方法", modelPath);
                return;
            }

            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            // 可以根据需要设置线程数等选项
            session = env.createSession(modelPath, opts);
            
            // 尝试加载类别标签（如果存在labels.txt文件）
            loadClassLabels();
            
            // 如果使用中文，加载中文标签映射
            if ("zh".equalsIgnoreCase(language)) {
                loadChineseLabelMap();
            }
            
            log.info("图像分类模型已加载: {} (语言: {})", modelPath, language);
        } catch (Exception e) {
            log.debug("加载图像分类模型失败: {}，将使用基于规则的方法", modelPath, e);
            session = null;
        }
    }

    /**
     * 加载类别标签文件（可选）
     * 如果存在labels.txt文件，则加载；否则尝试加载ImageNet标准标签
     */
    private void loadClassLabels() {
        try {
            // 优先尝试加载自定义标签文件
            java.io.File labelsFile = new java.io.File(modelPath.replace(".onnx", "_labels.txt"));
            if (labelsFile.exists()) {
                classLabels = java.nio.file.Files.readAllLines(labelsFile.toPath())
                        .stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                log.info("已加载自定义类别标签: {} 个类别", classLabels.size());
                return;
            }
            
            // 尝试加载ImageNet标准标签文件
            java.io.File imagenetLabelsFile = new java.io.File("./models/imagenet_labels.txt");
            if (imagenetLabelsFile.exists()) {
                classLabels = java.nio.file.Files.readAllLines(imagenetLabelsFile.toPath())
                        .stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                log.info("已加载ImageNet类别标签: {} 个类别", classLabels.size());
                return;
            }
            
            // 如果都没有，使用默认的简化标签（仅用于测试）
            log.warn("未找到标签文件，使用默认简化标签。建议下载ImageNet标签文件到 ./models/imagenet_labels.txt");
            classLabels = getDefaultLabels();
        } catch (Exception e) {
            log.warn("加载类别标签失败，使用默认标签: {}", e.getMessage());
            classLabels = getDefaultLabels();
        }
    }

    /**
     * 获取默认标签列表（ImageNet常用类别的中文映射）
     */
    private List<String> getDefaultLabels() {
        // 这里只包含一些常用的场景和物体类别
        // 实际使用时，应该根据你的模型输出类别来配置
        return Arrays.asList(
                // 场景类
                "室内", "室外", "自然", "城市", "建筑", "风景", "海滩", "山", "森林", "公园",
                // 物体类
                "动物", "猫", "狗", "鸟", "花", "植物", "食物", "车辆", "汽车", "自行车"
        );
    }

    /**
     * 调整图像大小并居中裁剪
     */
    private BufferedImage resizeAndCrop(BufferedImage img, int targetSize) {
        int w = img.getWidth();
        int h = img.getHeight();

        // 计算缩放比例，使短边等于targetSize
        double scale = (double) targetSize / Math.min(w, h);
        int newW = (int) (w * scale);
        int newH = (int) (h * scale);

        // 缩放图像
        Image scaled = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        resized.getGraphics().drawImage(scaled, 0, 0, null);

        // 居中裁剪
        int x = (newW - targetSize) / 2;
        int y = (newH - targetSize) / 2;
        return resized.getSubimage(Math.max(0, x), Math.max(0, y), targetSize, targetSize);
    }

    /**
     * 预处理图像：归一化到[0, 1]并转换为CHW格式
     */
    private float[] preprocessImage(BufferedImage img, int size) {
        float[] tensor = new float[3 * size * size];
        int idx = 0;

        // ImageNet标准化：均值[0.485, 0.456, 0.406]，标准差[0.229, 0.224, 0.225]
        float[] mean = {0.485f, 0.456f, 0.406f};
        float[] std = {0.229f, 0.224f, 0.225f};

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = img.getRGB(x, y);
                float r = ((rgb >> 16) & 0xff) / 255.0f;
                float g = ((rgb >> 8) & 0xff) / 255.0f;
                float b = (rgb & 0xff) / 255.0f;

                // 标准化
                tensor[idx] = (r - mean[0]) / std[0];
                tensor[idx + size * size] = (g - mean[1]) / std[1];
                tensor[idx + 2 * size * size] = (b - mean[2]) / std[2];
                idx++;
            }
        }

        return tensor;
    }

    /**
     * 如果需要，对logits应用softmax转换为概率
     */
    private float[] applySoftmaxIfNeeded(float[] values) {
        // 检查值是否在[0,1]范围内（可能是概率）
        boolean isProbability = true;
        float sum = 0.0f;
        for (float v : values) {
            if (v < 0 || v > 1) {
                isProbability = false;
            }
            sum += v;
        }
        
        // 如果值在[0,1]范围内且总和接近1，认为是概率，直接返回
        if (isProbability && Math.abs(sum - 1.0f) < 0.1f) {
            return values;
        }
        
        // 否则认为是logits，需要softmax
        float max = Float.NEGATIVE_INFINITY;
        for (float v : values) {
            if (v > max) max = v;
        }
        
        float[] exp = new float[values.length];
        float expSum = 0.0f;
        for (int i = 0; i < values.length; i++) {
            exp[i] = (float) Math.exp(values[i] - max); // 减去max避免溢出
            expSum += exp[i];
        }
        
        float[] probabilities = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            probabilities[i] = exp[i] / expSum;
        }
        
        return probabilities;
    }

    /**
     * 获取top-k分类结果
     * 
     * 策略说明：
     * 1. strict-top-k=false（推荐）：先按置信度过滤，再取top-k，保证质量
     * 2. strict-top-k=true：强制返回前k个，即使置信度很低也返回，可能不准确
     */
    private List<ClassificationResult> getTopKResults(float[] probabilities) {
        List<ClassificationResult> results = new ArrayList<>();

        // 创建索引数组并排序（按置信度降序）
        Integer[] indices = new Integer[probabilities.length];
        for (int i = 0; i < probabilities.length; i++) {
            indices[i] = i;
        }

        Arrays.sort(indices, (a, b) -> Float.compare(probabilities[b], probabilities[a]));

        if (strictTopK) {
            // 策略1：强制返回前k个（忽略置信度阈值）
            // 注意：这可能导致低置信度的标签被包含，准确率可能下降
            for (int i = 0; i < Math.min(topK, indices.length); i++) {
                int idx = indices[i];
                float confidence = probabilities[idx];
                
                String label = idx < classLabels.size() ? classLabels.get(idx) : "类别" + idx;
                
                // 如果使用中文，尝试映射到中文标签
                if ("zh".equalsIgnoreCase(language) && chineseLabelMap != null) {
                    String chineseLabel = chineseLabelMap.get(label.toLowerCase());
                    if (chineseLabel != null && !chineseLabel.isEmpty()) {
                        label = chineseLabel;
                    }
                }
                
                results.add(new ClassificationResult(label, confidence));
            }
            
            // 如果强制top-k但置信度都很低，记录警告
            if (!results.isEmpty() && results.get(0).getConfidence() < confidenceThreshold) {
                log.debug("强制top-k模式：前{}个结果的置信度均低于阈值{}，准确率可能下降", 
                    topK, confidenceThreshold);
            }
        } else {
            // 策略2：先过滤置信度，再取top-k（推荐）
            // 这样可以保证返回的标签都有一定的可信度
            for (int i = 0; i < indices.length; i++) {
                if (results.size() >= topK) {
                    break; // 已收集足够的标签
                }
                
                int idx = indices[i];
                float confidence = probabilities[idx];

                // 如果置信度低于阈值，跳过
                if (confidence < confidenceThreshold) {
                    continue;
                }

                String label = idx < classLabels.size() ? classLabels.get(idx) : "类别" + idx;
                
                // 如果使用中文，尝试映射到中文标签
                if ("zh".equalsIgnoreCase(language) && chineseLabelMap != null) {
                    String chineseLabel = chineseLabelMap.get(label.toLowerCase());
                    if (chineseLabel != null && !chineseLabel.isEmpty()) {
                        label = chineseLabel;
                    }
                }
                
                results.add(new ClassificationResult(label, confidence));
            }
        }

        return results;
    }

    /**
     * 加载中文标签映射文件
     */
    private void loadChineseLabelMap() {
        try {
            java.io.File zhLabelsFile = new java.io.File("./models/imagenet_labels_zh.txt");
            if (zhLabelsFile.exists()) {
                chineseLabelMap = new HashMap<>();
                java.nio.file.Files.readAllLines(zhLabelsFile.toPath())
                        .stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                        .forEach(line -> {
                            if (line.contains("|")) {
                                String[] parts = line.split("\\|", 2);
                                if (parts.length == 2) {
                                    String key = parts[0].trim().toLowerCase();
                                    String value = parts[1].trim();
                                    // 确保key和value都不为空
                                    if (!key.isEmpty() && !value.isEmpty()) {
                                        chineseLabelMap.put(key, value);
                                    }
                                }
                            }
                        });
                log.info("已加载中文标签映射: {} 个类别", chineseLabelMap.size());
            } else {
                log.warn("中文标签映射文件不存在: {}，将使用英文标签", zhLabelsFile.getPath());
            }
        } catch (Exception e) {
            log.warn("加载中文标签映射失败: {}", e.getMessage());
        }
    }

    /**
     * 将模型输出的类别映射到中文标签（兼容旧接口）
     */
    public String mapToChineseTag(String className) {
        if (chineseLabelMap != null) {
            String chineseLabel = chineseLabelMap.get(className.toLowerCase());
            if (chineseLabel != null && !chineseLabel.isEmpty()) {
                return chineseLabel;
            }
        }
        return className;
    }

    /**
     * 检查标签名称是否可能是ImageNet分类标签（用于识别智能标签）
     * @param tagName 标签名称（可能是中文或英文）
     * @return true如果标签可能是ImageNet分类标签
     */
    public boolean isPossibleImageNetTag(String tagName) {
        if (tagName == null || tagName.isEmpty()) {
            return false;
        }
        
        // 检查英文标签列表
        if (classLabels != null) {
            String lowerTagName = tagName.toLowerCase();
            for (String label : classLabels) {
                if (label.toLowerCase().equals(lowerTagName)) {
                    return true;
                }
            }
        }
        
        // 检查中文映射
        if (chineseLabelMap != null) {
            // 检查是否是中文映射的值
            if (chineseLabelMap.containsValue(tagName)) {
                return true;
            }
            // 检查英文标签映射后的中文标签
            String lowerTagName = tagName.toLowerCase();
            String mappedChinese = chineseLabelMap.get(lowerTagName);
            if (mappedChinese != null && mappedChinese.equals(tagName)) {
                return true;
            }
        }
        
        return false;
    }

    @Override
    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * 分类结果
     */
    public static class ClassificationResult {
        private String label;
        private float confidence;

        public ClassificationResult(String label, float confidence) {
            this.label = label;
            this.confidence = confidence;
        }

        public String getLabel() {
            return label;
        }

        public float getConfidence() {
            return confidence;
        }
    }
}

