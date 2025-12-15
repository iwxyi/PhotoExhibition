package com.photoexhibition.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

@Slf4j
@Service
public class FaceRecognitionService {

    private FaceDetectionService faceDetectionService;

    @org.springframework.beans.factory.annotation.Value("${face.detection.allow-simple-fallback:true}")
    private boolean allowSimpleFallback;

    // 使用setter注入避免循环依赖
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setFaceDetectionService(FaceDetectionService faceDetectionService) {
        this.faceDetectionService = faceDetectionService;
    }

    /**
     * 人脸检测：优先使用ONNX专业模型，失败时回退到简单检测方法
     */
    public List<DetectedFace> detectFaces(File imageFile) {
        // 优先尝试使用专业检测模型
        if (faceDetectionService != null) {
            try {
                List<FaceDetectionService.DetectedFace> onnxFaces = faceDetectionService.detectFaces(imageFile);
                if (!onnxFaces.isEmpty()) {
                    // 转换为旧格式
                    return onnxFaces.stream()
                        .map(f -> new DetectedFace(f.getX(), f.getY(), f.getWidth(), f.getHeight(), f.getConfidence()))
                        .collect(java.util.stream.Collectors.toList());
                }
                log.debug("专业检测模型未返回结果，简单检测={}", allowSimpleFallback);
            } catch (Exception e) {
                log.debug("专业检测模型失败，回退到简单检测: {}", e.getMessage());
            }
        } else {
            log.debug("未注入专业检测服务，简单检测={}", allowSimpleFallback);
        }
        
        // 回退到简单检测方法（可配置禁用以避免低精度框）
        if (allowSimpleFallback) {
            return detectFacesSimple(imageFile);
        }

        // 禁用回退时返回空结果，由上层决定是否处理
        return java.util.Collections.emptyList();
    }

    /**
     * 基于肤色聚类的轻量级人脸检测（无需额外依赖）
     * 仅用于生成候选人脸区域，避免依赖重量级CV库
     * 作为专业检测模型的回退方案
     */
    private List<DetectedFace> detectFacesSimple(File imageFile) {
        List<DetectedFace> results = new ArrayList<>();
        try {
            BufferedImage original = ImageIO.read(imageFile);
            if (original == null) {
                return results;
            }

            int originalWidth = original.getWidth();
            int originalHeight = original.getHeight();

            // 控制处理尺寸，保障性能
            int targetMax = 600;
            BufferedImage image = original;
            if (Math.max(originalWidth, originalHeight) > targetMax) {
                image = Scalr.resize(original, Scalr.Method.QUALITY, targetMax);
            }

            int w = image.getWidth();
            int h = image.getHeight();
            boolean[][] skin = new boolean[h][w];

            int step = Math.max(1, Math.max(w, h) / 400); // 自适应采样步长
            for (int y = 0; y < h; y += step) {
                for (int x = 0; x < w; x += step) {
                    Color c = new Color(image.getRGB(x, y));
                    if (isSkinPixel(c)) {
                        skin[y][x] = true;
                    }
                }
            }

            // 连通域标记
            int[][] label = new int[h][w];
            int currentLabel = 0;
            List<ComponentBox> components = new ArrayList<>();
            int minArea = Math.max(40, (w * h) / 500); // 过滤过小区域

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (!skin[y][x] || label[y][x] != 0) continue;
                    currentLabel++;
                    ComponentBox box = floodFill(x, y, skin, label, currentLabel, step);
                    if (box.count >= minArea) {
                        components.add(box);
                    }
                }
            }

            // 转换为归一化坐标
            double scaleX = (double) originalWidth / w;
            double scaleY = (double) originalHeight / h;

            components.sort(Comparator.comparingInt(c -> -c.count));
            for (ComponentBox c : components) {
                double x = c.minX * scaleX / originalWidth;
                double y = c.minY * scaleY / originalHeight;
                double width = (c.maxX - c.minX + step) * scaleX / originalWidth;
                double height = (c.maxY - c.minY + step) * scaleY / originalHeight;
                double confidence = Math.min(1.0, (double) c.count / (w * h) * 8);

                // 基于人脸常见比例做过滤，放宽以减少漏检
                double ratio = height > 0 ? width / height : 1.0;
                if (ratio < 0.5 || ratio > 1.6) continue; // 放宽到0.5-1.6

                // 面积检查：确保区域足够大（至少占图片的0.5%）
                double area = width * height;
                if (area < 0.005) continue;

                results.add(new DetectedFace(x, y, width, height, confidence));
            }

            if (results.isEmpty()) {
                log.debug("未检测到人脸: {}", imageFile.getName());
            } else {
                log.debug("检测到 {} 个候选人脸: {}", results.size(), imageFile.getName());
            }
        } catch (Exception e) {
            log.warn("人脸检测失败: {}", imageFile.getName(), e);
        }
        return results;
    }

    private ComponentBox floodFill(int startX, int startY, boolean[][] skin, int[][] label, int currentLabel, int step) {
        int h = skin.length;
        int w = skin[0].length;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startX, startY});
        label[startY][startX] = currentLabel;

        int minX = startX, minY = startY, maxX = startX, maxY = startY, count = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int x = p[0], y = p[1];
            count++;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);

            for (int[] d : dirs) {
                int nx = x + d[0] * step;
                int ny = y + d[1] * step;
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && skin[ny][nx] && label[ny][nx] == 0) {
                    label[ny][nx] = currentLabel;
                    stack.push(new int[]{nx, ny});
                }
            }
        }
        return new ComponentBox(minX, minY, maxX, maxY, count);
    }

    private boolean isSkinPixel(Color c) {
        // YCbCr肤色简单规则
        int r = c.getRed();
        int g = c.getGreen();
        int b = c.getBlue();

        double y = 0.299 * r + 0.587 * g + 0.114 * b;
        double cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b;
        double cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;

        boolean yOk = y > 50 && y < 250;
        boolean cbOk = cb >= 77 && cb <= 127;
        boolean crOk = cr >= 133 && cr <= 173;
        boolean rgDiff = Math.abs(r - g) > 15 && r > g && r > b;
        return yOk && cbOk && crOk && rgDiff;
    }

    @Data
    @AllArgsConstructor
    public static class DetectedFace {
        private double x;
        private double y;
        private double width;
        private double height;
        private double confidence;
    }

    @Data
    @AllArgsConstructor
    private static class ComponentBox {
        private int minX;
        private int minY;
        private int maxX;
        private int maxY;
        private int count;
    }
}

