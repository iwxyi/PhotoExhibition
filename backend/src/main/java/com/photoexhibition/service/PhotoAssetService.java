package com.photoexhibition.service;

import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.StorageProvider;
import com.photoexhibition.entity.StorageType;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.StorageProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PhotoAssetService {

    private final PhotoRepository photoRepository;
    private final UserPathService userPathService;
    private final StorageProviderRepository storageProviderRepository;
    private final StorageUploadService storageUploadService;

    public ResponseEntity<Resource> readPhotoAsset(Long photoId, String variant) {
        Photo photo = photoRepository.findById(photoId)
            .orElseThrow(() -> new RuntimeException("照片不存在"));
        String selectedPath = selectAssetPath(photo, variant);
        if (selectedPath == null || selectedPath.isBlank()) {
            throw new RuntimeException("当前照片没有可访问的资源");
        }

        UserPathService.StoragePathReference reference = userPathService.parseStoragePathReference(selectedPath);
        if (reference == null) {
            return readLocalPath(selectedPath);
        }

        StorageProvider provider = storageProviderRepository.findById(reference.getStorageProviderId())
            .orElseThrow(() -> new RuntimeException("存储提供者不存在: " + reference.getStorageProviderId()));
        if (provider.getType() == StorageType.LOCAL) {
            Path resolved = userPathService.resolveStoredPhotoPath(selectedPath);
            return readLocalPath(resolved.toString());
        }

        try {
            UserAccount user = null;
            if (reference.getUserId() != null) {
                user = new UserAccount();
                user.setId(reference.getUserId());
            }
            StorageUploadService.DownloadedFile downloaded = storageUploadService.downloadFile(
                provider,
                user,
                reference.getRelativePath()
            );
            ByteArrayResource resource = new ByteArrayResource(downloaded.getBytes());
            return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .contentType(resolveMediaType(downloaded.getContentType(), downloaded.getFilename()))
                .contentLength(downloaded.getBytes().length)
                .body(resource);
        } catch (Exception e) {
            throw new RuntimeException("读取远端照片资源失败: " + userPathService.sanitizeVisibleText(e.getMessage()), e);
        }
    }

    private ResponseEntity<Resource> readLocalPath(String path) {
        try {
            Path resolved = userPathService.resolveStoredPhotoPath(path);
            if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
                throw new RuntimeException("文件不存在");
            }
            byte[] bytes = Files.readAllBytes(resolved);
            ByteArrayResource resource = new ByteArrayResource(bytes);
            String filename = resolved.getFileName() != null ? resolved.getFileName().toString() : null;
            return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(resolveMediaType(Files.probeContentType(resolved), filename))
                .contentLength(bytes.length)
                .body(resource);
        } catch (Exception e) {
            throw new RuntimeException("读取本地照片资源失败: " + userPathService.sanitizeVisibleText(e.getMessage()), e);
        }
    }

    private String selectAssetPath(Photo photo, String variant) {
        String normalizedVariant = variant == null ? "auto" : variant.trim().toLowerCase(Locale.ROOT);
        switch (normalizedVariant) {
            case "small":
                return firstNonBlank(photo.getSmallThumbPath(), photo.getMediumThumbPath(), photo.getThumbnailPath(), photo.getWebpPath(), photo.getOriginalPath());
            case "medium":
                return firstNonBlank(photo.getMediumThumbPath(), photo.getThumbnailPath(), photo.getWebpPath(), photo.getOriginalPath());
            case "large":
                return firstNonBlank(photo.getLargeThumbPath(), photo.getWebpPath(), photo.getMediumThumbPath(), photo.getThumbnailPath(), photo.getOriginalPath());
            case "thumbnail":
                return firstNonBlank(photo.getThumbnailPath(), photo.getMediumThumbPath(), photo.getWebpPath(), photo.getOriginalPath());
            case "webp":
                return firstNonBlank(photo.getWebpPath(), photo.getLargeThumbPath(), photo.getMediumThumbPath(), photo.getThumbnailPath(), photo.getOriginalPath());
            case "original":
                return photo.getOriginalPath();
            case "auto":
            default:
                return firstNonBlank(photo.getWebpPath(), photo.getMediumThumbPath(), photo.getThumbnailPath(), photo.getOriginalPath());
        }
    }

    private MediaType resolveMediaType(String contentType, String filename) {
        if (contentType != null && !contentType.isBlank()) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
            }
        }
        String normalized = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (normalized.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (normalized.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (normalized.endsWith(".bmp")) {
            return MediaType.parseMediaType("image/bmp");
        }
        if (normalized.endsWith(".svg")) {
            return MediaType.parseMediaType("image/svg+xml");
        }
        return MediaType.IMAGE_JPEG;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
