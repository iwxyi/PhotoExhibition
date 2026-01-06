package com.photoexhibition.service;

import com.photoexhibition.entity.Face;
import com.photoexhibition.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataCleanupService {

    private final PhotoRepository photoRepository;
    private final AlbumRepository albumRepository;
    private final TagRepository tagRepository;
    private final FaceRepository faceRepository;
    private final PersonProfileRepository personProfileRepository;

    /**
     * 清理所有数据，只保留账号数据（AdminUser）
     * 注意：这是一个危险操作，会删除所有照片、相册、标签、人脸、人物等数据
     */
    @Transactional
    public void cleanupAllData() {
        log.warn("开始清理所有数据（保留账号数据）...");
        
        long faceCount = faceRepository.count();
        long photoCount = photoRepository.count();
        long albumCount = albumRepository.count();
        long tagCount = tagRepository.count();
        long personCount = personProfileRepository.count();
        
        // 1. 删除所有人脸（必须先删除，因为有外键关联）
        log.info("删除 {} 个人脸记录", faceCount);
        faceRepository.deleteAll();
        
        // 2. 删除所有人物
        log.info("删除 {} 个人物记录", personCount);
        personProfileRepository.deleteAll();
        
        // 3. 删除所有照片（照片可能关联标签，但删除时会自动处理关联表）
        log.info("删除 {} 张照片记录", photoCount);
        photoRepository.deleteAll();
        
        // 4. 删除所有相册（相册可能关联标签，但删除时会自动处理关联表）
        log.info("删除 {} 个相册记录", albumCount);
        albumRepository.deleteAll();
        
        // 5. 删除所有标签
        log.info("删除 {} 个标签记录", tagCount);
        tagRepository.deleteAll();
        
        log.warn("数据清理完成。已删除: 人脸={}, 照片={}, 相册={}, 标签={}, 人物={}",
            faceCount, photoCount, albumCount, tagCount, personCount);
    }

    /**
     * 清理重复的人脸记录
     * 找出同一张照片有多条人脸记录的情况，保留最新的记录
     */
    @Transactional
    public Map<String, Object> cleanupDuplicateFaces() {
        Map<String, Object> result = new HashMap<>();
        log.info("开始清理重复的人脸记录...");

        try {
            // 1. 统计重复情况
            List<Object[]> duplicates = faceRepository.findDuplicateFacesByPhoto();
            log.info("发现 {} 组重复人脸记录", duplicates.size());

            int totalDeleted = 0;

            // 2. 处理每组重复记录
            for (Object[] duplicate : duplicates) {
                Long photoId = (Long) duplicate[0];
                Long count = (Long) duplicate[1];

                if (count <= 1) continue; // 没有重复

                log.debug("处理照片 {} 的 {} 条重复人脸记录", photoId, count);

                // 获取该照片的所有人脸记录，按创建时间倒序（最新的在前）
                List<Face> faces = faceRepository.findByPhotoIdOrderByCreatedAtDesc(photoId);

                // 保留第一条（最新的），删除其余的
                if (faces.size() > 1) {
                    List<Face> toDelete = faces.subList(1, faces.size());
                    faceRepository.deleteAll(toDelete);
                    totalDeleted += toDelete.size();

                    log.debug("照片 {} 删除 {} 条重复人脸记录，保留最新的 1 条", photoId, toDelete.size());
                }
            }

            result.put("success", true);
            result.put("message", "重复人脸记录清理完成");
            result.put("duplicateGroups", duplicates.size());
            result.put("totalDeleted", totalDeleted);

            log.info("重复人脸记录清理完成，共删除 {} 条记录，涉及 {} 组重复", totalDeleted, duplicates.size());

        } catch (Exception e) {
            log.error("清理重复人脸记录失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }
}

