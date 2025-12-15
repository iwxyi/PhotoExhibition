package com.photoexhibition.service;

import ai.onnxruntime.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.Collections;

/**
 * 基于ONNX模型的显著性检测服务
 * 使用深度学习模型（如U²-Net、BASNet）进行更准确的显著性检测
 * 如果模型未启用或加载失败，返回null，会回退到混合策略
 */
@Slf4j
@Service
public class SaliencyDetectionService implements AutoCloseable {

    @Value("${smart-tag.subject-detection.saliency.enabled:false}")
    private boolean enabled;

    @Value("${smart-tag.subject-detection.saliency.model-path:./models/saliency_detection.onnx}")
    private String modelPath;

    @Value("${smart-tag.subject-detection.saliency.input-size:320}")
    private int inputSize; // 模型输入尺寸，RMBG-1.4使用1024x1024，U²-Net Lite使用320x320

    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;
    private int actualInputSize; // 实际使用的输入尺寸（可能从模型自动检测）

    /**
     * 检测图像的主体位置（使用显著性检测模型）
     * 返回焦点位置的百分比坐标 [focusX, focusY]，范围 0-100
     * 如果模型未启用或检测失败，返回null
     */
    public double[] detectSaliency(File imageFile, int originalWidth, int originalHeight) {
        if (!enabled) {
            return null;
        }

        try {
            ensureSession();
            if (session == null) {
                return null;
            }

            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
                return null;
            }

            // 预处理图像：保持宽高比，短边缩放到actualInputSize（自动检测的尺寸）
            int sizeToUse = actualInputSize > 0 ? actualInputSize : inputSize;
            BufferedImage resized = resizeImage(img, sizeToUse);
            float[] inputTensor = preprocessImage(resized, sizeToUse);

            // 运行推理
            OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputTensor),
                    new long[]{1, 3, sizeToUse, sizeToUse});

            try (OrtSession.Result result = session.run(
                    Collections.singletonMap(inputName, input))) {

                // 解析输出（显著性图通常是单通道，尺寸与输入相同或不同）
                Object output = result.get(0).getValue();
                float[][] saliencyMap = null;

                if (output instanceof float[][][][]) {
                    // 形状: [1, 1, H, W] 或 [1, H, W, 1] 或 [1, H, W]
                    float[][][][] map4d = (float[][][][]) output;
                    // 尝试不同的维度顺序
                    if (map4d[0].length > 0 && map4d[0][0].length > 0 && map4d[0][0][0].length > 0) {
                        // [1, H, W, 1] 格式
                        int h = map4d[0].length;
                        int w = map4d[0][0].length;
                        saliencyMap = new float[h][w];
                        for (int i = 0; i < h; i++) {
                            for (int j = 0; j < w; j++) {
                                saliencyMap[i][j] = map4d[0][i][j][0];
                            }
                        }
                    } else if (map4d[0].length > 0 && map4d[0][0].length > 0) {
                        // [1, 1, H, W] 格式
                        int h = map4d[0][0].length;
                        int w = map4d[0][0][0].length;
                        saliencyMap = new float[h][w];
                        for (int i = 0; i < h; i++) {
                            for (int j = 0; j < w; j++) {
                                saliencyMap[i][j] = map4d[0][0][i][j];
                            }
                        }
                    }
                } else if (output instanceof float[][][]) {
                    // 形状: [1, H, W] 或 [H, W, 1]
                    float[][][] map3d = (float[][][]) output;
                    int h = map3d[0].length;
                    int w = map3d[0][0].length;
                    saliencyMap = new float[h][w];
                    for (int i = 0; i < h; i++) {
                        for (int j = 0; j < w; j++) {
                            saliencyMap[i][j] = map3d[0][i][j];
                        }
                    }
                } else if (output instanceof float[][]) {
                    // 形状: [H, W]
                    saliencyMap = (float[][]) output;
                }

                if (saliencyMap == null || saliencyMap.length == 0) {
                    log.debug("无法解析显著性检测模型输出，输出类型: {}", output != null ? output.getClass().getName() : "null");
                    if (output != null) {
                        log.debug("输出形状信息: {}", getOutputShapeInfo(output));
                    }
                    return null;
                }

                // 从显著性图中找到最显著的位置
                int sizeForMap = actualInputSize > 0 ? actualInputSize : inputSize;
                return findFocusFromSaliencyMap(saliencyMap, originalWidth, originalHeight, sizeForMap);

            } finally {
                input.close();
            }
        } catch (Exception e) {
            log.warn("ONNX显著性检测失败: {} - {}", imageFile.getName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从显著性图中找到焦点位置
     */
    private double[] findFocusFromSaliencyMap(float[][] saliencyMap, int originalWidth, int originalHeight, int modelInputSize) {
        int mapHeight = saliencyMap.length;
        int mapWidth = saliencyMap[0].length;

        // 找到显著性值最大的位置
        float maxSaliency = Float.MIN_VALUE;
        int maxX = mapWidth / 2;
        int maxY = mapHeight / 2;

        // 使用加权平均，避免单个噪声点影响
        double totalWeight = 0;
        double weightedX = 0;
        double weightedY = 0;

        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                float saliency = saliencyMap[y][x];
                
                // 找到最大值位置（作为备选）
                if (saliency > maxSaliency) {
                    maxSaliency = saliency;
                    maxX = x;
                    maxY = y;
                }

                // 计算加权平均（只考虑显著性值较高的区域）
                if (saliency > 0.5) { // 阈值可调
                    double weight = saliency * saliency; // 平方权重，突出高显著性区域
                    weightedX += x * weight;
                    weightedY += y * weight;
                    totalWeight += weight;
                }
            }
        }

        // 如果加权平均有效，使用它；否则使用最大值位置
        int focusX, focusY;
        if (totalWeight > 0) {
            focusX = (int) Math.round(weightedX / totalWeight);
            focusY = (int) Math.round(weightedY / totalWeight);
        } else {
            focusX = maxX;
            focusY = maxY;
        }

        // 将模型输出坐标转换为原始图片的百分比坐标
        // 注意：模型输入保持宽高比缩放，短边缩放到inputSize，然后填充到正方形
        double aspectRatio = (double) originalWidth / originalHeight;
        
        // 计算实际缩放后的尺寸（短边缩放到inputSize）
        int scaledWidth, scaledHeight;
        if (originalWidth > originalHeight) {
            scaledHeight = modelInputSize;
            scaledWidth = (int) (modelInputSize * aspectRatio);
        } else {
            scaledWidth = modelInputSize;
            scaledHeight = (int) (modelInputSize / aspectRatio);
        }
        
        // 计算在原始图片中的位置
        // 模型输出是正方形，需要减去填充偏移
        double offsetX = (modelInputSize - scaledWidth) / 2.0;
        double offsetY = (modelInputSize - scaledHeight) / 2.0;
        
        // 将模型输出坐标转换为缩放后图片的坐标
        double xInScaled = focusX - offsetX;
        double yInScaled = focusY - offsetY;
        
        // 转换为原始图片的坐标
        double xInOriginal = xInScaled * originalWidth / scaledWidth;
        double yInOriginal = yInScaled * originalHeight / scaledHeight;
        
        // 转换为百分比
        double focusXPercent = Math.max(0, Math.min(100, xInOriginal * 100.0 / originalWidth));
        double focusYPercent = Math.max(0, Math.min(100, yInOriginal * 100.0 / originalHeight));

        return new double[]{focusXPercent, focusYPercent};
    }

    /**
     * 预处理图像：保持宽高比，短边缩放到targetSize，然后填充到正方形
     */
    private BufferedImage resizeImage(BufferedImage img, int targetSize) {
        int w = img.getWidth();
        int h = img.getHeight();

        // 保持宽高比，短边缩放到targetSize
        double scale = (double) targetSize / Math.min(w, h);
        int newW = (int) (w * scale);
        int newH = (int) (h * scale);

        // 创建目标尺寸的图像（正方形）
        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, targetSize, targetSize);

        // 居中绘制缩放后的图像
        int offsetX = (targetSize - newW) / 2;
        int offsetY = (targetSize - newH) / 2;
        Image scaled = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        g.drawImage(scaled, offsetX, offsetY, null);
        g.dispose();

        return resized;
    }

    /**
     * 预处理图像：转换为CHW格式，归一化到[0,1]
     */
    private float[] preprocessImage(BufferedImage img, int size) {
        float[] tensor = new float[3 * size * size];
        int idx = 0;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                Color color = new Color(img.getRGB(x, y));
                // 归一化到[0,1]，注意顺序：RGB -> CHW
                tensor[idx] = color.getRed() / 255.0f;      // R channel
                tensor[idx + size * size] = color.getGreen() / 255.0f;  // G channel
                tensor[idx + 2 * size * size] = color.getBlue() / 255.0f; // B channel
                idx++;
            }
        }

        return tensor;
    }

    /**
     * 确保ONNX会话已加载
     */
    private void ensureSession() {
        if (session != null) return;

        try {
            File modelFile = new File(modelPath);
            if (!modelFile.exists()) {
                log.debug("显著性检测模型文件不存在: {}，将回退到混合策略", modelPath);
                return;
            }

            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            session = env.createSession(modelPath, opts);

            // 获取输入名称和输入形状信息
            inputName = session.getInputNames().iterator().next();
            
            // 自动检测模型的输入尺寸
            NodeInfo inputInfo = session.getInputInfo().get(inputName);
            actualInputSize = inputSize; // 默认使用配置的尺寸
            
            if (inputInfo != null && inputInfo.getInfo() instanceof TensorInfo) {
                TensorInfo tensorInfo = (TensorInfo) inputInfo.getInfo();
                long[] shape = tensorInfo.getShape();
                if (shape.length >= 4) {
                    // 假设输入格式为 [batch, channels, height, width]
                    long detectedHeight = shape[shape.length - 2];
                    long detectedWidth = shape[shape.length - 1];
                    if (detectedHeight > 0 && detectedWidth > 0) {
                        // 使用检测到的尺寸（通常是正方形）
                        actualInputSize = (int) detectedHeight;
                        if (detectedHeight != detectedWidth) {
                            log.warn("模型输入不是正方形: {}x{}，使用高度 {}", detectedHeight, detectedWidth, detectedHeight);
                        }
                        if (actualInputSize != inputSize) {
                            log.info("检测到模型输入尺寸: {}x{}，配置中的input-size ({})将被覆盖", 
                                    actualInputSize, actualInputSize, inputSize);
                        }
                    }
                }
            }
            
            log.info("显著性检测模型已加载: {}，输入名称: {}，使用输入尺寸: {}x{}", 
                    modelPath, inputName, actualInputSize, actualInputSize);
        } catch (Exception e) {
            log.warn("加载显著性检测模型失败: {}，将回退到混合策略", modelPath, e);
            session = null;
        }
    }

    /**
     * 获取输出形状信息的辅助方法
     */
    private String getOutputShapeInfo(Object output) {
        if (output == null) return "null";
        try {
            if (output instanceof float[][][][]) {
                float[][][][] arr = (float[][][][]) output;
                return String.format("[%d, %d, %d, %d]", 
                    arr.length, 
                    arr.length > 0 ? arr[0].length : 0,
                    arr.length > 0 && arr[0].length > 0 ? arr[0][0].length : 0,
                    arr.length > 0 && arr[0].length > 0 && arr[0][0].length > 0 ? arr[0][0][0].length : 0);
            } else if (output instanceof float[][][]) {
                float[][][] arr = (float[][][]) output;
                return String.format("[%d, %d, %d]", 
                    arr.length,
                    arr.length > 0 ? arr[0].length : 0,
                    arr.length > 0 && arr[0].length > 0 ? arr[0][0].length : 0);
            } else if (output instanceof float[][]) {
                float[][] arr = (float[][]) output;
                return String.format("[%d, %d]", 
                    arr.length,
                    arr.length > 0 ? arr[0].length : 0);
            } else if (output instanceof float[]) {
                float[] arr = (float[]) output;
                return String.format("[%d]", arr.length);
            }
            return output.getClass().getSimpleName();
        } catch (Exception e) {
            return "无法获取形状信息: " + e.getMessage();
        }
    }

    @Override
    public void close() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                log.warn("关闭显著性检测会话失败", e);
            }
            session = null;
        }
    }
}

