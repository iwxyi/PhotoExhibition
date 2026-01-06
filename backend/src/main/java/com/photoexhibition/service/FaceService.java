package com.photoexhibition.service;

import com.photoexhibition.dto.FaceDTO;
import com.photoexhibition.dto.FaceClusterDTO;
import com.photoexhibition.dto.PersonDTO;
import com.photoexhibition.dto.PersonListItemDTO;
import com.photoexhibition.entity.Face;
import com.photoexhibition.entity.PersonProfile;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.dto.PersonSummaryDTO;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.PersonProfileRepository;
import com.photoexhibition.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
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

        // 设置人脸列表到photo对象
        targetPhoto.setFaces(faces);
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

        if (personId == null) {
            face.setPerson(null);
            face.setIsConfirmed(false);
            faceRepository.save(face);
            // 不再自动清理，需要前端调用检查接口并确认
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
        for (Long faceId : faceIds) {
            Face face = faceRepository.findById(faceId).orElse(null);
            if (face != null) {
                face.setPerson(null);
                face.setIsConfirmed(false);
                faceRepository.save(face);
            }
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
    public Page<FaceDTO> listUnassignedFaces(Pageable pageable) {
        return faceRepository.findByPersonIsNull(pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<FaceDTO> listAssignedFaces(Pageable pageable) {
        return faceRepository.findByPersonIsNotNull(pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<FaceDTO> listPersonFaces(Long personId, Pageable pageable) {
        return faceRepository.findByPersonId(personId, pageable).map(this::toDTO);
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
    public List<PersonSummaryDTO> listPersonsWithSample() {
        return personProfileRepository.findAll().stream()
            .map(this::toSummaryDTO)
            .collect(Collectors.toList());
    }

    /**
     * 获取统一的人物列表（包括已确认人物和未确认聚类）
     */
    @Transactional(readOnly = true)
    public List<PersonListItemDTO> listPersonItems(double clusterThreshold) {
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

            // 获取代表脸
            Face sample = faceRepository.findTopByPersonIdOrderByConfidenceDescCreatedAtDesc(person.getId());
            if (sample == null) {
                sample = faceRepository.findTopByPersonIdOrderByCreatedAtDesc(person.getId());
            }
            if (sample != null && sample.getPhoto() != null) {
                item.setSampleFaceId(sample.getId());
                item.setSamplePhotoId(sample.getPhoto().getId());
                item.setSampleConfidence(sample.getConfidence());
                item.setSampleThumbnailPath(convertToRelativePath(sample.getPhoto().getThumbnailPath()));
                item.setSampleOriginalPath(convertToRelativePath(sample.getPhoto().getOriginalPath()));
            }

            items.add(item);
        }

        // 2. 未确认的聚类
        List<FaceClusterDTO> clusters = clusterSimilarFaces(clusterThreshold);
        for (int i = 0; i < clusters.size(); i++) {
            FaceClusterDTO cluster = clusters.get(i);
            PersonListItemDTO item = new PersonListItemDTO();
            item.setType("cluster");
            item.setId((long) i); // 使用索引作为ID
            item.setName("未命名");
            item.setDescription("自动聚合的相似人脸，尚未确认");
            item.setFaceCount(cluster.getCount());
            item.setAvgConfidence(cluster.getAvgConfidence());

            // 获取代表脸
            if (cluster.getRepresentativeFaceId() != null) {
                Face sample = faceRepository.findById(cluster.getRepresentativeFaceId()).orElse(null);
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
    @Transactional(readOnly = true)
    public List<FaceDTO> findSimilarUnassignedFaces(Long personId, int top, double threshold) {
        // 获取该人物的所有人脸
        List<Face> personFaces = faceRepository.findByPersonId(personId, PageRequest.of(0, 1000))
            .getContent();
        if (personFaces.isEmpty()) {
            return new ArrayList<>();
        }

        // 计算该人物所有人脸的平均向量（更准确）
        List<float[]> personVectors = new ArrayList<>();
        for (Face face : personFaces) {
            if (face.getEmbedding() == null || face.getEmbedding().isEmpty()) continue;
            float[] vec = parseEmbedding(face.getEmbedding());
            if (vec != null) {
                personVectors.add(vec);
            }
        }
        
        if (personVectors.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 计算平均向量
        int dim = personVectors.get(0).length;
        float[] avgVec = new float[dim];
        for (float[] vec : personVectors) {
            for (int i = 0; i < dim; i++) {
                avgVec[i] += vec[i];
            }
        }
        int count = personVectors.size();
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

        // 查找所有未分配的人脸
        List<Face> unassigned = faceRepository.findByPersonIsNull();
        List<FaceDTO> result = new ArrayList<>();

        for (Face f : unassigned) {
            if (f.getEmbedding() == null || f.getEmbedding().isEmpty()) continue;
            float[] vec = parseEmbedding(f.getEmbedding());
            if (vec == null) continue;
            double sim = cosine(avgVec, vec);
            if (sim >= threshold) {
                FaceDTO dto = toDTO(f);
                dto.setSimilarity(sim);
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

        // 绑定所有人脸（创建时默认为自动分配，用户需要手动确认）
        for (Long faceId : faceIds) {
            Face face = faceRepository.findById(faceId)
                .orElseThrow(() -> new RuntimeException("人脸不存在: " + faceId));
            face.setPerson(person);
            face.setIsConfirmed(false); // 创建时默认为自动分配，用户需要手动确认
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

        List<Face> all = faceRepository.findAll();
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
            dto.setPhotoThumbnailPath(convertToRelativePath(face.getPhoto().getThumbnailPath()));
            dto.setPhotoOriginalPath(convertToRelativePath(face.getPhoto().getOriginalPath()));
        }
        return dto;
    }

    private PersonDTO toDTO(PersonProfile person) {
        PersonDTO dto = new PersonDTO();
        dto.setId(person.getId());
        dto.setName(person.getName());
        dto.setDescription(person.getDescription());
        return dto;
    }

    private PersonSummaryDTO toSummaryDTO(PersonProfile person) {
        PersonSummaryDTO dto = new PersonSummaryDTO();
        dto.setId(person.getId());
        dto.setName(person.getName());
        dto.setDescription(person.getDescription());
        dto.setCreatedAt(person.getCreatedAt());
        dto.setUpdatedAt(person.getUpdatedAt());

        Face sample = faceRepository.findTopByPersonIdOrderByConfidenceDescCreatedAtDesc(person.getId());
        if (sample == null) {
            sample = faceRepository.findTopByPersonIdOrderByCreatedAtDesc(person.getId());
        }
        if (sample != null && sample.getPhoto() != null) {
            dto.setSampleFaceId(sample.getId());
            dto.setSamplePhotoId(sample.getPhoto().getId());
            dto.setSampleConfidence(sample.getConfidence());
            dto.setSampleThumbnailPath(convertToRelativePath(sample.getPhoto().getThumbnailPath()));
            dto.setSampleOriginalPath(convertToRelativePath(sample.getPhoto().getOriginalPath()));
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
     * 清空所有人脸数据
     */
    @Transactional
    public void clearAllFaces() {
        // 删除所有人脸记录
        faceRepository.deleteAll();
        // 删除所有人物记录（人物是通过人脸聚类生成的）
        personProfileRepository.deleteAll();
    }
}

