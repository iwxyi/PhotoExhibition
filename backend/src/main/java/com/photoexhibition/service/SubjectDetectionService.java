package com.photoexhibition.service;

import com.photoexhibition.entity.Photo;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

@Slf4j
@Service
public class SubjectDetectionService {

    /**
     * 检测图片的主体位置（焦点区域）
     * 返回焦点位置的百分比坐标 (focusX, focusY)，范围 0-100
     * 默认返回 (50, 50) 表示中心位置
     */
    public void detectSubject(File imageFile, Photo photo) {
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                // 默认居中
                photo.setFocusX(50.0);
                photo.setFocusY(50.0);
                return;
            }

            // 缩放图片以加快处理速度
            BufferedImage scaledImage = Scalr.resize(image, Scalr.Method.QUALITY, 400);
            int width = scaledImage.getWidth();
            int height = scaledImage.getHeight();

            // 将图片分成网格，计算每个区域的"兴趣度"
            int gridSize = 10; // 10x10网格
            int cellWidth = width / gridSize;
            int cellHeight = height / gridSize;
            
            double[][] interestMap = new double[gridSize][gridSize];
            double maxInterest = 0;
            int bestX = gridSize / 2;
            int bestY = gridSize / 2;

            // 计算每个网格区域的兴趣度
            for (int gy = 0; gy < gridSize; gy++) {
                for (int gx = 0; gx < gridSize; gx++) {
                    double interest = 0;
                    int sampleCount = 0;

                    // 采样网格内的像素
                    for (int y = gy * cellHeight; y < Math.min((gy + 1) * cellHeight, height); y += 5) {
                        for (int x = gx * cellWidth; x < Math.min((gx + 1) * cellWidth, width); x += 5) {
                            if (x < width && y < height) {
                                Color color = new Color(scaledImage.getRGB(x, y));
                                
                                // 计算对比度（与周围像素的差异）
                                double contrast = calculateLocalContrast(scaledImage, x, y, width, height);
                                
                                // 计算边缘强度（使用 Sobel 算子简化版）
                                double edgeStrength = calculateEdgeStrength(scaledImage, x, y, width, height);
                                
                                // 计算亮度（主体通常不是最亮或最暗的区域）
                                double brightness = (color.getRed() + color.getGreen() + color.getBlue()) / 3.0;
                                double brightnessScore = 1.0 - Math.abs(brightness - 128) / 128.0; // 偏好中等亮度
                                
                                // 综合兴趣度
                                interest += contrast * 0.4 + edgeStrength * 0.4 + brightnessScore * 0.2;
                                sampleCount++;
                            }
                        }
                    }

                    if (sampleCount > 0) {
                        interestMap[gy][gx] = interest / sampleCount;
                        
                        // 应用三分法则权重（主体通常在图片的上1/3或下1/3区域）
                        double ruleOfThirdsWeight = calculateRuleOfThirdsWeight(gx, gy, gridSize);
                        interestMap[gy][gx] *= ruleOfThirdsWeight;
                        
                        if (interestMap[gy][gx] > maxInterest) {
                            maxInterest = interestMap[gy][gx];
                            bestX = gx;
                            bestY = gy;
                        }
                    }
                }
            }

            // 转换为百分比坐标
            double focusX = (bestX + 0.5) * 100.0 / gridSize;
            double focusY = (bestY + 0.5) * 100.0 / gridSize;

            photo.setFocusX(focusX);
            photo.setFocusY(focusY);
            
            log.debug("检测到主体位置: ({}, {}) for {}", focusX, focusY, imageFile.getName());

        } catch (Exception e) {
            log.warn("检测主体位置失败: {}", imageFile.getName(), e);
            // 默认居中
            photo.setFocusX(50.0);
            photo.setFocusY(50.0);
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

