package com.photoexhibition.service;

import com.photoexhibition.entity.Photo;
import com.photoexhibition.repository.PhotoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/**
 * 相似照片搜索服务
 * 基于多种特征（颜色、构图、人脸、标签等）搜索相似照片
 */
@Slf4j
@Service
public class SimilarPhotoSearchService {

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private ColorAnalysisService colorAnalysisService;

    @Autowired
    private FaceService faceService;

    /**
     * 搜索相似照片
     */
    public List<SimilarPhotoResult> findSimilarPhotos(Long photoId, int limit) {
        return findSimilarPhotos(photoId, limit, null);
    }

    public List<SimilarPhotoResult> findSimilarPhotos(Long photoId, int limit, Long userId) {
        Photo targetPhoto = userId == null
            ? photoRepository.findById(photoId).orElse(null)
            : photoRepository.findByIdAndUserId(photoId, userId).orElse(null);
        if (targetPhoto == null) {
            return new ArrayList<>();
        }

        List<SimilarPhotoResult> candidates = new ArrayList<>();

        // 1. 基于颜色的相似性搜索
        List<SimilarPhotoResult> colorSimilar = findColorSimilarPhotos(targetPhoto, limit * 2, userId);
        candidates.addAll(colorSimilar);

        // 2. 基于标签的相似性搜索
        List<SimilarPhotoResult> tagSimilar = findTagSimilarPhotos(targetPhoto, limit * 2, userId);
        candidates.addAll(tagSimilar);

        // 3. 基于人脸的相似性搜索
        List<SimilarPhotoResult> faceSimilar = findFaceSimilarPhotos(targetPhoto, limit * 2, userId);
        candidates.addAll(faceSimilar);

        // 4. 基于场景/相册的相似性搜索
        List<SimilarPhotoResult> albumSimilar = findAlbumSimilarPhotos(targetPhoto, limit * 2, userId);
        candidates.addAll(albumSimilar);

        // 5. 合并和排序结果
        Map<Long, SimilarPhotoResult> uniqueResults = new HashMap<>();
        for (SimilarPhotoResult result : candidates) {
            if (uniqueResults.containsKey(result.photoId)) {
                // 合并相似度分数
                SimilarPhotoResult existing = uniqueResults.get(result.photoId);
                existing.similarityScore = Math.max(existing.similarityScore, result.similarityScore);
                existing.matchReasons.addAll(result.matchReasons);
                // 去重
                existing.matchReasons = existing.matchReasons.stream().distinct().collect(Collectors.toList());
            } else {
                uniqueResults.put(result.photoId, result);
            }
        }

        // 转换为列表并排序
        List<SimilarPhotoResult> results = new ArrayList<>(uniqueResults.values());
        results.sort((a, b) -> Double.compare(b.similarityScore, a.similarityScore));

        // 返回前limit个结果
        return results.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * 基于颜色相似性搜索
     */
    private List<SimilarPhotoResult> findColorSimilarPhotos(Photo targetPhoto, int limit, Long userId) {
        List<SimilarPhotoResult> results = new ArrayList<>();

        if (targetPhoto.getDominantColor() == null) {
            return results;
        }

        try {
            // 计算目标照片的主色调
            Color targetColor = Color.decode(targetPhoto.getDominantColor());

            // 搜索具有相似颜色的照片
            List<Photo> candidates = loadScopedVisibleCandidates(userId, limit * 2);

            for (Photo photo : candidates) {
                if (photo.getId().equals(targetPhoto.getId())) {
                    continue; // 跳过自己
                }

                if (photo.getDominantColor() != null) {
                    Color candidateColor = Color.decode(photo.getDominantColor());
                    double colorSimilarity = calculateColorSimilarity(targetColor, candidateColor);

                    if (colorSimilarity > 0.7) { // 颜色相似度阈值
                        SimilarPhotoResult result = new SimilarPhotoResult();
                        result.photoId = photo.getId();
                        result.photo = photo;
                        result.similarityScore = colorSimilarity * 0.8; // 颜色权重80%
                        result.matchReasons.add("颜色相似");
                        results.add(result);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("颜色相似性搜索失败: {}", e.getMessage());
        }

        return results;
    }

    /**
     * 基于标签相似性搜索
     */
    private List<SimilarPhotoResult> findTagSimilarPhotos(Photo targetPhoto, int limit, Long userId) {
        List<SimilarPhotoResult> results = new ArrayList<>();

        if (targetPhoto.getTags() == null || targetPhoto.getTags().isEmpty()) {
            return results;
        }

        Set<String> targetTagNames = targetPhoto.getTags().stream()
                .map(tag -> tag.getName())
                .collect(Collectors.toSet());

        // 搜索具有相似标签的照片
        List<Photo> candidates = loadScopedVisibleCandidates(userId, limit * 3);

        for (Photo photo : candidates) {
            if (photo.getId().equals(targetPhoto.getId())) {
                continue;
            }

            if (photo.getTags() != null && !photo.getTags().isEmpty()) {
                Set<String> candidateTagNames = photo.getTags().stream()
                        .map(tag -> tag.getName())
                        .collect(Collectors.toSet());

                // 计算标签交集
                Set<String> intersection = new HashSet<>(targetTagNames);
                intersection.retainAll(candidateTagNames);

                if (!intersection.isEmpty()) {
                    double tagSimilarity = (double) intersection.size() /
                            Math.max(targetTagNames.size(), candidateTagNames.size());

                    if (tagSimilarity > 0.3) { // 标签相似度阈值
                        SimilarPhotoResult result = new SimilarPhotoResult();
                        result.photoId = photo.getId();
                        result.photo = photo;
                        result.similarityScore = tagSimilarity * 0.9; // 标签权重90%
                        result.matchReasons.add("标签相似: " + String.join(", ", intersection));
                        results.add(result);
                    }
                }
            }
        }

        return results;
    }

    /**
     * 基于人脸相似性搜索
     */
    private List<SimilarPhotoResult> findFaceSimilarPhotos(Photo targetPhoto, int limit, Long userId) {
        List<SimilarPhotoResult> results = new ArrayList<>();

        try {
            // 获取目标照片的人脸
            List<com.photoexhibition.entity.Face> targetFaces = faceService.getFacesByPhoto(targetPhoto.getId());
            if (targetFaces == null || targetFaces.isEmpty()) {
                return results;
            }

            // 搜索包含人脸的照片
            List<Photo> candidates = loadScopedVisibleCandidates(userId, limit * 3).stream()
                .filter(photo -> photo.getFaces() != null && !photo.getFaces().isEmpty())
                .collect(Collectors.toList());

            for (Photo photo : candidates) {
                if (photo.getId().equals(targetPhoto.getId())) {
                    continue;
                }

                List<com.photoexhibition.entity.Face> candidateFaces = faceService.getFacesByPhoto(photo.getId());
                if (candidateFaces != null && !candidateFaces.isEmpty()) {
                    // 检查是否包含相似的人脸数量
                    double faceSimilarity = Math.min(1.0,
                            (double) Math.min(targetFaces.size(), candidateFaces.size()) /
                            Math.max(targetFaces.size(), candidateFaces.size()));

                    if (faceSimilarity > 0.5) { // 人脸相似度阈值
                        SimilarPhotoResult result = new SimilarPhotoResult();
                        result.photoId = photo.getId();
                        result.photo = photo;
                        result.similarityScore = faceSimilarity * 0.7; // 人脸权重70%
                        result.matchReasons.add("人脸数量相似");
                        results.add(result);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("人脸相似性搜索失败: {}", e.getMessage());
        }

        return results;
    }

    /**
     * 基于相册/场景相似性搜索
     */
    private List<SimilarPhotoResult> findAlbumSimilarPhotos(Photo targetPhoto, int limit, Long userId) {
        List<SimilarPhotoResult> results = new ArrayList<>();

        // 基于相册ID搜索
        Pageable pageable = PageRequest.of(0, limit);
        Page<Photo> albumPhotos = userId == null
            ? photoRepository.findByAlbumId(targetPhoto.getAlbumId(), pageable)
            : photoRepository.findByAlbumIdAndUserId(targetPhoto.getAlbumId(), userId, pageable);

        for (Photo photo : albumPhotos.getContent()) {
            if (photo.getId().equals(targetPhoto.getId())) {
                continue;
            }

            SimilarPhotoResult result = new SimilarPhotoResult();
            result.photoId = photo.getId();
            result.photo = photo;
            result.similarityScore = 0.6; // 同一相册权重60%
            result.matchReasons.add("同一相册");
            results.add(result);
        }

        return results;
    }

    private List<Photo> loadScopedVisibleCandidates(Long userId, int limit) {
        int safeLimit = Math.max(1, limit);
        if (userId == null) {
            Page<Photo> candidates = photoRepository.findAll(PageRequest.of(0, safeLimit));
            return candidates.getContent();
        }
        return photoRepository.findVisibleByUserId(userId, PageRequest.of(0, safeLimit)).getContent();
    }

    /**
     * 计算颜色相似度（使用HSV颜色空间）
     */
    private double calculateColorSimilarity(Color color1, Color color2) {
        float[] hsb1 = Color.RGBtoHSB(color1.getRed(), color1.getGreen(), color1.getBlue(), null);
        float[] hsb2 = Color.RGBtoHSB(color2.getRed(), color2.getGreen(), color2.getBlue(), null);

        // 计算HSV空间中的欧几里得距离
        double hueDiff = Math.min(Math.abs(hsb1[0] - hsb2[0]), 1.0 - Math.abs(hsb1[0] - hsb2[0]));
        double saturationDiff = Math.abs(hsb1[1] - hsb2[1]);
        double brightnessDiff = Math.abs(hsb1[2] - hsb2[2]);

        // 加权计算相似度
        double distance = Math.sqrt(
                hueDiff * hueDiff * 4 + // 色相权重更高
                saturationDiff * saturationDiff +
                brightnessDiff * brightnessDiff
        );

        // 转换为相似度（0-1），距离越小相似度越高
        return Math.max(0, 1.0 - distance / Math.sqrt(6)); // 最大距离约为sqrt(4+1+1)=sqrt(6)
    }

    /**
     * 相似照片搜索结果
     */
    public static class SimilarPhotoResult {
        public Long photoId;
        public Photo photo;
        public double similarityScore; // 0-1之间的相似度分数
        public List<String> matchReasons = new ArrayList<>(); // 匹配原因
    }
}
