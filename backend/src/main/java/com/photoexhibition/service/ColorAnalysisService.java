package com.photoexhibition.service;

import com.photoexhibition.entity.Photo;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ColorAnalysisService {

    /**
     * 颜色分类枚举
     */
    public enum ColorCategory {
        RED("红色", "#FF0000"),
        ORANGE("橙色", "#FFA500"),
        YELLOW("黄色", "#FFFF00"),
        GREEN("绿色", "#008000"),
        BLUE("蓝色", "#0000FF"),
        PURPLE("紫色", "#800080"),
        PINK("粉色", "#FFC0CB"),
        BROWN("棕色", "#A52A2A"),
        GRAY("灰色", "#808080"),
        BLACK("黑色", "#000000"),
        WHITE("白色", "#FFFFFF");

        private final String displayName;
        private final String hexColor;

        ColorCategory(String displayName, String hexColor) {
            this.displayName = displayName;
            this.hexColor = hexColor;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getHexColor() {
            return hexColor;
        }
    }

    /**
     * 分析图片的主色调和调色板
     */
    public void analyzeColor(File imageFile, Photo photo) {
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) return;

            // 缩放图片以加快处理速度
            BufferedImage scaledImage = Scalr.resize(image, Scalr.Method.QUALITY, 200);

            // 使用改进的调色板提取算法
            List<Color> paletteColors = extractColorPalette(scaledImage, 6);

            if (!paletteColors.isEmpty()) {
                // 主色调是第一个颜色
                Color dominantColor = paletteColors.get(0);
                String hexColor = String.format("#%02x%02x%02x",
                    dominantColor.getRed(),
                    dominantColor.getGreen(),
                    dominantColor.getBlue());
                photo.setDominantColor(hexColor);
                photo.setColorCategory(classifyColor(hexColor));

                // 调色板（包含主色调和其余颜色）
                List<String> palette = paletteColors.stream()
                    .map(color -> String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()))
                    .collect(Collectors.toList());

                photo.setColorPalette(String.format("[\"%s\"]", String.join("\",\"", palette)));
            }

        } catch (IOException e) {
            log.warn("色彩分析失败: {}", imageFile.getName(), e);
        }
    }

    /**
     * 提取图片的调色板（改进算法）
     */
    public List<Color> extractColorPalette(BufferedImage image, int numColors) {
        List<Color> samples = new ArrayList<>();

        int width = image.getWidth();
        int height = image.getHeight();

        // 自适应采样：图片越大，采样步长越大
        int sampleStep = Math.max(1, Math.min(width, height) / 100);

        // 采样像素，考虑颜色显著性
        for (int x = 0; x < width; x += sampleStep) {
            for (int y = 0; y < height; y += sampleStep) {
                Color color = new Color(image.getRGB(x, y));

                // 计算颜色的显著性权重
                double significance = calculateColorSignificance(color, x, y, width, height);

                // 根据显著性重复添加样本（显著性高的颜色被采样多次）
                int sampleCount = Math.max(1, (int)(significance * 3));
                for (int i = 0; i < sampleCount; i++) {
                    samples.add(color);
                }
            }
        }

        if (samples.isEmpty()) {
            return Collections.emptyList();
        }

        // 使用K-means聚类算法提取主要颜色
        return kMeansClustering(samples, numColors);
    }

    /**
     * 计算颜色的显著性权重
     */
    private double calculateColorSignificance(Color color, int x, int y, int width, int height) {
        // 转换为HSB色彩空间
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float saturation = hsb[1];
        float brightness = hsb[2];

        // 中心区域权重更高
        double centerWeight = 1.0;
        int centerX = width / 2;
        int centerY = height / 2;
        double distanceFromCenter = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));
        double maxDistance = Math.sqrt(Math.pow(centerX, 2) + Math.pow(centerY, 2));
        centerWeight = 1.0 - (distanceFromCenter / maxDistance) * 0.5;

        // 饱和度权重（饱和度高的颜色更显著）
        double saturationWeight = 0.5 + saturation * 0.5;

        // 亮度权重（避免过暗或过亮的颜色权重过低）
        double brightnessWeight = 1.0 - Math.abs(brightness - 0.5) * 0.4;

        return centerWeight * saturationWeight * brightnessWeight;
    }

    /**
     * K-means聚类算法提取主要颜色
     */
    private List<Color> kMeansClustering(List<Color> samples, int k) {
        if (samples.size() < k) {
            return samples.stream().distinct().collect(Collectors.toList());
        }

        // 初始化聚类中心（随机选择k个不同的颜色）
        List<Color> centroids = new ArrayList<>();
        Set<String> usedColors = new HashSet<>();
        Random random = new Random();

        for (int i = 0; i < k && centroids.size() < samples.size(); i++) {
            Color candidate;
            String colorKey;
            int attempts = 0;
            do {
                candidate = samples.get(random.nextInt(samples.size()));
                colorKey = candidate.getRed() + "," + candidate.getGreen() + "," + candidate.getBlue();
                attempts++;
            } while (usedColors.contains(colorKey) && attempts < 50);

            if (!usedColors.contains(colorKey)) {
                centroids.add(candidate);
                usedColors.add(colorKey);
            }
        }

        // 如果没有足够的独特颜色，返回频率最高的颜色
        if (centroids.size() < k) {
            Map<Color, Integer> frequency = new HashMap<>();
            for (Color color : samples) {
                frequency.put(color, frequency.getOrDefault(color, 0) + 1);
            }
            return frequency.entrySet().stream()
                .sorted(Map.Entry.<Color, Integer>comparingByValue().reversed())
                .limit(k)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        }

        // K-means迭代
        final int maxIterations = 10;
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            // 分配样本到最近的聚类中心
            Map<Color, List<Color>> clusters = new HashMap<>();
            for (Color centroid : centroids) {
                clusters.put(centroid, new ArrayList<>());
            }

            for (Color sample : samples) {
                Color nearestCentroid = centroids.stream()
                    .min((c1, c2) -> Double.compare(colorDistance(sample, c1), colorDistance(sample, c2)))
                    .orElse(centroids.get(0));
                clusters.get(nearestCentroid).add(sample);
            }

            // 更新聚类中心
            List<Color> newCentroids = new ArrayList<>();
            for (Color oldCentroid : centroids) {
                List<Color> cluster = clusters.get(oldCentroid);
                if (cluster.isEmpty()) {
                    newCentroids.add(oldCentroid);
                } else {
                    // 计算聚类的平均颜色
                    int avgR = (int) cluster.stream().mapToInt(Color::getRed).average().orElse(0);
                    int avgG = (int) cluster.stream().mapToInt(Color::getGreen).average().orElse(0);
                    int avgB = (int) cluster.stream().mapToInt(Color::getBlue).average().orElse(0);
                    newCentroids.add(new Color(
                        Math.max(0, Math.min(255, avgR)),
                        Math.max(0, Math.min(255, avgG)),
                        Math.max(0, Math.min(255, avgB))
                    ));
                }
            }

            centroids = newCentroids;
        }

        // 使用最终的聚类中心进行最后的聚类分配，然后按样本数量排序
        final Map<Color, List<Color>> finalClusters = assignSamplesToClusters(samples, centroids);

        // 对最终的聚类中心按样本数量排序（最重要的颜色排在前面）
        return centroids.stream()
            .sorted((c1, c2) -> {
                int count1 = finalClusters.get(c1).size();
                int count2 = finalClusters.get(c2).size();
                return Integer.compare(count2, count1); // 降序排列
            })
            .collect(Collectors.toList());
    }

    /**
     * 计算两个颜色之间的距离
     */
    private double colorDistance(Color c1, Color c2) {
        // 使用CIEDE2000颜色距离或者简化的欧几里得距离
        int dr = c1.getRed() - c2.getRed();
        int dg = c1.getGreen() - c2.getGreen();
        int db = c1.getBlue() - c2.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /**
     * 将样本分配到聚类中心
     */
    private Map<Color, List<Color>> assignSamplesToClusters(List<Color> samples, List<Color> centroids) {
        Map<Color, List<Color>> clusters = new HashMap<>();
        for (Color centroid : centroids) {
            clusters.put(centroid, new ArrayList<>());
        }

        for (Color sample : samples) {
            Color nearestCentroid = centroids.stream()
                .min((c1, c2) -> Double.compare(colorDistance(sample, c1), colorDistance(sample, c2)))
                .orElse(centroids.get(0));
            clusters.get(nearestCentroid).add(sample);
        }

        return clusters;
    }

    /**
     * 量化颜色，将相近的颜色归为一类（保留作为备选方案）
     */
    private Color quantizeColor(Color color) {
        int quantizeLevel = 32; // 量化级别
        int r = (color.getRed() / quantizeLevel) * quantizeLevel;
        int g = (color.getGreen() / quantizeLevel) * quantizeLevel;
        int b = (color.getBlue() / quantizeLevel) * quantizeLevel;
        return new Color(r, g, b);
    }

    /**
     * 根据HEX颜色字符串分类颜色
     */
    public static String classifyColor(String hexColor) {
        if (hexColor == null || hexColor.isEmpty()) {
            return ColorCategory.GRAY.name();
        }

        try {
            // 移除#前缀
            String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;

            // 转换为RGB
            int r = Integer.valueOf(hex.substring(0, 2), 16);
            int g = Integer.valueOf(hex.substring(2, 4), 16);
            int b = Integer.valueOf(hex.substring(4, 6), 16);

            // 计算亮度和饱和度
            float[] hsb = Color.RGBtoHSB(r, g, b, null);
            float hue = hsb[0] * 360; // 色相 (0-360)
            float saturation = hsb[1]; // 饱和度 (0-1)
            float brightness = hsb[2]; // 亮度 (0-1)

            // 计算最大和最小值
            int max = Math.max(Math.max(r, g), b);
            int min = Math.min(Math.min(r, g), b);

            // 低饱和度或低对比度认为是灰色
            if (saturation < 0.1 || (max - min) < 30) {
                if (brightness < 0.2) return ColorCategory.BLACK.name();
                if (brightness > 0.8) return ColorCategory.WHITE.name();
                return ColorCategory.GRAY.name();
            }

            // 根据色相分类
            if (hue >= 0 && hue < 30) return ColorCategory.RED.name(); // 红色-橙色过渡
            if (hue >= 30 && hue < 60) return ColorCategory.ORANGE.name(); // 橙色
            if (hue >= 60 && hue < 90) return ColorCategory.YELLOW.name(); // 黄色
            if (hue >= 90 && hue < 150) return ColorCategory.GREEN.name(); // 绿色
            if (hue >= 150 && hue < 210) return ColorCategory.BLUE.name(); // 青色-蓝色
            if (hue >= 210 && hue < 270) return ColorCategory.BLUE.name(); // 蓝色
            if (hue >= 270 && hue < 330) return ColorCategory.PURPLE.name(); // 紫色-品红
            if (hue >= 330 && hue <= 360) return ColorCategory.PINK.name(); // 粉红-红色

            // 默认返回灰色
            return ColorCategory.GRAY.name();

        } catch (Exception e) {
            log.warn("颜色分类失败: {}", hexColor, e);
            return ColorCategory.GRAY.name();
        }
    }
}

