package com.photoexhibition.service;

import ai.onnxruntime.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于ONNX模型的专业人脸检测服务
 * 支持RetinaFace、MTCNN等专业检测模型
 */
@Slf4j
@Service
public class FaceDetectionService implements AutoCloseable {

    @Value("${face.detection.model-path:./models/face_detection.onnx}")
    private String detectionModelPath;

    @Value("${face.detection.enabled:false}")
    private boolean enabled;

    @Value("${face.detection.input-size:640}")
    private int inputSize;

    @Value("${face.detection.confidence-threshold:0.5}")
    private double confidenceThreshold;

    @Value("${face.detection.nms-threshold:0.4}")
    private double nmsThreshold;

    private OrtEnvironment env;
    private OrtSession detectionSession;

    /**
     * 使用ONNX模型检测人脸
     * 如果模型未配置或加载失败，返回空列表（会回退到简单检测方法）
     */
    public List<DetectedFace> detectFaces(File imageFile) {
        if (!enabled) {
            return new ArrayList<>();
        }

        try {
            ensureSession();
            if (detectionSession == null) {
                return new ArrayList<>();
            }

            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
                return new ArrayList<>();
            }

            // 预处理图像
            int size = Math.max(320, Math.min(2048, inputSize)); // 限定范围，防止过大
            BufferedImage resized = resizeImage(img, size);
            float[] inputTensor = preprocessImage(resized, size);

            // 运行推理
            OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputTensor),
                new long[]{1, 3, size, size});
            
            try (OrtSession.Result result = detectionSession.run(
                Collections.singletonMap(detectionSession.getInputNames().iterator().next(), input))) {
                
                // 解析输出（RetinaFace输出格式：boxes, scores, landmarks）
                Object boxesObj = result.get(0).getValue();
                Object scoresObj = result.get(1).getValue();
                
                if (boxesObj instanceof float[][][] && scoresObj instanceof float[][]) {
                    float[][][] boxes = (float[][][]) boxesObj;
                    float[][] scores = (float[][]) scoresObj;
                    
                    List<DetectedFace> faces = parseDetections(boxes, scores, img.getWidth(), img.getHeight(), size);
                    return applyNMS(faces);
                }
            } finally {
                input.close();
            }
        } catch (Exception e) {
            log.warn("ONNX人脸检测失败，将回退到简单检测方法: {}", imageFile.getName(), e);
        }
        
        return new ArrayList<>();
    }

    private void ensureSession() {
        if (detectionSession != null) return;
        
        try {
            java.io.File modelFile = new java.io.File(detectionModelPath);
            if (!modelFile.exists()) {
                log.warn("人脸检测模型文件不存在: {}，将使用简单检测方法", detectionModelPath);
                return;
            }
            
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            // 可以根据需要设置线程数等选项
            detectionSession = env.createSession(detectionModelPath, opts);
            log.info("人脸检测模型已加载: {}", detectionModelPath);
        } catch (Exception e) {
            log.warn("加载人脸检测模型失败: {}，将使用简单检测方法", detectionModelPath, e);
            detectionSession = null;
        }
    }

    private BufferedImage resizeImage(BufferedImage img, int targetSize) {
        int w = img.getWidth();
        int h = img.getHeight();
        
        // 保持宽高比，短边缩放到targetSize
        double scale = (double) targetSize / Math.max(w, h);
        int newW = (int) (w * scale);
        int newH = (int) (h * scale);
        
        java.awt.Image scaled = img.getScaledInstance(newW, newH, java.awt.Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = resized.createGraphics();
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, targetSize, targetSize);
        g.drawImage(scaled, (targetSize - newW) / 2, (targetSize - newH) / 2, null);
        g.dispose();
        
        return resized;
    }

    private float[] preprocessImage(BufferedImage img, int size) {
        float[] tensor = new float[3 * size * size];
        int idx = 0;
        
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                
                // 归一化到[0, 1]或[-1, 1]，根据模型要求调整
                tensor[idx] = (r - 127.5f) / 128f;
                tensor[idx + size * size] = (g - 127.5f) / 128f;
                tensor[idx + 2 * size * size] = (b - 127.5f) / 128f;
                idx++;
            }
        }
        
        return tensor;
    }

    private List<DetectedFace> parseDetections(float[][][] boxes, float[][] scores, 
                                               int originalWidth, int originalHeight, int inputSize) {
        List<DetectedFace> faces = new ArrayList<>();
        
        if (boxes.length == 0 || scores.length == 0) {
            return faces;
        }
        
        // 计算缩放比例（模型输入尺寸到原始图片尺寸）
        double scaleX = (double) originalWidth / inputSize;
        double scaleY = (double) originalHeight / inputSize;
        
        // 解析检测结果（RetinaFace格式：每个检测框有4个坐标）
        for (int i = 0; i < scores[0].length; i++) {
            float score = scores[0][i];
            if (score < confidenceThreshold) {
                continue;
            }
            
            if (boxes[0][i].length >= 4) {
                // 坐标格式可能是 [x1, y1, x2, y2] 或 [cx, cy, w, h]
                float x1 = boxes[0][i][0];
                float y1 = boxes[0][i][1];
                float x2 = boxes[0][i][2];
                float y2 = boxes[0][i][3];
                
                // 转换为归一化坐标
                double x = (x1 / inputSize) * scaleX / originalWidth;
                double y = (y1 / inputSize) * scaleY / originalHeight;
                double w = ((x2 - x1) / inputSize) * scaleX / originalWidth;
                double h = ((y2 - y1) / inputSize) * scaleY / originalHeight;
                
                // 确保坐标在有效范围内
                x = Math.max(0.0, Math.min(1.0, x));
                y = Math.max(0.0, Math.min(1.0, y));
                w = Math.max(0.01, Math.min(1.0 - x, w));
                h = Math.max(0.01, Math.min(1.0 - y, h));
                
                faces.add(new DetectedFace(x, y, w, h, score));
            }
        }
        
        return faces;
    }

    private List<DetectedFace> applyNMS(List<DetectedFace> faces) {
        if (faces.size() <= 1) {
            return faces;
        }
        
        // 简单的NMS实现
        List<DetectedFace> result = new ArrayList<>();
        boolean[] suppressed = new boolean[faces.size()];
        
        for (int i = 0; i < faces.size(); i++) {
            if (suppressed[i]) continue;
            
            DetectedFace face1 = faces.get(i);
            result.add(face1);
            
            for (int j = i + 1; j < faces.size(); j++) {
                if (suppressed[j]) continue;
                
                DetectedFace face2 = faces.get(j);
                double iou = calculateIOU(face1, face2);
                if (iou > nmsThreshold) {
                    suppressed[j] = true;
                }
            }
        }
        
        return result;
    }

    private double calculateIOU(DetectedFace f1, DetectedFace f2) {
        double x1 = Math.max(f1.getX(), f2.getX());
        double y1 = Math.max(f1.getY(), f2.getY());
        double x2 = Math.min(f1.getX() + f1.getWidth(), f2.getX() + f2.getWidth());
        double y2 = Math.min(f1.getY() + f1.getHeight(), f2.getY() + f2.getHeight());
        
        if (x2 <= x1 || y2 <= y1) {
            return 0.0;
        }
        
        double intersection = (x2 - x1) * (y2 - y1);
        double area1 = f1.getWidth() * f1.getHeight();
        double area2 = f2.getWidth() * f2.getHeight();
        double union = area1 + area2 - intersection;
        
        return union > 0 ? intersection / union : 0.0;
    }

    @Override
    public void close() {
        try {
            if (detectionSession != null) detectionSession.close();
            if (env != null) env.close();
        } catch (Exception ignored) {
        }
    }

    public static class DetectedFace {
        private double x;
        private double y;
        private double width;
        private double height;
        private double confidence;

        public DetectedFace(double x, double y, double width, double height, double confidence) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.confidence = confidence;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public double getConfidence() { return confidence; }
    }
}

