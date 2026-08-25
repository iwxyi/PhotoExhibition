package com.photoexhibition.service;

import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PhotoDedupService {

    private final PhotoRepository photoRepository;
    private final UserPathService userPathService;

    @Transactional(readOnly = true)
    public Map<String, Object> precheckContentHash(UserAccount currentUser, String contentHash) {
        String normalizedHash = contentHash == null ? "" : contentHash.trim().toLowerCase();
        if (normalizedHash.isBlank()) {
            throw new IllegalArgumentException("contentHash 不能为空");
        }

        Photo matched = photoRepository.findByContentHash(normalizedHash).orElse(null);
        Photo canonical = resolveCanonicalPhoto(matched);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("contentHash", normalizedHash);
        resp.put("exists", canonical != null);
        if (canonical == null) {
            resp.put("sameOwner", false);
            resp.put("visible", false);
            resp.put("reusableMetadata", false);
            resp.put("reusableDerivatives", false);
            resp.put("message", "未命中已存在内容，可继续正常上传");
            return resp;
        }

        boolean visible = isVisibleTo(currentUser, canonical);
        boolean sameOwner = currentUser != null && canonical.getUserId() != null && canonical.getUserId().equals(currentUser.getId());
        resp.put("canonicalPhotoId", canonical.getId());
        resp.put("canonicalUserId", visible ? canonical.getUserId() : null);
        resp.put("sameOwner", sameOwner);
        resp.put("visible", visible);
        resp.put("reusableMetadata", hasReusableMetadata(canonical));
        resp.put("reusableDerivatives", hasReusableDerivatives(canonical));
        resp.put("canonicalSource", canonical.getCanonicalPhotoId() == null);
        if (visible) {
            resp.put("photoId", canonical.getId());
            resp.put("originalPath", userPathService.toDisplayPath(canonical.getOriginalPath(), true));
            resp.put("thumbnailPath", userPathService.toDisplayPath(canonical.getThumbnailPath(), true));
        }
        resp.put("message", sameOwner
            ? "命中当前用户已存在内容，可复用规范源信息并为后续秒传铺底"
            : "命中已存在内容，可复用规范源信息并为后续跨用户去重铺底");
        return resp;
    }

    private Photo resolveCanonicalPhoto(Photo matched) {
        if (matched == null) {
            return null;
        }
        if (matched.getCanonicalPhotoId() == null) {
            return matched;
        }
        return photoRepository.findById(matched.getCanonicalPhotoId()).orElse(matched);
    }

    private boolean isVisibleTo(UserAccount currentUser, Photo photo) {
        if (currentUser == null || photo == null) {
            return false;
        }
        if (currentUser.getRole() == UserRole.SUPER_ADMIN) {
            return true;
        }
        return photo.getUserId() != null && photo.getUserId().equals(currentUser.getId());
    }

    private boolean hasReusableMetadata(Photo photo) {
        return photo.getWidth() != null
            || photo.getHeight() != null
            || hasText(photo.getFormat())
            || hasText(photo.getExifData())
            || photo.getTakenAt() != null
            || photo.getQualityScore() != null
            || hasText(photo.getDominantColor())
            || hasText(photo.getColorCategory())
            || hasText(photo.getColorPalette());
    }

    private boolean hasReusableDerivatives(Photo photo) {
        return hasText(photo.getThumbnailPath())
            || hasText(photo.getWebpPath())
            || hasText(photo.getSmallThumbPath())
            || hasText(photo.getMediumThumbPath())
            || hasText(photo.getLargeThumbPath())
            || hasText(photo.getBackgroundRemovedPath());
    }

    private boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }
}
