package com.photoexhibition.service;

import com.photoexhibition.dto.FaceDTO;
import com.photoexhibition.dto.FaceClusterDTO;
import com.photoexhibition.dto.PersonDTO;
import com.photoexhibition.dto.PersonListItemDTO;
import com.photoexhibition.dto.PersonSimilarityDTO;
import com.photoexhibition.dto.AlbumRecommendationDTO;
import com.photoexhibition.entity.Face;
import com.photoexhibition.entity.PersonProfile;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.Album;
import com.photoexhibition.dto.PersonSummaryDTO;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.PersonProfileRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.PhotoAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaceService {

    // 用于预过滤的辅助类
    private static class GroupSimilarity {
        final int groupIndex;
        final double similarity;

        GroupSimilarity(int groupIndex, double similarity) {
            this.groupIndex = groupIndex;
            this.similarity = similarity;
        }
    }

    private final FaceRepository faceRepository;
    private final PersonProfileRepository personProfileRepository;
    private final PhotoRepository photoRepository;
    private final AlbumRepository albumRepository;
    private final PhotoAssignmentRepository photoAssignmentRepository;
    private final AlbumService albumService;
    private final FaceRecognitionService faceRecognitionService;
    private final FaceEmbeddingService faceEmbeddingService;
    @Value("${photo.scan.base-path}")
    private String photoBasePath;
    @Value("${face.detection.confidence-threshold:0.25}")
    private double detectionConfidenceThreshold;

    // 以下过滤参数可通过 application.yml 调整
    @Value("${face.filters.min-area:0.002}")
    private double minArea;          // 最小面积（相对整图）
    @Value("${face.filters.max-area:0.35}")
    private double maxArea;          // 最大面积（相对整图）
    @Value("${face.filters.max-width:0.5}")
    private double maxWidth;         // 最大宽度占比
    @Value("${face.filters.max-height:0.5}")
    private double maxHeight;        // 最大高度占比
    @Value("${face.filters.min-ratio:0.4}")
    private double minRatio;         // 最小宽高比
    @Value("${face.filters.max-ratio:2.5}")
    private double maxRatio;         // 最大宽高比
    @Value("${face.filters.max-faces-per-image:20}")
    private int maxFacesPerImage;    // 单张图片最多保存人脸数
    // 聚类缓存（按阈值存储计算结果，避免重复计算）
    private final Map<Double, List<FaceClusterDTO>> clusterCache = new ConcurrentHashMap<>();

    // ========== 人物相似度缓存（用于加速"相似推荐"和"未分配"tab） ==========
    // 缓存结构：personId -> PersonSimilarityCacheEntry
    private final ConcurrentHashMap<Long, PersonSimilarityCacheEntry> personSimilarityCache = new ConcurrentHashMap<>();
    // 上一次未分配人脸总数，用于检测未分配人脸变化
    private long lastUnassignedCount = -1;
    // 用于防止并发重复计算
    private final ConcurrentHashMap<Long, Object> computingLocks = new ConcurrentHashMap<>();

    /**
     * 人物相似度缓存条目
     * 存储：该人物与所有未分配人脸的相似度计算结果
     */
    private static class PersonSimilarityCacheEntry {
        // 该人物与所有未分配人脸的相似度列表
        final List<CachedFaceSimilarity> allSimilarities;
        // 缓存时的未分配人脸数量，用于检测变化
        final long unassignedFaceCount;
        // 缓存时间
        final long computedAt;

        PersonSimilarityCacheEntry(List<CachedFaceSimilarity> allSimilarities, long unassignedFaceCount) {
            this.allSimilarities = allSimilarities;
            this.unassignedFaceCount = unassignedFaceCount;
            this.computedAt = System.currentTimeMillis();
        }
    }

    /**
     * 缓存的人脸相似度信息
     */
    private static class CachedFaceSimilarity {
        final Long faceId;
        final double similarity;
        final boolean sameFolder;

        CachedFaceSimilarity(Long faceId, double similarity, boolean sameFolder) {
            this.faceId = faceId;
            this.similarity = similarity;
            this.sameFolder = sameFolder;
        }
    }
    // ========== 缓存定义结束 ==========

    // 聚类策略参数（可在 application.yml 配置）
    @Value("${face.clustering.threshold-bonus:0.05}")
    private double clusteringThresholdBonus; // 在用户阈值基础上增加的偏移
    @Value("${face.clustering.min-threshold:0.78}")
    private double clusteringMinThreshold;   // 聚类最低阈值
    @Value("${face.clustering.min-matches:3}")
    private int clusteringMinMatches;        // 多点验证需要的最少匹配数
    @Value("${face.clustering.min-group-size:3}")
    private int clusteringMinGroupSize;      // 进入“大组”判定的最小组大小
    @Value("${face.clustering.require-multi-point:true}")
    private boolean clusteringRequireMultiPoint; // 是否启用多点验证
    @Value("${face.clustering.require-stats:true}")
    private boolean clusteringRequireStats;      // 是否启用统计指标（均值/中位/75分位等）
    @Value("${face.clustering.small-group-strict:true}")
    private boolean clusteringSmallGroupStrict;  // 小组（成员数 < min-group-size）是否使用严格匹配
    @Value("${face.clustering.min-sim-slack-small:0.03}")
    private double clusteringMinSimSlackSmall;   // 小组最小相似度的放宽值
    @Value("${face.clustering.min-sim-slack-large:0.05}")
    private double clusteringMinSimSlackLarge;   // 大组最小相似度的放宽值

    /**
     * 重新检测并保存某张照片的人脸信息
     */
    @Transactional
    public List<Face> detectAndSaveFaces(File imageFile, Photo photo) {
        return detectAndSaveFaces(imageFile, photo, false);
    }

    /**
     * 重新检测并保存某张照片的人脸信息
     * @param verbose 是否输出详细调试日志（单张重建时开启，批量扫描时关闭）
     */
    @Transactional
    public List<Face> detectAndSaveFaces(File imageFile, Photo photo, boolean verbose) {
        // 防止并发重复检测：检查该照片是否已经有处理中的人脸检测
        Long photoId = photo.getId();
        if (photoId != null) {
            // 检查是否已有人脸数据，如果有则跳过重复检测
            long existingFaceCount = faceRepository.findByPhotoId(photoId).size();
            if (existingFaceCount > 0) {
                log.debug("照片 {} 已有 {} 个人脸记录，跳过重复检测", photoId, existingFaceCount);
                return faceRepository.findByPhotoId(photoId);
            }
        }

        // 控制检测服务的详细日志开关
        FaceDetectionService.VERBOSE_LOG.set(verbose);
        List<FaceRecognitionService.DetectedFace> detected;
        try {
            detected = faceRecognitionService.detectFaces(imageFile);
        } finally {
            // 用完即清理，避免泄漏到其他线程
            FaceDetectionService.VERBOSE_LOG.remove();
        }
        if (verbose) {
            log.debug("人脸检测候选数量: {}，file={}", detected.size(), imageFile.getName());
        }

        // 使用传入的 Photo 实例，如果有ID则删除现有的人脸记录
        Photo targetPhoto = photo;
        if (photo.getId() != null) {
            faceRepository.deleteByPhotoId(photo.getId());
        }

        // 排序并截取前 N 个最高置信度的人脸（N 可配置）
        detected.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        int limit = Math.max(1, maxFacesPerImage);
        if (detected.size() > limit) {
            detected = detected.subList(0, limit);
        }

        List<Face> faces = new ArrayList<>();
        for (FaceRecognitionService.DetectedFace f : detected) {
            // 仅做必要的过滤，尽量保留ONNX检测结果，避免过度过滤导致全部丢弃
            // 1. 置信度阈值从配置读取（默认0.25，原0.5）
            if (f.getConfidence() < detectionConfidenceThreshold) {
                if (verbose) {
                    log.debug("跳过低置信度人脸: conf={}, file={}", f.getConfidence(), imageFile.getName());
                }
                continue;
            }
            // 2. 坐标归一化与裁剪，防止越界导致空白
            double x = Math.max(0.0, Math.min(1.0, f.getX()));
            double y = Math.max(0.0, Math.min(1.0, f.getY()));
            double w = Math.max(0.0, Math.min(1.0 - x, f.getWidth()));
            double h = Math.max(0.0, Math.min(1.0 - y, f.getHeight()));
            // 3. 面积/比例过滤：过滤极小噪点和极端比例框
            double area = w * h;
            if (area <= 0.0) {
                if (verbose) {
                    log.debug("跳过无效面积人脸: area={}, w={}, h={}, file={}", area, w, h, imageFile.getName());
                }
                continue;
            }
            // 最小面积（可配置）
            if (area < minArea) {
                if (verbose) {
                    log.debug("跳过面积过小的人脸: area={}, w={}, h={}, file={}", area, w, h, imageFile.getName());
                }
                continue;
            }
            // 最大面积 / 尺寸（可配置，避免整身/整图）
            if (area > maxArea || w > maxWidth || h > maxHeight) {
                if (verbose) {
                    log.debug("跳过面积/尺寸过大的人脸: area={}, w={}, h={}, file={}", area, w, h, imageFile.getName());
                }
                continue;
            }
            double ratio = h > 0 ? w / h : 1.0;
            if (ratio < minRatio || ratio > maxRatio) {
                if (verbose) {
                    log.debug("跳过异常比例人脸: ratio={}, w={}, h={}, file={}", ratio, w, h, imageFile.getName());
                }
                continue;
            }
            Face face = new Face();
            face.setPhoto(targetPhoto);
            face.setX(x);
            face.setY(y);
            face.setWidth(w);
            face.setHeight(h);
            face.setConfidence(f.getConfidence());
            Face saved = faceRepository.save(face);

            // 提取人脸向量
            float[] embedding = faceEmbeddingService.extract(imageFile, saved);
            if (embedding != null) {
                saved.setEmbedding(toJson(embedding));
                saved = faceRepository.save(saved);
            }
            faces.add(saved);
        }

        // 注意：不再直接设置photo的faces属性，避免Hibernate orphanRemoval问题
        // 改为通过返回值让调用方决定如何处理faces集合的更新
        // targetPhoto.setFaces(faces); // 移除这行，避免事务中的集合引用变更
        log.debug("保存人脸 {} 个，photoId={}", faces.size(), targetPhoto.getId());
        invalidateClusterCache();
        return faces;
    }

    /**
     * 获取某张照片的人脸列表（返回Face实体，用于内部使用）
     */
    @Transactional(readOnly = true)
    public List<Face> getFacesByPhoto(Long photoId) {
        return faceRepository.findByPhotoId(photoId);
    }

    /**
     * 获取某张照片的人脸列表（返回DTO）
     */
    @Transactional(readOnly = true)
    public List<FaceDTO> getFacesByPhotoDTO(Long photoId) {
        return faceRepository.findByPhotoId(photoId).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * 获取某张照片的人脸数量
     */
    @Transactional(readOnly = true)
    public long getFaceCountByPhoto(Long photoId) {
        return faceRepository.findByPhotoId(photoId).size();
    }

    /**
     * 分页获取所有人脸记录，可按姓名/文件名模糊搜索
     */
    @Transactional(readOnly = true)
    public Page<FaceDTO> listFaces(String keyword, Pageable pageable) {
        Page<Face> page = faceRepository.searchFaces(keyword, pageable);
        return page.map(this::toDTO);
    }

    /**
     * 将人脸绑定到人物（personId 为空则解绑）
     * @param faceId 人脸ID
     * @param personId 人物ID（为空则解绑）
     * @param confirmed 是否确认（true=已确认，false=自动分配，null=保持原状态）
     */
    @Transactional
    public FaceDTO assignFaceToPerson(Long faceId, Long personId, Boolean confirmed) {
        Face face = faceRepository.findById(faceId)
            .orElseThrow(() -> new RuntimeException("人脸不存在"));

        // 记录原来的 personId，用于清除缓存
        Long originalPersonId = face.getPerson() != null ? face.getPerson().getId() : null;

        if (personId == null) {
            face.setPerson(null);
            face.setIsConfirmed(false);
            faceRepository.save(face);
        } else {
            PersonProfile person = personProfileRepository.findById(personId)
                .orElseThrow(() -> new RuntimeException("人物不存在"));
            face.setPerson(person);
            if (confirmed != null) {
                face.setIsConfirmed(confirmed);
            }
            faceRepository.save(face);
        }
        invalidateClusterCache();
        // 清除相关人物的相似度缓存
        if (originalPersonId != null) {
            clearPersonSimilarityCache(originalPersonId);
        }
        if (personId != null && !personId.equals(originalPersonId)) {
            clearPersonSimilarityCache(personId);
        }
        return toDTO(face);
    }

    /**
     * 检查移除已确认人脸后，哪些自动分配人脸需要清理
     * @param personId 人物ID
     * @param removedFaceId 已移除的人脸ID
     * @return 需要解绑的人脸ID列表
     */
    @Transactional(readOnly = true)
    public List<Long> findFacesToCleanupAfterRemoval(Long personId, Long removedFaceId) {
        List<Long> result = new ArrayList<>();
        
        // 获取该人物的剩余已确认人脸
        List<Face> remainingConfirmed = faceRepository.findByPersonIdAndIsConfirmed(personId, true, PageRequest.of(0, 1000))
            .getContent();
        
        // 如果没有剩余已确认人脸，则所有自动分配的人脸都需要解绑
        if (remainingConfirmed.isEmpty()) {
            List<Face> autoAssigned = faceRepository.findByPersonIdAndIsConfirmed(personId, false, PageRequest.of(0, 1000))
                .getContent();
            return autoAssigned.stream().map(Face::getId).collect(Collectors.toList());
        }

        // 获取已移除的人脸向量
        Face removedFace = faceRepository.findById(removedFaceId).orElse(null);
        if (removedFace == null || removedFace.getEmbedding() == null || removedFace.getEmbedding().isEmpty()) {
            return result;
        }
        float[] removedVec = parseEmbedding(removedFace.getEmbedding());
        if (removedVec == null) return result;

        // 计算剩余已确认人脸的平均向量
        List<float[]> confirmedVectors = new ArrayList<>();
        for (Face confirmed : remainingConfirmed) {
            if (confirmed.getEmbedding() == null || confirmed.getEmbedding().isEmpty()) continue;
            float[] vec = parseEmbedding(confirmed.getEmbedding());
            if (vec != null) {
                confirmedVectors.add(vec);
            }
        }
        if (confirmedVectors.isEmpty()) return result;

        // 计算平均向量
        int dim = confirmedVectors.get(0).length;
        float[] avgVec = new float[dim];
        for (float[] vec : confirmedVectors) {
            for (int i = 0; i < dim; i++) {
                avgVec[i] += vec[i];
            }
        }
        int count = confirmedVectors.size();
        for (int i = 0; i < dim; i++) {
            avgVec[i] /= count;
        }
        // 归一化
        double norm = 0;
        for (float v : avgVec) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-6) {
            for (int i = 0; i < dim; i++) {
                avgVec[i] /= (float) norm;
            }
        }

        // 获取该人物的自动分配人脸
        List<Face> autoAssigned = faceRepository.findByPersonIdAndIsConfirmed(personId, false, PageRequest.of(0, 1000))
            .getContent();

        // 检查每个自动分配的人脸
        for (Face autoFace : autoAssigned) {
            if (autoFace.getEmbedding() == null || autoFace.getEmbedding().isEmpty()) continue;
            float[] autoVec = parseEmbedding(autoFace.getEmbedding());
            if (autoVec == null) continue;

            // 计算与已移除人脸的相似度
            double simToRemoved = cosine(autoVec, removedVec);
            
            // 计算与剩余已确认人脸平均向量的相似度
            double simToRemaining = cosine(autoVec, avgVec);

            // 如果与已移除人脸的相似度 >= 0.75，但与剩余已确认人脸的相似度 < 0.6，则需要解绑
            if (simToRemoved >= 0.75 && simToRemaining < 0.6) {
                result.add(autoFace.getId());
            }
        }
        
        return result;
    }

    /**
     * 批量解绑人脸
     * @param faceIds 要解绑的人脸ID列表
     */
    @Transactional
    public void unassignFaces(List<Long> faceIds) {
        Set<Long> affectedPersonIds = new HashSet<>();
        for (Long faceId : faceIds) {
            Face face = faceRepository.findById(faceId).orElse(null);
            if (face != null && face.getPerson() != null) {
                affectedPersonIds.add(face.getPerson().getId());
            }
            if (face != null) {
                face.setPerson(null);
                face.setIsConfirmed(false);
                faceRepository.save(face);
            }
        }
        // 清除受影响人物的缓存
        for (Long personId : affectedPersonIds) {
            clearPersonSimilarityCache(personId);
        }
        log.info("已批量解绑 {} 个人脸", faceIds.size());
    }

    /**
     * 移除已确认人脸后，清理只与该人脸相似的自动分配人脸（已废弃，改为需要用户确认）
     * @param personId 人物ID
     * @param removedFaceId 已移除的人脸ID
     */
    @Deprecated
    @SuppressWarnings("unused")
    private void cleanupAutoAssignedFacesAfterRemoval(Long personId, Long removedFaceId) {
        // 获取该人物的剩余已确认人脸
        List<Face> remainingConfirmed = faceRepository.findByPersonIdAndIsConfirmed(personId, true, PageRequest.of(0, 1000))
            .getContent();
        
        // 如果没有剩余已确认人脸，则解绑所有自动分配的人脸
        if (remainingConfirmed.isEmpty()) {
            List<Face> autoAssigned = faceRepository.findByPersonIdAndIsConfirmed(personId, false, PageRequest.of(0, 1000))
                .getContent();
            for (Face autoFace : autoAssigned) {
                autoFace.setPerson(null);
                autoFace.setIsConfirmed(false);
                faceRepository.save(autoFace);
            }
            if (!autoAssigned.isEmpty()) {
                log.info("已移除人物 {} 的所有自动分配人脸（无剩余已确认人脸）", personId);
            }
            return;
        }

        // 获取已移除的人脸向量
        Face removedFace = faceRepository.findById(removedFaceId).orElse(null);
        if (removedFace == null || removedFace.getEmbedding() == null || removedFace.getEmbedding().isEmpty()) {
            return;
        }
        float[] removedVec = parseEmbedding(removedFace.getEmbedding());
        if (removedVec == null) return;

        // 计算剩余已确认人脸的平均向量
        List<float[]> confirmedVectors = new ArrayList<>();
        for (Face confirmed : remainingConfirmed) {
            if (confirmed.getEmbedding() == null || confirmed.getEmbedding().isEmpty()) continue;
            float[] vec = parseEmbedding(confirmed.getEmbedding());
            if (vec != null) {
                confirmedVectors.add(vec);
            }
        }
        if (confirmedVectors.isEmpty()) return;

        // 计算平均向量
        int dim = confirmedVectors.get(0).length;
        float[] avgVec = new float[dim];
        for (float[] vec : confirmedVectors) {
            for (int i = 0; i < dim; i++) {
                avgVec[i] += vec[i];
            }
        }
        int count = confirmedVectors.size();
        for (int i = 0; i < dim; i++) {
            avgVec[i] /= count;
        }
        // 归一化
        double norm = 0;
        for (float v : avgVec) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-6) {
            for (int i = 0; i < dim; i++) {
                avgVec[i] /= (float) norm;
            }
        }

        // 获取该人物的自动分配人脸
        List<Face> autoAssigned = faceRepository.findByPersonIdAndIsConfirmed(personId, false, PageRequest.of(0, 1000))
            .getContent();

        // 检查每个自动分配的人脸
        for (Face autoFace : autoAssigned) {
            if (autoFace.getEmbedding() == null || autoFace.getEmbedding().isEmpty()) continue;
            float[] autoVec = parseEmbedding(autoFace.getEmbedding());
            if (autoVec == null) continue;

            // 计算与已移除人脸的相似度
            double simToRemoved = cosine(autoVec, removedVec);
            
            // 计算与剩余已确认人脸平均向量的相似度
            double simToRemaining = cosine(autoVec, avgVec);

            // 如果与已移除人脸的相似度 >= 0.75，但与剩余已确认人脸的相似度 < 0.6，则解绑
            // 说明这个自动分配的人脸主要是基于已移除的人脸分配的
            if (simToRemoved >= 0.75 && simToRemaining < 0.6) {
                autoFace.setPerson(null);
                autoFace.setIsConfirmed(false);
                faceRepository.save(autoFace);
                log.debug("已自动解绑人脸 {}（只与已移除人脸相似，相似度={}，与剩余人脸相似度={}）", 
                    autoFace.getId(), String.format("%.2f", simToRemoved), String.format("%.2f", simToRemaining));
            }
        }
    }

    /**
     * 将人脸绑定到人物（兼容旧接口，默认为已确认）
     */
    @Transactional
    public FaceDTO assignFaceToPerson(Long faceId, Long personId) {
        return assignFaceToPerson(faceId, personId, true); // 默认已确认
    }

    @Transactional(readOnly = true)
    public Page<FaceDTO> listUnassignedFaces(Pageable pageable, String sort) {
        // 根据sort参数创建排序
        Sort sortObj;
        if ("confidence".equals(sort)) {
            sortObj = Sort.by(Sort.Direction.DESC, "confidence");
        } else {
            sortObj = Sort.unsorted();
        }

        // 合并分页和排序
        Pageable pageableWithSort = PageRequest.of(
            pageable.getPageNumber(),
            Math.min(pageable.getPageSize(), 100), // 限制最多100项
            sortObj
        );

        return faceRepository.findByPersonIsNull(pageableWithSort).map(this::toDTO);
    }

    /**
     * 获取与指定人物相似的未分配人脸（相似度在0.4-0.6之间的）
     */
    public Page<FaceDTO> listUnassignedFacesForPerson(Long personId, Pageable pageable) {
        // 使用缓存获取相似人脸
        List<CachedFaceSimilarity> cached = getCachedSimilarities(personId);
        if (cached == null) {
            cached = computeAndCacheSimilarities(personId);
        }

        // 过滤并转换为 DTO（筛选低相似度）
        List<CachedFaceSimilarity> candidates = new ArrayList<>();
        for (CachedFaceSimilarity cs : cached) {
            // 相似度<50% 且不在同文件夹的
            if (cs.similarity < 0.5 && !cs.sameFolder) {
                candidates.add(cs);
            }
        }

        // 按相似度降序排序
        candidates.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        // 限制总数最多100个
        if (candidates.size() > 100) {
            candidates = candidates.subList(0, 100);
        }

        // 手动分页
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), candidates.size());
        List<CachedFaceSimilarity> pageContent = candidates.subList(start, end);

        return new PageImpl<>(
            pageContent.stream().map(cs -> {
                Face face = faceRepository.findById(cs.faceId).orElse(null);
                if (face == null) return null;
                FaceDTO dto = toDTO(face);
                dto.setSimilarity(cs.similarity);
                return dto;
            }).filter(Objects::nonNull).collect(Collectors.toList()),
            pageable,
            candidates.size()
        );
    }

    /**
     * 辅助类，用于存储人脸和相似度
     */
    private static class FaceSimilarity {
        Face face;
        double similarity;

        FaceSimilarity(Face face, double similarity) {
            this.face = face;
            this.similarity = similarity;
        }
    }

    /**
     * 获取与指定聚类相似的未分配人脸（相似度在0.4-0.6之间的）
     */
    public Page<FaceDTO> listUnassignedFacesForCluster(Integer clusterIndex, Pageable pageable) {
        // 获取聚类中的人脸
        List<FaceDTO> clusterFaces = getClusterFaces(clusterIndex, 0.6);
        if (clusterFaces.isEmpty()) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }

        // 获取聚类中所有人脸的ID集合
        Set<Long> clusterFaceIds = clusterFaces.stream()
            .map(FaceDTO::getId)
            .collect(Collectors.toSet());

        // 获取聚类中的Face实体
        List<Face> clusterFaceEntities = clusterFaces.stream()
            .map(faceDto -> faceRepository.findById(faceDto.getId()).orElse(null))
            .filter(face -> face != null)
            .collect(Collectors.toList());

        // 计算聚类平均特征向量
        float[] clusterAvgEmbedding = calculateAverageEmbeddingFromEntities(clusterFaceEntities);
        if (clusterAvgEmbedding == null) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }

        // 获取所有未分配人脸，计算相似度
        List<Face> allUnassigned = faceRepository.findByPersonIsNull();
        List<FaceSimilarity> candidates = new ArrayList<>();

        for (Face face : allUnassigned) {
            if (clusterFaceIds.contains(face.getId())) continue; // 排除已在聚类中的

            if (face.getEmbedding() == null || face.getEmbedding().isEmpty()) continue;
            float[] faceEmbedding = parseEmbedding(face.getEmbedding());
            if (faceEmbedding == null) continue;

            double similarity = cosine(clusterAvgEmbedding, faceEmbedding);
            if (similarity >= 0.4 && similarity < 0.6) { // 相似度在0.4-0.6之间的
                candidates.add(new FaceSimilarity(face, similarity));
            }
        }

        // 按相似度降序排序
        candidates.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        // 限制总数最多100个
        if (candidates.size() > 100) {
            candidates = candidates.subList(0, 100);
        }

        // 手动分页
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), candidates.size());
        List<FaceSimilarity> pageContent = candidates.subList(start, end);

        return new PageImpl<>(
            pageContent.stream().map(fs -> toDTO(fs.face)).collect(Collectors.toList()),
            pageable,
            candidates.size()
        );
    }

    @Transactional(readOnly = true)
    public Page<FaceDTO> listAssignedFaces(Pageable pageable) {
        return faceRepository.findByPersonIsNotNull(pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<FaceDTO> listPersonFaces(Long personId, Pageable pageable) {
        return faceRepository.findByPersonIdOrderByPhotoTimeDesc(personId, pageable).map(this::toDTO);
    }

    /**
     * 获取人物代表照片（用于封面显示）
     * 逻辑：按相册时间倒序，每个相册取一张（点赞 > 评分 > 创建时间）
     * 如果相册不足4个，继续在剩余照片中按同样逻辑选取
     */
    @Transactional(readOnly = true)
    public List<FaceDTO> getPersonSamplePhotos(Long personId) {
        // 获取该人物所在的所有相册ID（按相册ID倒序）
        List<Long> albumIds = faceRepository.findDistinctAlbumIdsByPersonId(personId);
        List<FaceDTO> result = new ArrayList<>();
        Set<Long> usedPhotoIds = new HashSet<>();

        // 每个相册取一张最优照片
        for (Long albumId : albumIds) {
            if (result.size() >= 4) break;

            List<Face> faces = faceRepository.findBestFaceByPersonAndAlbum(personId, albumId);
            for (Face face : faces) {
                if (face.getPhoto() != null && !usedPhotoIds.contains(face.getPhoto().getId())) {
                    FaceDTO dto = toDTO(face);
                    // 确保返回完整的Photo信息（使用相对路径）
                    if (face.getPhoto() != null) {
                        dto.setPhotoFilename(face.getPhoto().getFilename());
                        dto.setPhotoThumbnailPath(convertToRelativePath(face.getPhoto().getMediumThumbPath()));
                        dto.setPhotoOriginalPath(convertToRelativePath(face.getPhoto().getOriginalPath()));
                        dto.setPhotoWidth(face.getPhoto().getWidth());
                        dto.setPhotoHeight(face.getPhoto().getHeight());
                    }
                    result.add(dto);
                    usedPhotoIds.add(face.getPhoto().getId());
                    break; // 每个相册只取一张
                }
            }
        }

        // 如果相册不足4个，继续在剩余照片中按同样逻辑选取
        if (result.size() < 4) {
            // 获取该人物的所有照片，按点赞 > 评分 > 创建时间排序
            List<Face> allFaces = faceRepository.findByPersonIdAndIsConfirmed(personId, true);
            // 合并已确认和未确认的照片
            allFaces.addAll(faceRepository.findByPersonIdAndIsConfirmed(personId, false));

            // 按点赞 > 评分 > 创建时间排序
            allFaces.sort((f1, f2) -> {
                Photo p1 = f1.getPhoto();
                Photo p2 = f2.getPhoto();
                if (p1 == null || p2 == null) return 0;

                int likeCompare = Integer.compare(p2.getLikeCount() != null ? p2.getLikeCount() : 0,
                        p1.getLikeCount() != null ? p1.getLikeCount() : 0);
                if (likeCompare != 0) return likeCompare;

                double scoreCompare = Double.compare(
                        p2.getQualityScore() != null ? p2.getQualityScore() : 0,
                        p1.getQualityScore() != null ? p1.getQualityScore() : 0);
                if (scoreCompare != 0) return scoreCompare > 0 ? 1 : -1;

                return p2.getCreatedAt().compareTo(p1.getCreatedAt());
            });

            for (Face face : allFaces) {
                if (result.size() >= 4) break;
                if (face.getPhoto() != null && !usedPhotoIds.contains(face.getPhoto().getId())) {
                    FaceDTO dto = toDTO(face);
                    if (face.getPhoto() != null) {
                        dto.setPhotoFilename(face.getPhoto().getFilename());
                        dto.setPhotoThumbnailPath(convertToRelativePath(face.getPhoto().getMediumThumbPath()));
                        dto.setPhotoOriginalPath(convertToRelativePath(face.getPhoto().getOriginalPath()));
                        dto.setPhotoWidth(face.getPhoto().getWidth());
                        dto.setPhotoHeight(face.getPhoto().getHeight());
                    }
                    result.add(dto);
                    usedPhotoIds.add(face.getPhoto().getId());
                }
            }
        }

        return result;
    }

    /**
     * 获取已确认的人脸（用户手动确认的）
     */
    @Transactional(readOnly = true)
    public Page<FaceDTO> listConfirmedFaces(Long personId, Pageable pageable) {
        return faceRepository.findByPersonIdAndIsConfirmed(personId, true, pageable).map(this::toDTO);
    }

    /**
     * 获取自动分配的人脸（未确认但已分配）
     */
    @Transactional(readOnly = true)
    public Page<FaceDTO> listAutoAssignedFaces(Long personId, Pageable pageable) {
        return faceRepository.findByPersonIdAndIsConfirmed(personId, false, pageable).map(this::toDTO);
    }

    /**
     * 获取同一文件夹的相似人脸（套图推荐）
     */
    @Transactional(readOnly = true)
    public List<FaceDTO> listSameFolderSimilarFaces(Long personId, int top) {
        // 获取该人物的已确认人脸和自动分配人脸，一起作为套图推荐的参考基准
        List<Face> confirmedFaces = faceRepository
                .findByPersonIdAndIsConfirmed(personId, true, PageRequest.of(0, 10))
                .getContent();
        List<Face> autoAssignedFaces = faceRepository
                .findByPersonIdAndIsConfirmed(personId, false, PageRequest.of(0, 20))
                .getContent();

        List<Face> referenceFaces = new ArrayList<>();
        referenceFaces.addAll(confirmedFaces);
        referenceFaces.addAll(autoAssignedFaces);

        if (referenceFaces.isEmpty()) {
            return new ArrayList<>();
        }

        // 构建路径前缀层级：同文件夹、上一级、再上一级（最多到 base-path 下两级：category/album）
        Set<String> folderPrefixes = new HashSet<>();
        for (Face ref : referenceFaces) {
            String path = ref.getPhoto() != null ? ref.getPhoto().getOriginalPath() : null;
            folderPrefixes.addAll(buildFolderPrefixes(path));
        }
        if (folderPrefixes.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取所有与这些人物照片同相册下的未分配人脸（含 embedding），避免全表扫描
        Set<Long> albumIds = referenceFaces.stream()
                .filter(f -> f.getPhoto() != null && f.getPhoto().getAlbumId() != null)
                .map(f -> f.getPhoto().getAlbumId())
                .collect(Collectors.toSet());
        if (albumIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Face> unassignedFaces = faceRepository.findByPersonIsNullAndPhotoAlbumIdIn(albumIds);
        if (unassignedFaces.isEmpty()) {
            return new ArrayList<>();
        }

        // 计算与已确认人脸的相似度，并按路径层级应用分层阈值
        List<FaceDTO> result = new ArrayList<>();
        for (Face candidate : unassignedFaces) {
            if (candidate.getEmbedding() == null || candidate.getEmbedding().isEmpty()) continue;
            String upath = candidate.getPhoto() != null ? candidate.getPhoto().getOriginalPath() : null;
            FolderScope scope = matchFolderScope(upath, folderPrefixes);
            if (scope == FolderScope.NONE) continue; // 不在同目录相关范围内
            
            float[] unassignedVec = parseEmbedding(candidate.getEmbedding());
            if (unassignedVec == null) continue;

            double maxSim = -1;
            for (Face ref : referenceFaces) {
                if (ref.getEmbedding() == null || ref.getEmbedding().isEmpty()) continue;
                float[] refVec = parseEmbedding(ref.getEmbedding());
                if (refVec == null) continue;
                
                double sim = cosine(unassignedVec, refVec);
                if (sim > maxSim) {
                    maxSim = sim;
                }
            }

            // 分层阈值：同目录放宽，越向上阈值越宽但不低于0.46，仍限制上限避免与相似推荐重叠
            double lower = 0.50;
            if (scope == FolderScope.PARENT) {
                lower = 0.48;
            } else if (scope == FolderScope.GRAND) {
                lower = 0.46;
            }
            double upper = 0.6; // 保持与相似推荐区间不重叠

            if (maxSim >= lower && maxSim < upper) {
                FaceDTO dto = toDTO(candidate);
                dto.setSimilarity(maxSim);
                result.add(dto);
            }
        }

        result.sort((a, b) -> Double.compare(
            b.getSimilarity() != null ? b.getSimilarity() : 0,
            a.getSimilarity() != null ? a.getSimilarity() : 0
        ));
        
        if (result.size() > top) {
            return result.subList(0, top);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Page<PersonSummaryDTO> listPersonsWithSample(Pageable pageable) {
        // 使用自定义分页查询，按 faceCount 倒序排序
        Page<PersonProfile> personPage = personProfileRepository.findAllOrderByFaceCountDesc(pageable);

        // 转换为 DTO
        List<PersonSummaryDTO> persons = personPage.getContent().stream()
            .map(this::toSummaryDTO)
            .collect(Collectors.toList());

        return new PageImpl<>(persons, pageable, personPage.getTotalElements());
    }

    /**
     * 获取统一的人物列表（包括已确认人物和未确认聚类）
     */
    @Transactional(readOnly = true)
    public List<PersonListItemDTO> listPersonItems(double clusterThreshold, int clusterPage, int clusterSize) {
        List<PersonListItemDTO> items = new ArrayList<>();

        // 1. 已确认的人物
        List<PersonProfile> confirmedPersons = personProfileRepository.findAll();
        for (PersonProfile person : confirmedPersons) {
            PersonListItemDTO item = new PersonListItemDTO();
            item.setType("confirmed");
            item.setId(person.getId());
            item.setName(person.getName());
            item.setDescription(person.getDescription());
            item.setCreatedAt(person.getCreatedAt());
            item.setUpdatedAt(person.getUpdatedAt());

            // 获取该人物的人脸数量
            long faceCount = faceRepository.findByPersonId(person.getId(), PageRequest.of(0, 1))
                .getTotalElements();
            item.setFaceCount((int) faceCount);

            // 使用统一的头像获取逻辑（优先已设置，fallback到动态计算）
            Object[] sampleData = getPersonSamplePhoto(person.getId());
            if (sampleData[0] != null) {
                item.setSampleFaceId((Long) sampleData[0]);
                item.setSamplePhotoId((Long) sampleData[1]);
                item.setSampleThumbnailPath((String) sampleData[2]);
                item.setSampleOriginalPath((String) sampleData[3]);
                item.setSampleConfidence((Double) sampleData[4]);
            }

            items.add(item);
        }

        // 2. 未确认的聚类（支持分页）
        List<FaceClusterDTO> allClusters = clusterSimilarFaces(clusterThreshold);

        // 计算分页的聚类
        int clusterStartIndex = clusterPage * clusterSize;
        int clusterEndIndex = Math.min(clusterStartIndex + clusterSize, allClusters.size());
        List<FaceClusterDTO> pageClusters = allClusters.subList(clusterStartIndex, clusterEndIndex);

        for (int i = 0; i < pageClusters.size(); i++) {
            FaceClusterDTO clusterDTO = pageClusters.get(i);
            // 使用全局索引（clusterPage * clusterSize + i）作为ID，确保分页后ID唯一
            int globalIndex = clusterStartIndex + i;

            PersonListItemDTO item = new PersonListItemDTO();
            item.setType("cluster");
            item.setId((long) globalIndex); // 使用全局索引作为ID，确保分页后ID唯一
            item.setName("未命名");
            item.setDescription("自动聚合的相似人脸，尚未确认");
            item.setFaceCount(clusterDTO.getCount());
            item.setAvgConfidence(clusterDTO.getAvgConfidence());

            // 获取代表脸
            if (clusterDTO.getRepresentativeFaceId() != null) {
                Face sample = faceRepository.findById(clusterDTO.getRepresentativeFaceId()).orElse(null);
                if (sample != null && sample.getPhoto() != null) {
                    item.setSampleFaceId(sample.getId());
                    item.setSamplePhotoId(sample.getPhoto().getId());
                    item.setSampleConfidence(sample.getConfidence());
                    item.setSampleThumbnailPath(convertToRelativePath(sample.getPhoto().getThumbnailPath()));
                    item.setSampleOriginalPath(convertToRelativePath(sample.getPhoto().getOriginalPath()));
                }
            }

            items.add(item);
        }

        // 按创建时间降序排序（已确认人物在前，聚类在后）
        items.sort((a, b) -> {
            if ("confirmed".equals(a.getType()) && "cluster".equals(b.getType())) {
                return -1;
            }
            if ("cluster".equals(a.getType()) && "confirmed".equals(b.getType())) {
                return 1;
            }
            if (a.getCreatedAt() != null && b.getCreatedAt() != null) {
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            }
            return 0;
        });

        return items;
    }

    /**
     * 自动分配相似人脸（高相似度，自动分配但不确认）
     */
    @Transactional
    public List<FaceDTO> autoAssignSimilarFaces(Long personId, double threshold) {
        List<FaceDTO> similar = findSimilarUnassignedFaces(personId, 100, threshold);
        // 自动分配相似度 >= 0.75 的人脸
        for (FaceDTO face : similar) {
            if (face.getSimilarity() != null && face.getSimilarity() >= 0.75) {
                assignFaceToPerson(face.getId(), personId, false); // 自动分配，不确认
            }
        }
        return similar.stream()
            .filter(f -> f.getSimilarity() != null && f.getSimilarity() >= 0.75)
            .collect(Collectors.toList());
    }

    /**
     * 获取与指定人物相似但未分配的人脸
     * 改进：使用该人物所有人脸的平均向量作为基准，提高准确率
     */
    /**
     * 检查两张照片是否在同一个文件夹内
     */
    private boolean isSameFolder(String path1, String path2) {
        if (path1 == null || path2 == null) return false;

        // 获取父目录路径
        java.nio.file.Path p1 = java.nio.file.Paths.get(path1).getParent();
        java.nio.file.Path p2 = java.nio.file.Paths.get(path2).getParent();

        return p1 != null && p2 != null && p1.equals(p2);
    }

    @Transactional(readOnly = true)
    public List<FaceDTO> findSimilarUnassignedFaces(Long personId, int top, double threshold) {
        // 使用缓存获取相似人脸
        List<CachedFaceSimilarity> cached = getCachedSimilarities(personId);
        if (cached == null) {
            cached = computeAndCacheSimilarities(personId);
        }

        // 过滤并转换为 DTO（筛选高相似度）
        List<FaceDTO> result = new ArrayList<>();
        for (CachedFaceSimilarity cs : cached) {
            if (cs.similarity >= 0.5) {
                Face face = faceRepository.findById(cs.faceId).orElse(null);
                if (face != null) {
                    FaceDTO dto = toDTO(face);
                    dto.setSimilarity(cs.similarity);
                    result.add(dto);
                }
            }
        }

        result.sort((a, b) -> Double.compare(
            b.getSimilarity() != null ? b.getSimilarity() : 0,
            a.getSimilarity() != null ? a.getSimilarity() : 0
        ));

        if (result.size() > top) {
            return result.subList(0, top);
        }
        return result;
    }

    /**
     * 获取聚类中的人脸列表
     */
    @Transactional(readOnly = true)
    public List<FaceDTO> getClusterFaces(int clusterIndex, double threshold) {
        List<FaceClusterDTO> clusters = clusterSimilarFaces(threshold);
        if (clusterIndex < 0 || clusterIndex >= clusters.size()) {
            return new ArrayList<>();
        }
        return clusters.get(clusterIndex).getFaces();
    }

    /**
     * 自动聚合相似人脸（基于embedding相似度）
     * 完全重写的保守聚类算法，优先保证准确率
     * 核心策略：
     * 1. 使用更高的基础阈值（0.78-0.82）
     * 2. 新成员必须与组内至少3个已有人脸都相似（多点验证）
     * 3. 要求与组内所有成员的平均相似度都要高（避免链式错误）
     * 4. 最小样本约束：只有组内至少有3个人脸时，才允许新成员加入
     * 5. 使用统计验证（中位数、75分位数）确保一致性
     * @param threshold 相似度阈值，建议0.75-0.80（更保守）
     * @return 聚类结果列表
     */
    @Transactional(readOnly = true)
    public List<FaceClusterDTO> clusterSimilarFaces(double threshold) {
        // 获取所有未分配且质量合格的人脸
        List<Face> unassigned = faceRepository.findByPersonIsNull();
        List<Face> withEmbedding = unassigned.stream()
            .filter(this::isValidForClustering)
            .collect(Collectors.toList());

        if (withEmbedding.isEmpty()) {
            return new ArrayList<>();
        }

        // 人脸聚类处理所有质量合格的人脸，后续有照片匹配补全步骤

        // 聚类阈值可配置：在用户阈值基础上加偏移，并设定可配置下限
        final double strictThreshold = Math.max(threshold + clusteringThresholdBonus, clusteringMinThreshold);

        // 尝试从缓存命中
        double cacheKey = roundThreshold(threshold);
        List<FaceClusterDTO> cached = clusterCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        List<List<Face>> groups = new ArrayList<>();

        // 按置信度降序排序，优先处理高质量人脸
        withEmbedding.sort((a, b) -> Double.compare(
            (b.getConfidence() != null ? b.getConfidence() : 0.0),
            (a.getConfidence() != null ? a.getConfidence() : 0.0)
        ));

        // 预计算组的代表向量，用于快速预过滤
        List<float[]> groupRepresentatives = new ArrayList<>();

        for (Face candidate : withEmbedding) {
            float[] candidateVec = parseEmbedding(candidate.getEmbedding());
            if (candidateVec == null) continue;

            Integer bestGroup = null;
            double bestAvgSimilarity = -1;

            // 性能优化：预过滤机制，只检查最有可能匹配的前N个组
            final int MAX_GROUPS_TO_CHECK = Math.min(10, groups.size()); // 最多检查10个最有可能的组
            List<GroupSimilarity> groupSimilarities = new ArrayList<>();

            // 计算与各组代表向量的相似度，用于预过滤
            for (int g = 0; g < groups.size(); g++) {
                if (groupRepresentatives.size() <= g) {
                    // 计算组的代表向量（取第一个成员的向量作为代表）
                    List<Face> group = groups.get(g);
                    if (!group.isEmpty()) {
                        float[] repVec = parseEmbedding(group.get(0).getEmbedding());
                        groupRepresentatives.add(repVec != null ? repVec : new float[0]);
                    } else {
                        groupRepresentatives.add(new float[0]);
                    }
                }

                float[] repVec = groupRepresentatives.get(g);
                if (repVec.length > 0) {
                    double repSimilarity = cosine(candidateVec, repVec);
                    groupSimilarities.add(new GroupSimilarity(g, repSimilarity));
                }
            }

            // 按相似度排序，只检查最有可能的组
            groupSimilarities.sort((a, b) -> Double.compare(b.similarity, a.similarity));
            List<Integer> groupsToCheck = groupSimilarities.stream()
                .limit(MAX_GROUPS_TO_CHECK)
                .map(gs -> gs.groupIndex)
                .collect(Collectors.toList());

            // 检查筛选出的组
            for (int g : groupsToCheck) {
                List<Face> group = groups.get(g);

                // 多点验证：计算与组内所有成员的相似度
                List<Double> similarities = new ArrayList<>();
                int matchCount = 0;
                
                for (Face member : group) {
                    float[] memberVec = parseEmbedding(member.getEmbedding());
                    if (memberVec == null) continue;
                    
                    double sim = cosine(candidateVec, memberVec);
                    similarities.add(sim);
                    
                    // 统计达到阈值的匹配数
                    if (sim >= strictThreshold) {
                        matchCount++;
                    }
                }

                if (similarities.isEmpty()) continue;

                // 计算统计指标
                double avgSim = similarities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double medianSim = quantile(similarities, 0.5);
                double q75Sim = quantile(similarities, 0.75);
                double minSim = similarities.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

                boolean canJoin = false;

                if (group.size() < clusteringMinGroupSize) {
                    // 小组：可配置是否使用严格匹配
                    if (clusteringSmallGroupStrict) {
                        canJoin = matchCount == group.size() && avgSim >= strictThreshold && minSim >= (strictThreshold - clusteringMinSimSlackSmall);
                    } else {
                        // 宽松模式：只要平均相似度达标即可
                        canJoin = avgSim >= strictThreshold || minSim >= (strictThreshold - clusteringMinSimSlackSmall);
                    }
                } else {
                    // 大组（3个或以上人脸）：使用多点验证和统计指标
                    int requiredMatches = Math.min(clusteringMinMatches, group.size());
                    boolean passMultiPoint = !clusteringRequireMultiPoint || matchCount >= requiredMatches;
                    if (!clusteringRequireStats) {
                        canJoin = passMultiPoint && avgSim >= strictThreshold;
                    } else {
                        boolean passAvg = avgSim >= strictThreshold;
                        boolean passMedian = medianSim >= strictThreshold;
                        boolean passQ75 = q75Sim >= strictThreshold;
                        boolean passMin = minSim >= (strictThreshold - clusteringMinSimSlackLarge);
                        canJoin = passMultiPoint && passAvg && passMedian && passQ75 && passMin;
                    }
                }

                if (canJoin) {
                    // 选择平均相似度最高的组
                    if (avgSim > bestAvgSimilarity) {
                        bestAvgSimilarity = avgSim;
                        bestGroup = g;
                    }
                }
            }

            if (bestGroup != null) {
                // 加入现有组
                groups.get(bestGroup).add(candidate);
            } else {
                // 创建新组（只有无法加入任何现有组时才创建）
                List<Face> newGroup = new ArrayList<>();
                newGroup.add(candidate);
                groups.add(newGroup);
            }
        }

        // 后处理：对每个组进行离群值清理
        List<FaceClusterDTO> clusters = new ArrayList<>();
        for (List<Face> group : groups) {
            if (group.size() < 1) continue;

            // 计算组中心向量
            float[] centroid = updateCentroid(group);
            if (centroid == null) continue;

            // 离群值清理：移除与中心向量相似度低于阈值的人脸
            List<Face> cleanedGroup = new ArrayList<>();
            for (Face face : group) {
                float[] vec = parseEmbedding(face.getEmbedding());
                if (vec == null) continue;
                double sim = cosine(centroid, vec);
                // 使用稍微宽松的阈值进行清理（比加入阈值低0.02）
                if (sim >= (strictThreshold - 0.02)) {
                    cleanedGroup.add(face);
                }
            }

            if (cleanedGroup.isEmpty()) {
                continue;
            }

            // 转换为DTO
            FaceClusterDTO cluster = new FaceClusterDTO();
            List<FaceDTO> faceDTOs = cleanedGroup.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
            cluster.setFaces(faceDTOs);
            cluster.setCount(cleanedGroup.size());

            // 计算平均置信度并找到代表脸
            double sumConf = 0;
            Face bestFace = null;
            double bestConf = -1;
            for (Face f : cleanedGroup) {
                if (f.getConfidence() != null) {
                    sumConf += f.getConfidence();
                    if (f.getConfidence() > bestConf) {
                        bestConf = f.getConfidence();
                        bestFace = f;
                    }
                }
            }
            cluster.setAvgConfidence(cleanedGroup.size() > 0 ? sumConf / cleanedGroup.size() : null);
            cluster.setRepresentativeFaceId(bestFace != null ? bestFace.getId() : cleanedGroup.get(0).getId());

            clusters.add(cluster);
        }

        // 按人脸数量降序排序
        clusters.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));

        log.info("保守聚类完成: 共 {} 个未分配人脸，聚合成 {} 个组（用户阈值: {}，实际阈值: {}）", 
            withEmbedding.size(), clusters.size(), threshold, strictThreshold);
        // 写入缓存（不可变拷贝）
        clusterCache.put(cacheKey, new ArrayList<>(clusters));
        return clusters;
    }

    private double roundThreshold(double threshold) {
        return Math.round(threshold * 1000.0) / 1000.0;
    }

    private void invalidateClusterCache() {
        clusterCache.clear();
        log.debug("聚类缓存已清空");
    }

    /**
     * 计算组内平均向量（中心向量）
     */
    private float[] updateCentroid(List<Face> group) {
        if (group.isEmpty()) return null;
        
        List<float[]> vectors = new ArrayList<>();
        for (Face face : group) {
            float[] vec = parseEmbedding(face.getEmbedding());
            if (vec != null) {
                vectors.add(vec);
            }
        }
        
        if (vectors.isEmpty()) return null;
        
        int dim = vectors.get(0).length;
        float[] centroid = new float[dim];
        
        // 计算平均值
        for (float[] vec : vectors) {
            for (int i = 0; i < dim; i++) {
                centroid[i] += vec[i];
            }
        }
        
        int count = vectors.size();
        for (int i = 0; i < dim; i++) {
            centroid[i] /= count;
        }
        
        // 归一化
        double norm = 0;
        for (float v : centroid) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-6) {
            for (int i = 0; i < dim; i++) {
                centroid[i] /= (float) norm;
            }
        }
        
        return centroid;
    }

    /**
     * 计算分位数（0-1）
     */
    private double quantile(List<Double> values, double q) {
        if (values == null || values.isEmpty()) return 0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        double pos = q * (sorted.size() - 1);
        int idx = (int) pos;
        double frac = pos - idx;
        if (idx + 1 < sorted.size()) {
            return sorted.get(idx) * (1 - frac) + sorted.get(idx + 1) * frac;
        }
        return sorted.get(idx);
    }

    /**
     * 目录层级枚举，用于分层阈值
     */
    private enum FolderScope {
        SAME, PARENT, GRAND, NONE
    }

    /**
     * 构建最多三层的目录前缀：同目录、父目录、祖父目录（不超过 base-path 下的第二层，如 category/album）
     */
    private Set<String> buildFolderPrefixes(String originalPath) {
        Set<String> prefixes = new HashSet<>();
        if (originalPath == null || originalPath.isEmpty()) return prefixes;

        try {
            String base = Paths.get(photoBasePath).normalize().toString();
            String normalized = Paths.get(originalPath).normalize().toString();
            // 去掉 base 前缀
            if (normalized.startsWith(base)) {
                normalized = normalized.substring(base.length());
            }
            normalized = normalized.replace('\\', '/');
            if (normalized.startsWith("/")) normalized = normalized.substring(1);
            String[] parts = normalized.split("/");
            if (parts.length < 3) {
                // 不足 category/album/xxx 时，仅用当前目录
                String current = normalized.contains("/") ? normalized.substring(0, normalized.lastIndexOf('/')) : normalized;
                if (!current.isEmpty()) prefixes.add(current);
                return prefixes;
            }

            // parts: [category, album, sub1, sub2...]
            String category = parts[0];
            String album = parts[1];

            // 同目录
            String current = normalized.contains("/") ? normalized.substring(0, normalized.lastIndexOf('/')) : normalized;
            if (!current.isEmpty()) prefixes.add(current);

            // 父目录（不高于 album 层）
            if (parts.length >= 3) {
                String parent = String.join("/", Arrays.copyOfRange(parts, 0, parts.length - 1));
                // 限制不超过 category/album
                String albumLevel = category + "/" + album;
                if (parent.startsWith(albumLevel)) {
                    prefixes.add(parent);
                } else {
                    prefixes.add(albumLevel);
                }
            }

            // 祖父目录：限制到 album 层
            prefixes.add(category + "/" + album);
        } catch (Exception e) {
            log.debug("构建目录前缀失败: path={}, err={}", originalPath, e.getMessage());
        }
        return prefixes;
    }

    /**
     * 匹配当前路径与前缀集合的层级
     */
    private FolderScope matchFolderScope(String originalPath, Set<String> prefixes) {
        if (originalPath == null || originalPath.isEmpty() || prefixes == null || prefixes.isEmpty()) {
            return FolderScope.NONE;
        }
        try {
            String normalized = Paths.get(originalPath).normalize().toString().replace('\\', '/');
            if (normalized.startsWith(photoBasePath)) {
                normalized = normalized.substring(Paths.get(photoBasePath).normalize().toString().length());
            }
            if (normalized.startsWith("/")) normalized = normalized.substring(1);

            // 构造当前目录层级链
            List<String> chain = new ArrayList<>();
            if (normalized.contains("/")) {
                String current = normalized.substring(0, normalized.lastIndexOf('/'));
                chain.add(current); // same
                int lastSlash = current.lastIndexOf('/');
                if (lastSlash > 0) {
                    chain.add(current.substring(0, lastSlash)); // parent
                    int second = current.substring(0, lastSlash).lastIndexOf('/');
                    if (second > 0) {
                        chain.add(current.substring(0, second)); // grand
                    }
                }
            } else {
                chain.add(normalized);
            }

            if (!chain.isEmpty() && prefixes.contains(chain.get(0))) return FolderScope.SAME;
            if (chain.size() > 1 && prefixes.contains(chain.get(1))) return FolderScope.PARENT;
            if (chain.size() > 2 && prefixes.contains(chain.get(2))) return FolderScope.GRAND;
        } catch (Exception e) {
            log.debug("匹配目录前缀失败: path={}, err={}", originalPath, e.getMessage());
        }
        return FolderScope.NONE;
    }

    /**
     * 判断人脸是否适合参与聚类：需要有向量、框形合理、置信度不太低
     */
    private boolean isValidForClustering(Face face) {
        if (face == null) return false;
        if (face.getEmbedding() == null || face.getEmbedding().isEmpty()) return false;

        // 框大小与比例过滤，避免极小或畸形框
        if (face.getWidth() != null && face.getHeight() != null) {
            double w = face.getWidth();
            double h = face.getHeight();
            double area = w * h;
            if (w <= 0 || h <= 0) return false;
            if (area < 0.01) return false; // 极小框
            double ratio = w / h;
            if (ratio < 0.45 || ratio > 2.2) return false; // 畸形比例
        }

        // 置信度过低的向量可能是噪声
        if (face.getConfidence() != null && face.getConfidence() < 0.5) {
            return false;
        }

        return true;
    }

    /**
     * 批量创建人物并绑定人脸
     * @param faceIds 人脸ID列表
     * @param personName 人物名称
     * @param description 人物描述
     * @return 创建的人物DTO
     */
    @Transactional
    public PersonDTO createPersonFromFaces(List<Long> faceIds, String personName, String description) {
        if (faceIds == null || faceIds.isEmpty()) {
            throw new RuntimeException("人脸ID列表不能为空");
        }
        if (personName == null || personName.trim().isEmpty()) {
            throw new RuntimeException("人物名称不能为空");
        }

        // 查找或创建人物
        PersonProfile person = personProfileRepository.findByName(personName.trim())
            .orElseGet(() -> {
                PersonProfile p = new PersonProfile();
                p.setName(personName.trim());
                p.setDescription(description);
                return personProfileRepository.save(p);
            });

        if (description != null && !description.trim().isEmpty()) {
            person.setDescription(description.trim());
            personProfileRepository.save(person);
        }

        // 绑定所有人脸（创建时直接设为已确认，跳过自动分配步骤）
        for (Long faceId : faceIds) {
            Face face = faceRepository.findById(faceId)
                .orElseThrow(() -> new RuntimeException("人脸不存在: " + faceId));
            face.setPerson(person);
            face.setIsConfirmed(true); // 创建时直接设为已确认
            faceRepository.save(face);
        }

        log.info("创建人物并绑定人脸: personId={}, name={}, faceCount={}", person.getId(), person.getName(), faceIds.size());
        invalidateClusterCache();
        return toDTO(person);
    }

    /**
     * 查找相似人脸
     */
    @Transactional(readOnly = true)
    public List<FaceDTO> findSimilarFaces(Long faceId, int top, double threshold) {
        Face base = faceRepository.findById(faceId).orElseThrow(() -> new RuntimeException("人脸不存在"));
        float[] baseVec = parseEmbedding(base.getEmbedding());
        if (baseVec == null) return List.of();

        // 关键优化：只遍历有 embedding 的人脸，避免全表扫描 + 大量 parseEmbedding(null/空)
        List<Face> all = faceRepository.findAllWithEmbedding();
        List<FaceDTO> result = new ArrayList<>();
        for (Face f : all) {
            if (f.getId().equals(faceId)) continue;
            float[] vec = parseEmbedding(f.getEmbedding());
            if (vec == null) continue;
            double sim = cosine(baseVec, vec);
            if (sim >= threshold) {
                FaceDTO dto = toDTO(f);
                dto.setSimilarity(sim);
                result.add(dto);
            }
        }

        result.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        if (result.size() > top) {
            return result.subList(0, top);
        }
        return result;
    }

    /**
     * 更新人脸对应的人员信息（若名称不存在则创建）
     */
    @Transactional
    public FaceDTO updateFacePerson(Long faceId, String name, String description) {
        Face face = faceRepository.findById(faceId)
            .orElseThrow(() -> new RuntimeException("人脸不存在"));

        if (name == null || name.trim().isEmpty()) {
            face.setPerson(null);
        } else {
            PersonProfile person = personProfileRepository.findByName(name.trim())
                .orElseGet(() -> {
                    PersonProfile p = new PersonProfile();
                    p.setName(name.trim());
                    p.setDescription(description);
                    return personProfileRepository.save(p);
                });

            if (description != null && !description.trim().isEmpty()) {
                person.setDescription(description.trim());
                personProfileRepository.save(person);
            }
            face.setPerson(person);
        }

        return toDTO(faceRepository.save(face));
    }

    @Transactional(readOnly = true)
    public List<PersonDTO> listPersons() {
        return personProfileRepository.findAll().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * 获取指定相册中的人物列表（按人脸数量倒序）
     */
    @Transactional(readOnly = true)
    public List<PersonSummaryDTO> getPersonsInAlbum(Long albumId) {
        List<Object[]> rows = faceRepository.findPersonIdsWithFaceCountByAlbumId(albumId);
        List<PersonSummaryDTO> result = new ArrayList<>();

        for (Object[] row : rows) {
            Long personId = ((Number) row[0]).longValue();
            Integer faceCount = ((Number) row[1]).intValue();

            // 获取人物信息
            PersonProfile person = personProfileRepository.findById(personId).orElse(null);
            if (person == null) continue;

            PersonSummaryDTO dto = new PersonSummaryDTO();
            dto.setId(person.getId());
            dto.setName(person.getName());
            dto.setDescription(person.getDescription());
            dto.setFaceCount(faceCount);

            // 获取代表缩略图
            Object[] sampleData = getPersonSamplePhoto(person.getId());
            if (sampleData[0] != null && sampleData[2] != null) {
                dto.setSampleFaceId((Long) sampleData[0]);
                dto.setSampleThumbnailPath((String) sampleData[2]);
            }

            result.add(dto);
        }

        return result;
    }

    @Transactional
    public PersonDTO createOrUpdatePerson(Long id, PersonDTO payload) {
        PersonProfile person;
        if (id != null) {
            person = personProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("人物不存在"));
        } else {
            Optional<PersonProfile> existing = personProfileRepository.findByName(payload.getName());
            person = existing.orElseGet(PersonProfile::new);
        }

        if (payload.getName() != null && !payload.getName().trim().isEmpty()) {
            person.setName(payload.getName().trim());
        } else if (person.getName() == null || person.getName().trim().isEmpty()) {
            person.setName("未命名人物");
        }
        person.setDescription(payload.getDescription());
        return toDTO(personProfileRepository.save(person));
    }

    private FaceDTO toDTO(Face face) {
        FaceDTO dto = new FaceDTO();
        dto.setId(face.getId());
        dto.setPhotoId(face.getPhoto() != null ? face.getPhoto().getId() : null);
        dto.setX(face.getX());
        dto.setY(face.getY());
        dto.setWidth(face.getWidth());
        dto.setHeight(face.getHeight());
        dto.setConfidence(face.getConfidence());
        dto.setIsConfirmed(face.getIsConfirmed() != null ? face.getIsConfirmed() : false);
        if (face.getPerson() != null) {
            dto.setPersonId(face.getPerson().getId());
            dto.setPersonName(face.getPerson().getName());
            dto.setPersonDescription(face.getPerson().getDescription());
        }
        if (face.getPhoto() != null) {
            dto.setPhotoId(face.getPhoto().getId());
            dto.setPhotoFilename(face.getPhoto().getFilename());
            dto.setPhotoThumbnailPath(convertToRelativePath(face.getPhoto().getMediumThumbPath()));
            dto.setPhotoMediumThumbPath(convertToRelativePath(face.getPhoto().getMediumThumbPath()));
            dto.setPhotoOriginalPath(convertToRelativePath(face.getPhoto().getOriginalPath()));
            dto.setPhotoWidth(face.getPhoto().getWidth());
            dto.setPhotoHeight(face.getPhoto().getHeight());
        }
        return dto;
    }

    private PersonDTO toDTO(PersonProfile person) {
        PersonDTO dto = new PersonDTO();
        dto.setId(person.getId());
        dto.setName(person.getName());
        dto.setDescription(person.getDescription());

        // 使用统一的头像获取逻辑（优先已设置，fallback到动态计算）
        Object[] sampleData = getPersonSamplePhoto(person.getId());
        if (sampleData[0] != null) {
            dto.setSampleFaceId((Long) sampleData[0]);
            dto.setSamplePhotoId((Long) sampleData[1]);
            dto.setSampleThumbnailPath((String) sampleData[2]);
            dto.setSampleOriginalPath((String) sampleData[3]);
            dto.setSampleConfidence((Double) sampleData[4]);
        }

        return dto;
    }

    /**
     * 设置人物的样例照片
     */
    @Transactional
    public PersonDTO setPersonSamplePhoto(Long personId, Long faceId) {
        PersonProfile person = personProfileRepository.findById(personId)
            .orElseThrow(() -> new RuntimeException("人物不存在"));
        Face face = faceRepository.findById(faceId)
            .orElseThrow(() -> new RuntimeException("人脸不存在"));

        person.setSamplePhotoId(face.getPhoto() != null ? face.getPhoto().getId() : null);
        person.setSampleFaceId(faceId);
        person.setSampleConfidence(face.getConfidence());
        if (face.getPhoto() != null) {
            person.setSampleThumbnailPath(face.getPhoto().getThumbnailPath());
            person.setSampleOriginalPath(face.getPhoto().getOriginalPath());
        }
        personProfileRepository.save(person);
        return toDTO(person);
    }

    /**
     * 获取人物的样例照片（统一逻辑）
     * 1. 优先返回已设置的样例照片
     * 2. 如果未设置或文件不存在，返回动态计算的最高置信度人脸
     * @return 包含 sampleFaceId, samplePhotoId, sampleThumbnailPath, sampleOriginalPath, sampleConfidence 的数组
     */
    private Object[] getPersonSamplePhoto(Long personId) {
        PersonProfile person = personProfileRepository.findById(personId).orElse(null);
        if (person == null) {
            return new Object[]{null, null, null, null, null};
        }

        // 1. 优先使用已设置的样例照片
        if (person.getSampleFaceId() != null && person.getSampleThumbnailPath() != null) {
            // 检查文件是否存在（这里简单判断路径是否为空，实际项目中可以检查文件是否存在）
            if (!person.getSampleThumbnailPath().isEmpty()) {
                return new Object[]{
                    person.getSampleFaceId(),
                    person.getSamplePhotoId(),
                    convertToRelativePath(person.getSampleThumbnailPath()),
                    convertToRelativePath(person.getSampleOriginalPath()),
                    person.getSampleConfidence()
                };
            }
        }

        // 2. 动态计算：取置信度最高的人脸
        Face sample = faceRepository.findTopByPersonIdOrderByConfidenceDescCreatedAtDesc(personId);
        if (sample == null) {
            sample = faceRepository.findTopByPersonIdOrderByCreatedAtDesc(personId);
        }

        if (sample != null && sample.getPhoto() != null) {
            return new Object[]{
                sample.getId(),
                sample.getPhoto().getId(),
                convertToRelativePath(sample.getPhoto().getThumbnailPath()),
                convertToRelativePath(sample.getPhoto().getOriginalPath()),
                sample.getConfidence()
            };
        }

        return new Object[]{null, null, null, null, null};
    }

    public PersonSummaryDTO toSummaryDTO(PersonProfile person) {
        PersonSummaryDTO dto = new PersonSummaryDTO();
        dto.setId(person.getId());
        dto.setName(person.getName());
        dto.setDescription(person.getDescription());
        dto.setCreatedAt(person.getCreatedAt());
        dto.setUpdatedAt(person.getUpdatedAt());

        // 计算人脸数量（即照片数量）
        long faceCount = faceRepository.findByPersonId(person.getId(), PageRequest.of(0, 1)).getTotalElements();
        dto.setFaceCount((int) faceCount);

        // 计算相册数量（从该人物的所有照片中提取唯一相册ID的数量）
        List<Long> albumIds = faceRepository.findByPersonId(person.getId(), PageRequest.of(0, Integer.MAX_VALUE))
            .getContent()
            .stream()
            .filter(face -> face.getPhoto() != null)
            .map(face -> face.getPhoto().getAlbumId())
            .distinct()
            .collect(Collectors.toList());
        dto.setAlbumCount(albumIds.size());

        // 使用统一的头像获取逻辑（优先已设置，fallback到动态计算）
        Object[] sampleData = getPersonSamplePhoto(person.getId());
        if (sampleData[0] != null) {
            dto.setSampleFaceId((Long) sampleData[0]);
            dto.setSamplePhotoId((Long) sampleData[1]);
            dto.setSampleThumbnailPath((String) sampleData[2]);
            dto.setSampleOriginalPath((String) sampleData[3]);
            dto.setSampleConfidence((Double) sampleData[4]);
        }

        return dto;
    }

    private float[] parseEmbedding(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            String s = json.trim();
            if (s.startsWith("[")) s = s.substring(1);
            if (s.endsWith("]")) s = s.substring(0, s.length() - 1);
            if (s.isEmpty()) return null;
            String[] parts = s.split(",");
            float[] v = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                v[i] = Float.parseFloat(parts[i]);
            }
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return -1;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        if (denom < 1e-6) return -1;
        return dot / denom;
    }

    private String toJson(float[] arr) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(java.util.Locale.US, "%.6f", arr[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 删除人物（解除所有人脸的关联后删除）
     */
    @Transactional
    public void deletePerson(Long personId) {
        PersonProfile person = personProfileRepository.findById(personId)
            .orElseThrow(() -> new RuntimeException("人物不存在"));
        
        // 解除所有人脸的关联
        List<Face> faces = faceRepository.findByPersonId(personId, PageRequest.of(0, 10000))
            .getContent();
        for (Face face : faces) {
            face.setPerson(null);
            faceRepository.save(face);
        }

        // 清理“图片直接指派”记录（photo_assignment.person_id -> person_profile.id 外键）
        // 否则删除 person_profile 会触发外键约束失败
        photoAssignmentRepository.deleteByPersonId(personId);
        
        // 删除人物
        personProfileRepository.delete(person);
        log.info("已删除人物: {} (ID: {})，已解除 {} 张人脸的关联", person.getName(), personId, faces.size());
        invalidateClusterCache();
    }

    private String convertToRelativePath(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return absolutePath;
        }

        try {
            String basePath = photoBasePath;
            if (!Paths.get(basePath).isAbsolute()) {
                String projectRoot = System.getProperty("user.dir");
                if (projectRoot.endsWith("backend")) {
                    projectRoot = new File(projectRoot).getParent();
                }
                String cleanPath = basePath.startsWith("./")
                    ? basePath.substring(2)
                    : basePath;
                basePath = new File(projectRoot, cleanPath).getAbsolutePath();
            }

            basePath = Paths.get(basePath).normalize().toString();
            String normalizedAbsolutePath = Paths.get(absolutePath).normalize().toString();

            if (!normalizedAbsolutePath.startsWith(basePath)) {
                return absolutePath;
            }

            String relativePath = normalizedAbsolutePath.substring(basePath.length());
            if (!relativePath.startsWith("/")) {
                relativePath = "/" + relativePath;
            }
            return relativePath.replace("\\", "/");
        } catch (Exception e) {
            return absolutePath;
        }
    }

    /**
     * 计算聚类与已确认人物的相似度（优化版：批量加载，预计算）
     */
    @Transactional(readOnly = true)
    public List<PersonSimilarityDTO> getSimilarPersonsForCluster(int clusterIndex, double threshold) {
        // 获取聚类中的人脸
        List<FaceDTO> clusterFaces = getClusterFaces(clusterIndex, threshold);
        if (clusterFaces.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取聚类中的Face实体（而不是DTO），因为需要embedding
        List<Face> clusterFaceEntities = clusterFaces.stream()
            .map(faceDto -> faceRepository.findById(faceDto.getId()).orElse(null))
            .filter(face -> face != null)
            .collect(Collectors.toList());

        // 计算聚类平均特征向量
        float[] clusterAvgEmbedding = calculateAverageEmbeddingFromEntities(clusterFaceEntities);
        if (clusterAvgEmbedding == null) {
            return new ArrayList<>();
        }

        // 预计算聚类向量的范数
        double clusterNormSum = 0;
        for (float v : clusterAvgEmbedding) {
            clusterNormSum += v * v;
        }
        final double clusterNorm = Math.sqrt(clusterNormSum);

        // 批量加载所有已确认人物的人脸（一次性查询）
        List<Object[]> personFaceData = faceRepository.findAllConfirmedPersonFacesWithEmbedding();
        
        // 按人物分组，预计算每个人的平均向量
        Map<Long, float[]> personAvgEmbeddingMap = new ConcurrentHashMap<>();
        Map<Long, String> personNameMap = new ConcurrentHashMap<>();
        Map<Long, Double> personNormMap = new ConcurrentHashMap<>();

        for (Object[] row : personFaceData) {
            Long personId = ((Number) row[0]).longValue();
            String personName = (String) row[1];
            String embeddingStr = (String) row[2];

            personNameMap.putIfAbsent(personId, personName);

            if (embeddingStr == null || embeddingStr.isEmpty()) continue;
            
            float[] embedding = parseEmbedding(embeddingStr);
            if (embedding == null) continue;

            // 累加向量
            personAvgEmbeddingMap.compute(personId, (k, existing) -> {
                if (existing == null) {
                    return embedding.clone();
                } else {
                    for (int i = 0; i < embedding.length; i++) {
                        existing[i] += embedding[i];
                    }
                    return existing;
                }
            });
        }

        // 计算每个人物的平均向量和范数
        for (Map.Entry<Long, float[]> entry : personAvgEmbeddingMap.entrySet()) {
            Long personId = entry.getKey();
            float[] sumVec = entry.getValue();
            
            // 统计该人物的人脸数量来计算平均值
            long faceCount = personFaceData.stream()
                .filter(row -> personId.equals(((Number) row[0]).longValue()))
                .count();
            
            if (faceCount > 0) {
                for (int i = 0; i < sumVec.length; i++) {
                    sumVec[i] /= faceCount;
                }
                
                // 计算范数
                double norm = 0;
                for (float v : sumVec) {
                    norm += v * v;
                }
                personNormMap.put(personId, Math.sqrt(norm));
            }
        }

        // 并行计算相似度
        List<PersonSimilarityDTO> similarities = personAvgEmbeddingMap.entrySet().parallelStream()
            .map(entry -> {
                Long personId = entry.getKey();
                float[] personVec = entry.getValue();
                Double personNorm = personNormMap.get(personId);
                
                if (personNorm == null || personNorm == 0 || clusterNorm == 0) {
                    return null;
                }
                
                // 计算余弦相似度
                double dotProduct = 0;
                for (int i = 0; i < personVec.length; i++) {
                    dotProduct += personVec[i] * clusterAvgEmbedding[i];
                }
                double similarity = dotProduct / (personNorm * clusterNorm);
                
                return new PersonSimilarityDTO(personId, personNameMap.get(personId), similarity);
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        // 按相似度降序排序，取前5个
        return similarities.stream()
            .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
            .limit(5)
            .collect(Collectors.toList());
    }

    /**
     * 计算选中人脸与所有人物的相似度
     */
    @Transactional(readOnly = true)
    public List<PersonSimilarityDTO> calculateSimilarityToPersons(List<Long> faceIds) {
        if (faceIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取选中的人脸实体
        List<Face> selectedFaces = faceIds.stream()
            .map(id -> faceRepository.findById(id).orElse(null))
            .filter(face -> face != null)
            .collect(Collectors.toList());

        if (selectedFaces.isEmpty()) {
            return new ArrayList<>();
        }

        // 计算选中人脸的平均特征向量
        float[] selectedAvgEmbedding = calculateAverageEmbeddingFromEntities(selectedFaces);
        if (selectedAvgEmbedding == null) {
            return new ArrayList<>();
        }

        // 获取所有已确认人物
        List<PersonProfile> confirmedPersons = personProfileRepository.findAll();
        List<PersonSimilarityDTO> similarities = new ArrayList<>();

        for (PersonProfile person : confirmedPersons) {
            // 获取人物的所有已确认人脸
            List<Face> personFaces = faceRepository.findByPersonIdAndIsConfirmed(person.getId(), true);
            if (personFaces.isEmpty()) continue;

            // 计算人物平均特征向量
            float[] personAvgEmbedding = calculateAverageEmbeddingFromEntities(personFaces);

            if (personAvgEmbedding != null) {
                double similarity = cosine(selectedAvgEmbedding, personAvgEmbedding);
                similarities.add(new PersonSimilarityDTO(person.getId(), person.getName(), similarity));
            }
        }

        // 按相似度降序排序
        return similarities.stream()
            .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
            .collect(Collectors.toList());
    }

    /**
     * 计算人脸实体列表的平均特征向量
     */
    private float[] calculateAverageEmbeddingFromEntities(List<Face> faces) {
        if (faces.isEmpty()) {
            return null;
        }

        int vectorSize = 512; // 假设特征向量长度为512
        float[] avgVector = new float[vectorSize];

        int validFaces = 0;
        for (Face face : faces) {
            float[] embedding = parseEmbedding(face.getEmbedding());
            if (embedding != null && embedding.length == vectorSize) {
                for (int i = 0; i < vectorSize; i++) {
                    avgVector[i] += embedding[i];
                }
                validFaces++;
            }
        }

        if (validFaces == 0) {
            return null;
        }

        // 计算平均值
        for (int i = 0; i < vectorSize; i++) {
            avgVector[i] /= validFaces;
        }

        return avgVector;
    }

    /**
     * 计算人脸DTO列表的平均特征向量（保留原有方法以防其他地方使用）
     */
    private float[] calculateAverageEmbedding(List<FaceDTO> faces) {
        if (faces.isEmpty()) {
            log.debug("人脸DTO列表为空，无法计算平均特征向量");
            return null;
        }

        log.warn("calculateAverageEmbedding方法被调用，但FaceDTO没有embedding字段，请使用calculateAverageEmbeddingFromEntities方法");
        return null;
    }

    /**
     * 获取人物的套图推荐（只显示人物已确认图片所在的相册）
     */
    @Transactional(readOnly = true)
    public List<AlbumRecommendationDTO> getAlbumRecommendationsForPerson(Long personId) {
        // 获取人物的所有已确认人脸
        List<Face> confirmedFaces = faceRepository.findByPersonIdAndIsConfirmed(personId, true);
        if (confirmedFaces.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取人物已确认人脸所在的相册ID及其人脸数量统计
        Map<Long, Long> albumFaceCountMap = confirmedFaces.stream()
            .filter(face -> face.getPhoto() != null && face.getPhoto().getAlbumId() != null)
            .collect(Collectors.groupingBy(
                face -> face.getPhoto().getAlbumId(),
                Collectors.counting()
            ));

        if (albumFaceCountMap.isEmpty()) {
            return new ArrayList<>();
        }

        List<AlbumRecommendationDTO> result = new ArrayList<>();

        // 批量统计“已认领图片数”（含：人脸绑定到人物 + 图片指派到人物），不加载相册图片本身
        Map<Long, Integer> albumClaimedCountMap = new HashMap<>();
        List<Long> albumIds = new ArrayList<>(albumFaceCountMap.keySet());
        if (!albumIds.isEmpty()) {
            List<Object[]> rows = photoAssignmentRepository.countClaimedPhotosByAlbumIds(personId, albumIds);
            for (Object[] row : rows) {
                if (row == null || row.length < 2) continue;
                Long albumId = row[0] == null ? null : ((Number) row[0]).longValue();
                Integer cnt = row[1] == null ? 0 : ((Number) row[1]).intValue();
                if (albumId != null) {
                    albumClaimedCountMap.put(albumId, cnt);
                }
            }
        }


        for (Map.Entry<Long, Long> entry : albumFaceCountMap.entrySet()) {
            Long albumId = entry.getKey();
            Long faceCount = entry.getValue();

            // 再次验证该相册中是否确实包含该人物的已确认人脸
            // 防止因为缓存或数据不一致导致显示错误的相册
            long actualConfirmedFaces = confirmedFaces.stream()
                .filter(face -> face.getPhoto() != null && albumId.equals(face.getPhoto().getAlbumId()))
                .count();

            if (actualConfirmedFaces == 0) {
                // 如果该相册中没有该人物的已确认人脸，跳过
                continue;
            }

            // 获取相册信息
            com.photoexhibition.entity.Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("相册不存在: " + albumId));

            AlbumRecommendationDTO dto = new AlbumRecommendationDTO();
            dto.setAlbumId(album.getId());
            dto.setAlbumName(album.getName());
            dto.setAlbumPath(getAlbumDisplayPath(album.getPath()));
            dto.setPhotoCount(album.getPhotoCount());
            dto.setSimilarFaceCount((int) actualConfirmedFaces); // 已确认人脸数量
            dto.setTakenAt(album.getLatestPhotoTakenAt());
            
            // 设置已认领的图片数量
            dto.setClaimedPhotoCount(albumClaimedCountMap.getOrDefault(albumId, 0));

            // 设置相册封面图片：使用该人物在相册中的最多3张已确认照片
            List<Face> facesInAlbum = confirmedFaces.stream()
                    .filter(face -> face.getPhoto() != null && albumId.equals(face.getPhoto().getAlbumId()))
                    .collect(Collectors.toList());

            String coverImagePath1 = null;
            String coverImagePath2 = null;
            String coverImagePath3 = null;

            if (!facesInAlbum.isEmpty()) {
                // 取前3张已确认照片作为封面
                for (int i = 0; i < Math.min(facesInAlbum.size(), 3); i++) {
                    Face face = facesInAlbum.get(i);
                    if (face.getPhoto() != null) {
                        String path = convertToRelativePath(face.getPhoto().getThumbnailPath());
                        if (i == 0) coverImagePath1 = path;
                        else if (i == 1) coverImagePath2 = path;
                        else if (i == 2) coverImagePath3 = path;
                    }
                }
                // 兼容旧版本：第一张图作为 coverImagePath
                dto.setCoverImagePath(coverImagePath1);
            } else {
                // 如果没有找到该人物的照片，则使用默认相册封面
                var coverImages = albumService.getAlbumCoverImages(album.getId());
                if (coverImages != null && coverImages.getLeftVertical() != null) {
                    coverImagePath1 = convertToRelativePath(coverImages.getLeftVertical().getThumbnailPath());
                    dto.setCoverImagePath(coverImagePath1);
                }
                if (coverImages != null && coverImages.getRightTop() != null) {
                    coverImagePath2 = convertToRelativePath(coverImages.getRightTop().getThumbnailPath());
                }
                if (coverImages != null && coverImages.getRightBottom() != null) {
                    coverImagePath3 = convertToRelativePath(coverImages.getRightBottom().getThumbnailPath());
                }
            }

            dto.setCoverImagePath1(coverImagePath1);
            dto.setCoverImagePath2(coverImagePath2);
            dto.setCoverImagePath3(coverImagePath3);

            result.add(dto);
        }

        // 按相册拍摄时间倒序排序（最新的相册排在前面）
        result.sort((a, b) -> {
            if (a.getTakenAt() == null && b.getTakenAt() == null) {
                return 0;
            }
            if (a.getTakenAt() == null) {
                return 1; // null值排在后面
            }
            if (b.getTakenAt() == null) {
                return -1; // null值排在后面
            }
            return b.getTakenAt().compareTo(a.getTakenAt()); // 倒序排列
        });

        return result;
    }


    /**
     * 获取指定相册中与人物相似的未分配人脸
     */
    @Transactional(readOnly = true)
    public List<FaceDTO> getSimilarFacesForAlbum(Long personId, Long albumId) {
        // 获取人物的所有已确认人脸（用于计算相似度）
        List<Face> confirmedFaces = faceRepository.findByPersonIdAndIsConfirmed(personId, true);

        // 计算人物平均特征向量
        float[] personAvgEmbedding = null;
        if (!confirmedFaces.isEmpty()) {
            personAvgEmbedding = calculateAverageEmbeddingFromEntities(confirmedFaces);
        }

        // 获取指定相册中的所有人脸（包括已分配和未分配的）
        // 关键优化：不要全库扫描 findAll()，直接按 albumId 查询
        List<Face> albumFaces = faceRepository.findByPhotoAlbumId(albumId);

        List<FaceSimilarity> allFaces = new ArrayList<>();

        for (Face face : albumFaces) {
            double similarity = 0.0;

            // 如果有确认的人脸，计算相似度
            if (personAvgEmbedding != null) {
                float[] embedding = parseEmbedding(face.getEmbedding());
                if (embedding != null) {
                    similarity = cosine(personAvgEmbedding, embedding);
                }
            }

            allFaces.add(new FaceSimilarity(face, similarity));
        }

        // 按相似度降序排序（相似度为0的排在后面）
        allFaces.sort((a, b) -> {
            // 已分配给人脸的排在前面
            boolean aAssigned = a.face.getPerson() != null && a.face.getPerson().getId().equals(personId);
            boolean bAssigned = b.face.getPerson() != null && b.face.getPerson().getId().equals(personId);

            if (aAssigned != bAssigned) {
                return aAssigned ? -1 : 1;
            }

            // 相似度从高到低排序
            return Double.compare(b.similarity, a.similarity);
        });

        // 转换为DTO
        return allFaces.stream()
            .map(fs -> {
                FaceDTO dto = toDTO(fs.face);
                dto.setSimilarity(fs.similarity);
                return dto;
            })
            .collect(Collectors.toList());
    }

    /**
     * 获取相册显示路径：基于base-path的相对路径，只保留分类和顶级相册名
     */
    private String getAlbumDisplayPath(String absolutePath) {
        try {
            // 计算base-path的绝对路径
            String basePathStr = photoBasePath;
            if (!Paths.get(basePathStr).isAbsolute()) {
                String projectRoot = System.getProperty("user.dir");
                if (projectRoot.endsWith("backend")) {
                    projectRoot = new File(projectRoot).getParent();
                }
                if (basePathStr.startsWith("./")) {
                    basePathStr = basePathStr.substring(2);
                }
                basePathStr = new File(projectRoot, basePathStr).getAbsolutePath();
            }

            // 标准化路径
            Path basePath = Paths.get(basePathStr).normalize();
            Path albumPath = Paths.get(absolutePath).normalize();

            // 检查相册路径是否在base-path下
            if (!albumPath.startsWith(basePath)) {
                return absolutePath; // 如果不在base-path下，返回原路径
            }

            // 获取相对路径
            Path relativePath = basePath.relativize(albumPath);

            // 转换为字符串并统一分隔符
            String pathStr = relativePath.toString().replace("\\", "/");

            // 显示完整的相对路径，但去掉最后一级（相册本身的名字）
            // 例如：人像/2024.07.19 大奇山-水上汉服/合照 -> 人像/2024.07.19 大奇山-水上汉服
            int lastSlashIndex = pathStr.lastIndexOf('/');
            if (lastSlashIndex > 0) {
                return pathStr.substring(0, lastSlashIndex);
            }

            // 如果没有斜杠，返回原路径
            return pathStr;

        } catch (Exception e) {
            // 转换失败，返回原路径
            return absolutePath;
        }
    }

    /**
     * 清空所有人脸数据
     */
    @Transactional
    public void clearAllFaces() {
        // 删除所有人脸记录
        faceRepository.deleteAll();
        // 删除所有人物记录（人物是通过人脸聚类生成的）
        personProfileRepository.deleteAll();
        // 清除缓存
        clearPersonSimilarityCache();
    }

    // ========== 人物相似度缓存相关方法 ==========

    /**
     * 检查是否有有效的缓存
     */
    private boolean hasValidSimilarityCache(Long personId) {
        PersonSimilarityCacheEntry entry = personSimilarityCache.get(personId);
        if (entry == null) {
            return false;
        }
        long currentUnassignedCount = faceRepository.countByPersonIsNull();
        return entry.unassignedFaceCount == currentUnassignedCount;
    }

    /**
     * 获取缓存的相似度列表
     */
    private List<CachedFaceSimilarity> getCachedSimilarities(Long personId) {
        PersonSimilarityCacheEntry entry = personSimilarityCache.get(personId);
        return entry != null ? entry.allSimilarities : null;
    }

    /**
     * 存储相似度计算结果到缓存
     */
    private void cacheSimilarities(Long personId, List<CachedFaceSimilarity> similarities) {
        long currentUnassignedCount = faceRepository.countByPersonIsNull();
        PersonSimilarityCacheEntry entry = new PersonSimilarityCacheEntry(similarities, currentUnassignedCount);
        personSimilarityCache.put(personId, entry);
        lastUnassignedCount = currentUnassignedCount;
        log.debug("缓存人物 {} 的相似度计算结果，共 {} 条", personId, similarities.size());
    }

    /**
     * 清除指定人物的相似度缓存
     */
    public void clearPersonSimilarityCache(Long personId) {
        personSimilarityCache.remove(personId);
        log.debug("清除人物 {} 的相似度缓存", personId);
    }

    /**
     * 清除所有人物相似度缓存
     */
    public void clearPersonSimilarityCache() {
        personSimilarityCache.clear();
        log.info("清除所有人物相似度缓存");
    }

    /**
     * 计算并缓存人物与所有未分配人脸的相似度（带并发控制）
     * 返回值：包含 faceId、similarity、sameFolder 的列表
     */
    private List<CachedFaceSimilarity> computeAndCacheSimilarities(Long personId) {
        // 获取锁
        Object lock = computingLocks.computeIfAbsent(personId, k -> new Object());
        synchronized (lock) {
            // 双重检查
            if (hasValidSimilarityCache(personId)) {
                log.debug("人物 {} 使用缓存（获得锁后）", personId);
                return getCachedSimilarities(personId);
            }

            long startTime = System.currentTimeMillis();
            log.info("人物 {} 开始计算相似度...", personId);

            // 获取人物的所有人脸
            List<Face> personFaces = faceRepository.findByPersonId(personId, PageRequest.of(0, 1000)).getContent();
            if (personFaces.isEmpty()) {
                return new ArrayList<>();
            }

            // 计算人物平均特征向量
            float[] personAvgEmbedding = calculateAverageEmbeddingFromEntities(personFaces);
            if (personAvgEmbedding == null) {
                return new ArrayList<>();
            }

            // 获取人物的所有照片路径（用于文件夹比较）
            Set<String> personPhotoPaths = personFaces.stream()
                .filter(face -> face.getPhoto() != null && face.getPhoto().getOriginalPath() != null)
                .map(face -> face.getPhoto().getOriginalPath())
                .collect(Collectors.toSet());

            // 获取所有未分配人脸（使用预加载关联的方法）
            List<Face> allUnassigned = faceRepository.findByPersonIsNullWithPhoto();

            // 预加载所有人脸的照片路径到 Map（避免懒加载和空指针问题）
            Map<Long, String> facePhotoPaths = new ConcurrentHashMap<>();
            for (Face face : allUnassigned) {
                if (face.getPhoto() != null && face.getPhoto().getOriginalPath() != null) {
                    facePhotoPaths.put(face.getId(), face.getPhoto().getOriginalPath());
                }
            }
            final Map<Long, String> finalFacePhotoPaths = facePhotoPaths;

            log.debug("人物 {}: 待计算 {} 个未分配人脸", personId, allUnassigned.size());

            // 预计算归一化后的目标向量（避免重复计算）
            double norm = 0;
            for (float v : personAvgEmbedding) {
                norm += v * v;
            }
            norm = Math.sqrt(norm);
            final double targetNorm = norm;

            // 并行计算相似度（使用 parallelStream）
            List<CachedFaceSimilarity> result = allUnassigned.parallelStream()
                .filter(face -> face.getEmbedding() != null && !face.getEmbedding().isEmpty())
                .map(face -> {
                    float[] faceEmbedding = parseEmbedding(face.getEmbedding());
                    if (faceEmbedding == null) return null;

                    // 使用优化后的余弦相似度计算
                    double sim = cosineOptimized(personAvgEmbedding, faceEmbedding, targetNorm);
                    
                    // 使用预加载的照片路径（避免懒加载问题）
                    String photoPath = finalFacePhotoPaths.get(face.getId());
                    boolean sameFolder = photoPath != null && 
                        personPhotoPaths.stream().anyMatch(pp -> isSameFolder(pp, photoPath));

                    return new CachedFaceSimilarity(face.getId(), sim, sameFolder);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            // 排序（按相似度降序）
            result.sort((a, b) -> Double.compare(b.similarity, a.similarity));

            // 存储到缓存
            cacheSimilarities(personId, result);

            long duration = System.currentTimeMillis() - startTime;
            log.info("人物 {} 相似度计算完成，耗时 {}ms，共 {} 条匹配", 
                personId, duration, result.size());

            return result;
        }
    }

    /**
     * 优化后的余弦相似度计算（预计算目标向量范数）
     */
    private double cosineOptimized(float[] a, float[] b, double normA) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
        double dotProduct = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dotProduct / (normA * Math.sqrt(normB));
    }

    /**
     * 根据相似度获取人脸（从缓存或重新计算）
     * filterHigh: true = 相似度 >= 0.5 或 (同文件夹且 >= 0.4)
     *            false = 相似度 < 0.5 且不在同文件夹
     */
    private List<FaceDTO> getFacesBySimilarityFilter(Long personId, boolean filterHigh) {
        // 获取或计算相似度列表
        List<CachedFaceSimilarity> allSimilarities = getCachedSimilarities(personId);
        if (allSimilarities == null) {
            allSimilarities = computeAndCacheSimilarities(personId);
        }

        // 过滤并转换为 DTO
        List<FaceDTO> result = new ArrayList<>();
        for (CachedFaceSimilarity cs : allSimilarities) {
            boolean pass = filterHigh
                ? (cs.similarity >= 0.5 || (cs.sameFolder && cs.similarity >= 0.4))
                : (cs.similarity < 0.5 && !cs.sameFolder);

            if (pass) {
                Face face = faceRepository.findById(cs.faceId).orElse(null);
                if (face != null) {
                    FaceDTO dto = toDTO(face);
                    dto.setSimilarity(cs.similarity);
                    result.add(dto);
                }
            }
        }

        return result;
    }
}

