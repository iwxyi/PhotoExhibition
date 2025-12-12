package com.photoexhibition.service;

import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartTagService {

    private final TagRepository tagRepository;

    /**
     * 根据图片特征/EXIF/人脸数量生成常用搜索标签
     */
    @Transactional
    public void applySmartTags(File imageFile, Photo photo, int faceCount) {
        Set<String> names = generateSmartTags(imageFile, photo, faceCount);
        if (names.isEmpty()) {
            return;
        }

        if (photo.getTags() == null) {
            photo.setTags(new HashSet<>());
        }

        for (String name : names) {
            Tag tag = tagRepository.findByName(name)
                .orElseGet(() -> {
                    Tag t = new Tag();
                    t.setName(name);
                    return tagRepository.save(t);
                });
            photo.getTags().add(tag);
        }
    }

    /**
     * 生成智能标签集合
     */
    public Set<String> generateSmartTags(File imageFile, Photo photo, int faceCount) {
        Set<String> tags = new HashSet<>();
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) return tags;

            int w = image.getWidth();
            int h = image.getHeight();
            double aspect = (double) w / Math.max(1, h);

            tags.add(aspect > 1.1 ? "横图" : "竖图");
            if (w * h >= 4000 * 3000) tags.add("高分辨率");

            // 亮度/色调统计
            long sampleCount = 0;
            long brightnessSum = 0;
            long redSum = 0, greenSum = 0, blueSum = 0;
            int step = Math.max(1, Math.max(w, h) / 300);
            for (int y = 0; y < h; y += step) {
                for (int x = 0; x < w; x += step) {
                    Color c = new Color(image.getRGB(x, y));
                    int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
                    brightnessSum += (r + g + b) / 3;
                    redSum += r;
                    greenSum += g;
                    blueSum += b;
                    sampleCount++;
                }
            }

            if (sampleCount > 0) {
                double avgBright = (double) brightnessSum / sampleCount;
                double avgR = (double) redSum / sampleCount;
                double avgG = (double) greenSum / sampleCount;
                double avgB = (double) blueSum / sampleCount;

                if (avgBright < 80) tags.add("夜景");
                if (avgBright > 180) tags.add("明亮");
                if (avgB > avgR * 1.2 && avgB > avgG * 1.2) tags.add("蓝色调");
                if (avgG > avgR * 1.2 && avgG > avgB * 1.1) tags.add("自然");
                if (avgR > avgG * 1.2 && avgR > avgB * 1.2) tags.add("暖色调");
            }

            // EXIF相关
            if (photo.getIso() != null && photo.getIso() >= 1600) {
                tags.add("高ISO");
            }
            if (photo.getAperture() != null) {
                try {
                    String apertureStr = photo.getAperture().replace("f/", "").replace("F/", "");
                    double aperture = Double.parseDouble(apertureStr);
                    if (aperture <= 2.0) {
                        tags.add("大光圈");
                    }
                } catch (Exception ignored) {
                }
            }
            if (photo.getCameraModel() != null && photo.getCameraModel().toLowerCase().contains("iphone")) {
                tags.add("手机拍摄");
            }

            // 人脸
            if (faceCount > 0) {
                tags.add("人像");
                if (faceCount > 1) tags.add("合影");
            }

        } catch (Exception e) {
            log.warn("智能标签生成失败: {}", imageFile.getName(), e);
        }
        return tags;
    }
}

