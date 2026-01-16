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

            // 提取主要颜色
            Map<Color, Integer> colorFrequency = new HashMap<>();
            int width = scaledImage.getWidth();
            int height = scaledImage.getHeight();

            // 采样像素
            int sampleStep = 5; // 每5个像素采样一次
            for (int x = 0; x < width; x += sampleStep) {
                for (int y = 0; y < height; y += sampleStep) {
                    Color color = new Color(scaledImage.getRGB(x, y));
                    // 量化颜色以减少颜色数量
                    Color quantized = quantizeColor(color);
                    colorFrequency.put(quantized, colorFrequency.getOrDefault(quantized, 0) + 1);
                }
            }

            // 找出主色调
            Color dominantColor = colorFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(new Color(128, 128, 128));

            String hexColor = String.format("#%02x%02x%02x",
                dominantColor.getRed(),
                dominantColor.getGreen(),
                dominantColor.getBlue());
            photo.setDominantColor(hexColor);
            photo.setColorCategory(classifyColor(hexColor));

            // 提取调色板（前5种主要颜色）
            List<String> palette = colorFrequency.entrySet().stream()
                .sorted(Map.Entry.<Color, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Color c = entry.getKey();
                    return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
                })
                .collect(Collectors.toList());

            photo.setColorPalette(String.format("[\"%s\"]", String.join("\",\"", palette)));

        } catch (IOException e) {
            log.warn("色彩分析失败: {}", imageFile.getName(), e);
        }
    }

    /**
     * 量化颜色，将相近的颜色归为一类
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

