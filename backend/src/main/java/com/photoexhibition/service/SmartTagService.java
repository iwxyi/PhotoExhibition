package com.photoexhibition.service;

import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmartTagService {

    private final TagRepository tagRepository;
    
    @Autowired(required = false)
    private ImageClassificationService imageClassificationService;

    /**
     * 智能标签名称集合（用于识别和删除智能标签）
     * 包括规则生成的标签
     */
    private static final Set<String> RULE_BASED_SMART_TAGS = Set.of(
        // 规则标签（固定列表）
        "横图", "竖图", "高分辨率", "夜景", "明亮", "蓝色调", "自然", "暖色调",
        "高ISO", "大光圈", "手机拍摄", "人像", "合影"
    );

    /**
     * 判断标签是否为智能标签（强制扫描时用于删除旧智能标签）
     * 
     * 策略：
     * 1. 相册标签：保留（不是智能标签）
     * 2. 规则生成的标签：删除（固定列表）
     * 3. 本次将要生成的智能标签：删除（AI分类或规则生成）
     * 4. AI分类可能生成的标签：如果AI分类服务已启用，检查标签是否可能是ImageNet类别
     * 5. 其他标签：保留（可能是手动添加的，采用保守策略）
     * 
     * @param tagName 标签名称
     * @param albumTagNames 相册标签名称集合（相册标签应该保留）
     * @param currentSmartTagNames 本次将要生成的智能标签名称集合
     */
    private boolean isSmartTag(String tagName, Set<String> albumTagNames, Set<String> currentSmartTagNames) {
        // 相册标签不是智能标签，应该保留
        if (albumTagNames != null && albumTagNames.contains(tagName)) {
            return false;
        }
        
        // 规则生成的标签（固定列表）- 确定是智能标签
        if (RULE_BASED_SMART_TAGS.contains(tagName)) {
            return true;
        }
        
        // 本次将要生成的智能标签（AI分类或规则生成）- 确定是智能标签
        if (currentSmartTagNames.contains(tagName)) {
            return true;
        }
        
        // 如果AI分类服务已启用，检查标签是否可能是ImageNet类别
        // 这样可以删除所有旧的AI分类标签，即使它们不在本次生成列表中
        if (imageClassificationService != null) {
            if (imageClassificationService.isPossibleImageNetTag(tagName)) {
                return true;
            }
        }
        
        // 其他标签：可能是手动添加的，保留（保守策略，避免误删手动标签）
        return false;
    }

    /**
     * 根据图片特征/EXIF/人脸数量生成常用搜索标签
     * @param imageFile 图片文件
     * @param photo 照片实体
     * @param faceCount 人脸数量
     * @param force 是否强制重建（true=删除旧智能标签后重新生成，false=追加）
     * @param albumTagNames 相册标签名称集合（用于区分智能标签和相册标签）
     */
    @Transactional
    public void applySmartTags(File imageFile, Photo photo, int faceCount, boolean force, Set<String> albumTagNames) {
        // 先生成智能标签名称（用于识别哪些是智能标签）
        Set<String> names = generateSmartTags(imageFile, photo, faceCount);
        
        // 如果强制重建，先删除旧的智能标签（保留相册标签和手动标签）
        if (force && photo.getTags() != null && !photo.getTags().isEmpty()) {
            Set<Tag> tagsToRemove = new HashSet<>();
            Set<String> albumTagSet = albumTagNames != null ? albumTagNames : Set.of();
            
            for (Tag tag : photo.getTags()) {
                // 只删除智能标签，保留相册标签和手动标签
                if (isSmartTag(tag.getName(), albumTagSet, names)) {
                    tagsToRemove.add(tag);
                }
            }
            if (!tagsToRemove.isEmpty()) {
                photo.getTags().removeAll(tagsToRemove);
                log.info("强制扫描：删除 {} 个旧智能标签，保留 {} 个手动标签", 
                    tagsToRemove.size(), photo.getTags().size());
            }
        }

        if (names.isEmpty()) {
            return;
        }

        if (photo.getTags() == null) {
            photo.setTags(new HashSet<>());
        }

        // 添加新生成的智能标签（避免重复添加）
        for (String name : names) {
            Tag tag = tagRepository.findByName(name)
                .orElseGet(() -> {
                    Tag t = new Tag();
                    t.setName(name);
                    return tagRepository.save(t);
                });
            // 检查是否已经包含此标签，避免重复添加导致主键冲突
            if (!photo.getTags().contains(tag)) {
                photo.getTags().add(tag);
            }
        }
    }

    /**
     * 兼容旧接口（默认不强制重建，无相册标签）
     */
    @Transactional
    public void applySmartTags(File imageFile, Photo photo, int faceCount) {
        applySmartTags(imageFile, photo, faceCount, false, null);
    }

    /**
     * 生成智能标签集合
     * 优先使用AI分类模型，失败时回退到基于规则的方法
     */
    public Set<String> generateSmartTags(File imageFile, Photo photo, int faceCount) {
        Set<String> tags = new HashSet<>();
        
        // 1. 尝试使用AI图像分类（如果启用）
        if (imageClassificationService != null) {
            try {
                List<ImageClassificationService.ClassificationResult> classifications = 
                    imageClassificationService.classify(imageFile);
                
                if (!classifications.isEmpty()) {
                    // 将分类结果转换为标签，并收集详细信息用于日志
                    StringBuilder tagDetails = new StringBuilder();
                    int addedCount = 0;
                    
                    for (ImageClassificationService.ClassificationResult result : classifications) {
                        String label = result.getLabel();
                        float confidence = result.getConfidence();
                        // 标签已经在ImageClassificationService中根据语言配置处理过了
                        if (label != null && !label.isEmpty()) {
                            tags.add(label);
                            if (addedCount > 0) {
                                tagDetails.append(", ");
                            }
                            // 确保置信度在合理范围内（0-1），然后转换为百分比
                            float normalizedConfidence = Math.max(0.0f, Math.min(1.0f, confidence));
                            tagDetails.append(label).append("(")
                                      .append(String.format("%.2f", normalizedConfidence * 100)).append("%)");
                            addedCount++;
                        }
                    }
                    log.info("AI分类生成 {} 个标签: [{}]", addedCount, tagDetails.toString());
                }
            } catch (Exception e) {
                log.debug("AI分类失败，回退到基于规则的方法: {}", e.getMessage());
            }
        }
        
        // 2. 基于规则的标签生成（作为补充和回退方案）
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) return tags;

            int w = image.getWidth();
            int h = image.getHeight();
            double aspect = (double) w / Math.max(1, h);

            // 基础属性标签
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

