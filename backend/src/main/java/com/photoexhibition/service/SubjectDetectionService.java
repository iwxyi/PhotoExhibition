package com.photoexhibition.service;

import com.photoexhibition.entity.Face;
import com.photoexhibition.entity.Photo;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

@Slf4j
@Service
public class SubjectDetectionService {

    @Autowired(required = false)
    @SuppressWarnings("unused")
    private FaceRecognitionService faceRecognitionService; // 保留用于未来扩展：如果未传入faces，可以自动检测

    @Autowired(required = false)
    private SaliencyDetectionService saliencyDetectionService;

    @Value("${smart-tag.subject-detection.strategy:hybrid}")
    private String detectionStrategy; // hybrid, face-first, exif-first, visual-only

    @Value("${smart-tag.subject-detection.face-weight:2.0}")
    private double faceWeight; // 人脸权重倍数

    /**
     * 检测图片的主体位置（焦点区域）
     * 使用多策略融合：1.人脸优先 2.EXIF对焦点 3.视觉显著性 4.回退算法
     * 返回焦点位置的百分比坐标 (focusX, focusY)，范围 0-100
     * 默认返回 (50, 50) 表示中心位置
     */
    public void detectSubject(File imageFile, Photo photo) {
        detectSubject(imageFile, photo, null);
    }

    /**
     * 检测图片的主体位置（可传入已检测的人脸列表）
     * 多策略融合检测（按优先级）：
     * 0. ONNX显著性检测：如果启用，优先使用深度学习模型（最准确）
     * 1. 人脸优先：如果检测到人脸，使用人脸中心位置（加权）
     * 2. EXIF对焦点：如果EXIF中有对焦点信息，使用它
     * 3. 视觉显著性：使用对比度、边缘、亮度等特征（混合策略）
     * 4. 回退算法：如果以上都失败，使用中心位置
     */
    public void detectSubject(File imageFile, Photo photo, List<Face> detectedFaces) {
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                photo.setFocusX(50.0);
                photo.setFocusY(50.0);
                return;
            }

            int originalWidth = image.getWidth();
            int originalHeight = image.getHeight();

            // 策略0：ONNX显著性检测（如果启用，优先使用）
            if (saliencyDetectionService != null) {
                double[] saliencyFocus = saliencyDetectionService.detectSaliency(imageFile, originalWidth, originalHeight);
                if (saliencyFocus != null) {
                    photo.setFocusX(saliencyFocus[0]);
                    photo.setFocusY(saliencyFocus[1]);
                    log.debug("使用ONNX显著性检测: ({}, {}) for {}", saliencyFocus[0], saliencyFocus[1], imageFile.getName());
                    return;
                }
            }

            // 策略1：人脸优先检测
            if (detectionStrategy.equals("face-first") || detectionStrategy.equals("hybrid")) {
                double[] faceFocus = detectFromFaces(photo, detectedFaces, originalWidth, originalHeight);
                if (faceFocus != null) {
                    photo.setFocusX(faceFocus[0]);
                    photo.setFocusY(faceFocus[1]);
                    log.debug("使用人脸检测结果: ({}, {}) for {}", faceFocus[0], faceFocus[1], imageFile.getName());
                    return;
                }
            }

            // 策略2：EXIF对焦点（如果可用）
            if (detectionStrategy.equals("exif-first") || detectionStrategy.equals("hybrid")) {
                double[] exifFocus = detectFromExif(photo, originalWidth, originalHeight);
                if (exifFocus != null) {
                    photo.setFocusX(exifFocus[0]);
                    photo.setFocusY(exifFocus[1]);
                    log.debug("使用EXIF对焦点: ({}, {}) for {}", exifFocus[0], exifFocus[1], imageFile.getName());
                    return;
                }
            }

            // 策略3：视觉显著性检测（融合人脸权重）- 混合策略的回退方案
            double[] visualFocus = detectFromVisualFeatures(image, photo, detectedFaces, originalWidth, originalHeight);
            if (visualFocus != null) {
                photo.setFocusX(visualFocus[0]);
                photo.setFocusY(visualFocus[1]);
                log.debug("使用视觉显著性检测: ({}, {}) for {}", visualFocus[0], visualFocus[1], imageFile.getName());
                return;
            }

            // 回退：默认居中
            photo.setFocusX(50.0);
            photo.setFocusY(50.0);
            log.debug("使用默认中心位置 for {}", imageFile.getName());

        } catch (Exception e) {
            log.warn("检测主体位置失败: {}", imageFile.getName(), e);
            photo.setFocusX(50.0);
            photo.setFocusY(50.0);
        }
    }

    /**
     * 策略1：从人脸检测结果获取焦点位置
     */
    private double[] detectFromFaces(Photo photo, List<Face> detectedFaces, int width, int height) {
        // 如果没有传入人脸列表，尝试从photo中获取
        List<Face> faces = detectedFaces;
        if (faces == null && photo.getFaces() != null) {
            faces = photo.getFaces();
        }

        if (faces == null || faces.isEmpty()) {
            return null;
        }

        // 计算所有人脸的中心位置（加权平均，大脸权重更高）
        double totalWeight = 0;
        double weightedX = 0;
        double weightedY = 0;

        for (Face face : faces) {
            if (face.getX() == null || face.getY() == null || 
                face.getWidth() == null || face.getHeight() == null) {
                continue;
            }

            // 计算人脸中心位置（百分比）
            double faceCenterX = (face.getX() + face.getWidth() / 2.0) * 100.0;
            double faceCenterY = (face.getY() + face.getHeight() / 2.0) * 100.0;

            // 权重：人脸面积（大脸更重要）
            double weight = face.getWidth() * face.getHeight() * faceWeight;
            weightedX += faceCenterX * weight;
            weightedY += faceCenterY * weight;
            totalWeight += weight;
        }

        if (totalWeight > 0) {
            return new double[]{
                Math.max(0, Math.min(100, weightedX / totalWeight)),
                Math.max(0, Math.min(100, weightedY / totalWeight))
            };
        }

        return null;
    }

    /**
     * 策略2：从EXIF数据获取对焦点（如果相机支持）
     * 注意：大多数相机的EXIF不包含对焦点信息，但可以尝试解析
     */
    private double[] detectFromExif(Photo photo, int width, int height) {
        // 目前大多数相机的EXIF不包含对焦点坐标
        // 但可以尝试从exifData JSON中解析（如果相机支持）
        // 这里预留接口，未来可以扩展
        return null;
    }

    /**
     * 策略3：视觉显著性检测（融合人脸权重）
     */
    private double[] detectFromVisualFeatures(BufferedImage image, Photo photo, 
                                             List<Face> detectedFaces, int originalWidth, int originalHeight) {
            // 缩放图片以加快处理速度
            BufferedImage scaledImage = Scalr.resize(image, Scalr.Method.QUALITY, 400);
            int width = scaledImage.getWidth();
            int height = scaledImage.getHeight();

            // 将图片分成网格，计算每个区域的"兴趣度"
        int gridSize = 10;
            int cellWidth = width / gridSize;
            int cellHeight = height / gridSize;
            
            double[][] interestMap = new double[gridSize][gridSize];
            double maxInterest = 0;
            int bestX = gridSize / 2;
            int bestY = gridSize / 2;

        // 计算每个网格区域的视觉兴趣度
            for (int gy = 0; gy < gridSize; gy++) {
                for (int gx = 0; gx < gridSize; gx++) {
                    double interest = 0;
                    int sampleCount = 0;

                    // 采样网格内的像素
                    for (int y = gy * cellHeight; y < Math.min((gy + 1) * cellHeight, height); y += 5) {
                        for (int x = gx * cellWidth; x < Math.min((gx + 1) * cellWidth, width); x += 5) {
                            if (x < width && y < height) {
                                Color color = new Color(scaledImage.getRGB(x, y));
                                
                            // 计算对比度
                                double contrast = calculateLocalContrast(scaledImage, x, y, width, height);
                                
                            // 计算边缘强度
                                double edgeStrength = calculateEdgeStrength(scaledImage, x, y, width, height);
                                
                                // 计算亮度（主体通常不是最亮或最暗的区域）
                                double brightness = (color.getRed() + color.getGreen() + color.getBlue()) / 3.0;
                            double brightnessScore = 1.0 - Math.abs(brightness - 128) / 128.0;
                                
                                // 综合兴趣度
                                interest += contrast * 0.4 + edgeStrength * 0.4 + brightnessScore * 0.2;
                                sampleCount++;
                            }
                        }
                    }

                    if (sampleCount > 0) {
                        interestMap[gy][gx] = interest / sampleCount;
                        
                    // 应用三分法则权重
                        double ruleOfThirdsWeight = calculateRuleOfThirdsWeight(gx, gy, gridSize);
                        interestMap[gy][gx] *= ruleOfThirdsWeight;
                }
            }
        }

        // 如果检测到人脸，增强人脸区域的权重
        if (detectedFaces != null && !detectedFaces.isEmpty()) {
            enhanceFaceRegions(interestMap, detectedFaces, gridSize, width, height, originalWidth, originalHeight);
        }

        // 找到兴趣度最高的区域
        for (int gy = 0; gy < gridSize; gy++) {
            for (int gx = 0; gx < gridSize; gx++) {
                        if (interestMap[gy][gx] > maxInterest) {
                            maxInterest = interestMap[gy][gx];
                            bestX = gx;
                            bestY = gy;
                    }
                }
            }

            // 转换为百分比坐标
            double focusX = (bestX + 0.5) * 100.0 / gridSize;
            double focusY = (bestY + 0.5) * 100.0 / gridSize;

        return new double[]{focusX, focusY};
    }

    /**
     * 增强人脸区域的权重
     */
    private void enhanceFaceRegions(double[][] interestMap, List<Face> faces, 
                                   int gridSize, int scaledWidth, int scaledHeight,
                                   int originalWidth, int originalHeight) {
        double scaleX = (double) scaledWidth / originalWidth;
        double scaleY = (double) scaledHeight / originalHeight;

        for (Face face : faces) {
            if (face.getX() == null || face.getY() == null || 
                face.getWidth() == null || face.getHeight() == null) {
                continue;
            }

            // 将原始坐标转换为缩放后的坐标
            int faceX = (int) (face.getX() * originalWidth * scaleX);
            int faceY = (int) (face.getY() * originalHeight * scaleY);
            int faceW = (int) (face.getWidth() * originalWidth * scaleX);
            int faceH = (int) (face.getHeight() * originalHeight * scaleY);

            // 计算人脸中心所在的网格
            int centerX = faceX + faceW / 2;
            int centerY = faceY + faceH / 2;
            int gridX = Math.min(gridSize - 1, centerX * gridSize / scaledWidth);
            int gridY = Math.min(gridSize - 1, centerY * gridSize / scaledHeight);

            // 增强人脸中心及其周围区域的权重
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int gx = gridX + dx;
                    int gy = gridY + dy;
                    if (gx >= 0 && gx < gridSize && gy >= 0 && gy < gridSize) {
                        // 距离人脸中心越近，权重越高
                        double distance = Math.sqrt(dx * dx + dy * dy);
                        double weight = faceWeight * (1.0 / (1.0 + distance));
                        interestMap[gy][gx] *= (1.0 + weight);
                    }
                }
            }
        }
    }

    /**
     * 计算局部对比度
     */
    private double calculateLocalContrast(BufferedImage image, int x, int y, int width, int height) {
        Color center = new Color(image.getRGB(x, y));
        double totalDiff = 0;
        int count = 0;

        // 检查周围8个像素
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    Color neighbor = new Color(image.getRGB(nx, ny));
                    double diff = Math.abs(center.getRed() - neighbor.getRed()) +
                                 Math.abs(center.getGreen() - neighbor.getGreen()) +
                                 Math.abs(center.getBlue() - neighbor.getBlue());
                    totalDiff += diff;
                    count++;
                }
            }
        }

        return count > 0 ? totalDiff / count : 0;
    }

    /**
     * 计算边缘强度（简化版 Sobel）
     */
    private double calculateEdgeStrength(BufferedImage image, int x, int y, int width, int height) {
        if (x < 1 || x >= width - 1 || y < 1 || y >= height - 1) {
            return 0;
        }

        // 简化的 Sobel 算子
        int gx = 0, gy = 0;
        int[][] sobelX = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
        int[][] sobelY = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};

        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                Color c = new Color(image.getRGB(x + j, y + i));
                int gray = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
                gx += gray * sobelX[i + 1][j + 1];
                gy += gray * sobelY[i + 1][j + 1];
            }
        }

        return Math.sqrt(gx * gx + gy * gy) / 255.0;
    }

    /**
     * 计算三分法则权重
     * 主体通常在图片的上1/3或下1/3区域，以及左右1/3位置
     */
    private double calculateRuleOfThirdsWeight(int gx, int gy, int gridSize) {
        double weight = 1.0;
        
        // 检查是否在三分法则的交叉点上
        double third = gridSize / 3.0;
        boolean onVerticalThird = Math.abs(gx - third) < 1 || Math.abs(gx - 2 * third) < 1;
        boolean onHorizontalThird = Math.abs(gy - third) < 1 || Math.abs(gy - 2 * third) < 1;
        
        if (onVerticalThird || onHorizontalThird) {
            weight = 1.2; // 增加权重
        }
        
        // 中心区域稍微降低权重（避免总是选择中心）
        double centerX = gridSize / 2.0;
        double centerY = gridSize / 2.0;
        double distanceFromCenter = Math.sqrt(Math.pow(gx - centerX, 2) + Math.pow(gy - centerY, 2));
        if (distanceFromCenter < gridSize * 0.2) {
            weight *= 0.9;
        }
        
        return weight;
    }
}

