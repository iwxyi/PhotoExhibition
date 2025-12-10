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

            photo.setDominantColor(String.format("#%02x%02x%02x", 
                dominantColor.getRed(), 
                dominantColor.getGreen(), 
                dominantColor.getBlue()));

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
}

