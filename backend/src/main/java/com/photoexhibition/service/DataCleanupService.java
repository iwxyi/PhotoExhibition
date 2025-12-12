package com.photoexhibition.service;

import com.photoexhibition.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}

