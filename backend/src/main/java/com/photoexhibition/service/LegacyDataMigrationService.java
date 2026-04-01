package com.photoexhibition.service;

import com.photoexhibition.entity.AdminUser;
import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Comment;
import com.photoexhibition.entity.Face;
import com.photoexhibition.entity.PersonProfile;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.StorageProvider;
import com.photoexhibition.entity.StorageType;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.entity.UserStatus;
import com.photoexhibition.repository.AdminUserRepository;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.CommentRepository;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.FilterOptionRepository;
import com.photoexhibition.repository.PersonProfileRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.StorageProviderRepository;
import com.photoexhibition.repository.TagRepository;
import com.photoexhibition.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyDataMigrationService {

    private final UserAccountRepository userAccountRepository;
    private final AdminUserRepository adminUserRepository;
    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final CommentRepository commentRepository;
    private final FaceRepository faceRepository;
    private final FilterOptionRepository filterOptionRepository;
    private final PersonProfileRepository personProfileRepository;
    private final TagRepository tagRepository;
    private final StorageProviderRepository storageProviderRepository;
    private final FilterOptionService filterOptionService;
    private final UserStorageService userStorageService;
    private final UserPathService userPathService;
    private final SystemConfigService systemConfigService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateLegacyOwnershipOnStartup() {
        if (systemConfigService.isLegacyMigrationCompleted()) {
            log.info("检测到旧数据迁移已完成，启动阶段跳过重复迁移");
            return;
        }
        runMigration();
    }

    @Transactional
    public Map<String, Object> runMigration() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("startedAt", LocalDateTime.now().toString());
        if (systemConfigService.isLegacyMigrationCompleted()) {
            summary.put("success", true);
            summary.put("skipped", true);
            summary.put("message", "旧数据迁移已完成，本次跳过");
            summary.put("finishedAt", LocalDateTime.now().toString());
            return summary;
        }
        UserAccount owner = ensureOwnerAccount();
        if (owner == null) {
            log.warn("未找到可用的默认数据归属账号，跳过旧数据 userId 迁移");
            summary.put("success", false);
            summary.put("message", "未找到可用的默认数据归属账号");
            summary.put("finishedAt", LocalDateTime.now().toString());
            return summary;
        }

        Long ownerUserId = owner.getId();
        List<Album> albums = albumRepository.findByUserIdIsNull();
        summary.put("migratedAlbumOwnershipCount", albums.size());
        for (Album album : albums) {
            album.setUserId(ownerUserId);
        }
        if (!albums.isEmpty()) {
            albumRepository.saveAll(albums);
            log.info("已迁移 {} 个旧相册到用户 {}", albums.size(), owner.getUsername());
        }

        Map<Long, Long> albumUserMap = albumRepository.findByUserIdIsNotNull().stream()
            .collect(Collectors.toMap(Album::getId, album -> album.getUserId() != null ? album.getUserId() : ownerUserId, (left, right) -> left, HashMap::new));

        List<Photo> photos = photoRepository.findByUserIdIsNull();
        summary.put("migratedPhotoOwnershipCount", photos.size());
        for (Photo photo : photos) {
            Long resolvedUserId = photo.getAlbumId() != null
                ? albumUserMap.getOrDefault(photo.getAlbumId(), ownerUserId)
                : ownerUserId;
            photo.setUserId(resolvedUserId);
        }
        if (!photos.isEmpty()) {
            photoRepository.saveAll(photos);
            log.info("已迁移 {} 张旧照片到用户 {}", photos.size(), owner.getUsername());
        }

        Map<Long, Long> photoUserMap = photoRepository.findByUserIdIsNotNull().stream()
            .collect(Collectors.toMap(Photo::getId, photo -> photo.getUserId() != null ? photo.getUserId() : ownerUserId, (left, right) -> left, HashMap::new));

        List<PersonProfile> persons = personProfileRepository.findByUserIdIsNull();
        summary.put("migratedPersonOwnershipCount", persons.size());
        for (PersonProfile person : persons) {
            person.setUserId(ownerUserId);
        }
        if (!persons.isEmpty()) {
            personProfileRepository.saveAll(persons);
            log.info("已迁移 {} 个人物到用户 {}", persons.size(), owner.getUsername());
        }

        List<Tag> tags = tagRepository.findByUserIdIsNull();
        summary.put("migratedTagOwnershipCount", tags.size());
        for (Tag tag : tags) {
            tag.setUserId(ownerUserId);
        }
        if (!tags.isEmpty()) {
            tagRepository.saveAll(tags);
            log.info("已迁移 {} 个标签到用户 {}", tags.size(), owner.getUsername());
        }

        List<Comment> comments = commentRepository.findByUserIdIsNull();
        summary.put("migratedCommentOwnershipCount", comments.size());
        for (Comment comment : comments) {
            comment.setUserId(albumUserMap.getOrDefault(comment.getAlbumId(), ownerUserId));
        }
        if (!comments.isEmpty()) {
            commentRepository.saveAll(comments);
            log.info("已迁移 {} 条评论到用户 {}", comments.size(), owner.getUsername());
        }

        List<Face> faces = faceRepository.findByUserIdIsNull();
        summary.put("migratedFaceOwnershipCount", faces.size());
        for (Face face : faces) {
            Long photoId = face.getPhoto() != null ? face.getPhoto().getId() : null;
            Long resolvedUserId = photoId != null ? photoUserMap.getOrDefault(photoId, ownerUserId) : ownerUserId;
            face.setUserId(resolvedUserId);
        }
        if (!faces.isEmpty()) {
            faceRepository.saveAll(faces);
            log.info("已迁移 {} 个人脸记录到用户 {}", faces.size(), owner.getUsername());
        }

        Map<String, Integer> fileMigration = migrateLegacyFileStructure(owner);
        summary.putAll(fileMigration);
        summary.putAll(migratePhotoPathReferences());
        filterOptionRepository.deleteAllInBatch();
        filterOptionService.updateAllFilterOptions();
        userAccountRepository.findAllIds().forEach(userStorageService::refreshStorageUsage);

        summary.put("success", true);
        summary.put("ownerUserId", owner.getId());
        summary.put("ownerUsername", owner.getUsername());
        summary.put("totalOwnershipMigrationCount",
            intValue(summary.get("migratedAlbumOwnershipCount"))
                + intValue(summary.get("migratedPhotoOwnershipCount"))
                + intValue(summary.get("migratedPersonOwnershipCount"))
                + intValue(summary.get("migratedFaceOwnershipCount"))
                + intValue(summary.get("migratedCommentOwnershipCount"))
                + intValue(summary.get("migratedTagOwnershipCount")));
        summary.put("totalPathRewriteCount",
            intValue(summary.get("rewrittenAlbumPathCount"))
                + intValue(summary.get("rewrittenPhotoPathCount"))
                + intValue(summary.get("rewrittenPhotoStorageRefCount")));
        summary.put("message", "旧数据迁移完成");
        summary.put("finishedAt", LocalDateTime.now().toString());
        systemConfigService.setLegacyMigrationCompleted(true);
        return summary;
    }

    private int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private Map<String, Integer> migrateLegacyFileStructure(UserAccount owner) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("movedTopLevelEntryCount", 0);
        result.put("movedAlbumDirectoryCount", 0);
        result.put("movedPhotoFileCount", 0);
        result.put("rewrittenAlbumPathCount", 0);
        result.put("rewrittenPhotoPathCount", 0);
        try {
            Path baseRoot = userPathService.resolvePhotoBasePath();
            Path ownerRoot = userPathService.getOwnedPhotoRoot(owner.getId());
            Files.createDirectories(ownerRoot);

            if (Files.exists(baseRoot) && Files.isDirectory(baseRoot)) {
                try (var stream = Files.list(baseRoot)) {
                    result.put("movedTopLevelEntryCount", (int) stream.filter(path -> migrateTopLevelEntry(path, ownerRoot)).count());
                }
            }

            RewriteSummary albumSummary = rewriteLegacyAlbumPaths();
            result.put("movedAlbumDirectoryCount", albumSummary.movedCount);
            result.put("rewrittenAlbumPathCount", albumSummary.rewrittenCount);

            RewriteSummary photoSummary = rewriteLegacyPhotoPaths();
            result.put("movedPhotoFileCount", photoSummary.movedCount);
            result.put("rewrittenPhotoPathCount", photoSummary.rewrittenCount);
        } catch (Exception e) {
            log.warn("旧文件目录迁移失败，保留现状继续启动: {}", e.getMessage(), e);
        }
        return result;
    }

    private Map<String, Integer> migratePhotoPathReferences() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("rewrittenPhotoStorageRefCount", 0);

        List<StorageProvider> localProviders = storageProviderRepository.findAllByOrderByPriorityAscIdAsc().stream()
            .filter(provider -> provider.getType() == StorageType.LOCAL)
            .collect(Collectors.toList());
        if (localProviders.isEmpty()) {
            return result;
        }

        List<Photo> photos = photoRepository.findByUserIdIsNotNull();

        int rewrittenCount = 0;
        List<Photo> toSave = new ArrayList<>();
        for (Photo photo : photos) {
            PathReferenceMigration originalPath = toStorageReference(photo.getOriginalPath(), photo.getUserId(), localProviders);
            PathReferenceMigration thumbnailPath = toStorageReference(photo.getThumbnailPath(), photo.getUserId(), localProviders);
            PathReferenceMigration webpPath = toStorageReference(photo.getWebpPath(), photo.getUserId(), localProviders);
            PathReferenceMigration smallThumbPath = toStorageReference(photo.getSmallThumbPath(), photo.getUserId(), localProviders);
            PathReferenceMigration mediumThumbPath = toStorageReference(photo.getMediumThumbPath(), photo.getUserId(), localProviders);
            PathReferenceMigration largeThumbPath = toStorageReference(photo.getLargeThumbPath(), photo.getUserId(), localProviders);
            PathReferenceMigration bgRemovedPath = toStorageReference(photo.getBackgroundRemovedPath(), photo.getUserId(), localProviders);

            if (!equalsNullable(originalPath.path, photo.getOriginalPath()) ||
                !equalsNullable(thumbnailPath.path, photo.getThumbnailPath()) ||
                !equalsNullable(webpPath.path, photo.getWebpPath()) ||
                !equalsNullable(smallThumbPath.path, photo.getSmallThumbPath()) ||
                !equalsNullable(mediumThumbPath.path, photo.getMediumThumbPath()) ||
                !equalsNullable(largeThumbPath.path, photo.getLargeThumbPath()) ||
                !equalsNullable(bgRemovedPath.path, photo.getBackgroundRemovedPath())) {
                photo.setOriginalPath(originalPath.path);
                photo.setThumbnailPath(thumbnailPath.path);
                photo.setWebpPath(webpPath.path);
                photo.setSmallThumbPath(smallThumbPath.path);
                photo.setMediumThumbPath(mediumThumbPath.path);
                photo.setLargeThumbPath(largeThumbPath.path);
                photo.setBackgroundRemovedPath(bgRemovedPath.path);
                if (originalPath.path != null) {
                    photo.setPathHash(sha256(originalPath.path));
                }
                toSave.add(photo);
                rewrittenCount++;
            }
        }

        if (!toSave.isEmpty()) {
            photoRepository.saveAll(toSave);
            log.info("已将 {} 张照片的路径改写为存储引用格式", rewrittenCount);
        }
        result.put("rewrittenPhotoStorageRefCount", rewrittenCount);
        return result;
    }

    private boolean migrateTopLevelEntry(Path path, Path ownerRoot) {
        try {
            String name = path.getFileName().toString();
            if (name.startsWith(".")) {
                return false;
            }
            if (path.normalize().equals(ownerRoot.normalize())) {
                return false;
            }
            if (name.matches("\\d+") && userAccountRepository.existsById(Long.parseLong(name))) {
                return false;
            }

            Path target = ownerRoot.resolve(name).normalize();
            if (!Files.exists(path) || Files.exists(target) || path.normalize().equals(target)) {
                return false;
            }

            Files.move(path, target);
            log.info("已迁移旧目录: {} -> {}", path, target);
            return true;
        } catch (Exception e) {
            log.warn("迁移旧目录失败: {}", path, e);
            return false;
        }
    }

    private RewriteSummary rewriteLegacyAlbumPaths() {
        List<Album> albums = albumRepository.findByUserIdIsNotNull().stream()
            .filter(album -> album.getPath() != null && !album.getPath().isBlank())
            .sorted(Comparator.comparingInt(album -> userPathService.normalizeAbsolutePath(album.getPath()).getNameCount()))
            .collect(Collectors.toList());

        int movedCount = 0;
        int rewrittenCount = 0;
        List<Album> albumsToSave = new ArrayList<>();
        for (Album album : albums) {
            String nextPath = rewriteLegacyPath(album.getPath(), album.getUserId());
            if (!equalsNullable(nextPath, album.getPath())) {
                if (movePathIfNecessary(album.getPath(), nextPath)) {
                    movedCount++;
                }
                album.setPath(nextPath);
                album.setPathHash(sha256(nextPath));
                albumsToSave.add(album);
                rewrittenCount++;
            }
        }

        if (!albumsToSave.isEmpty()) {
            albumRepository.saveAll(albumsToSave);
            log.info("已重写 {} 个相册路径到所属用户目录，其中搬迁了 {} 个目录", rewrittenCount, movedCount);
        }
        return new RewriteSummary(movedCount, rewrittenCount);
    }

    private RewriteSummary rewriteLegacyPhotoPaths() {
        List<Photo> photos = photoRepository.findByUserIdIsNotNull();

        int movedCount = 0;
        int rewrittenCount = 0;
        List<Photo> photosToSave = new ArrayList<>();
        for (Photo photo : photos) {
            PathMigration originalPath = rewriteLegacyFilePath(photo.getOriginalPath(), photo.getUserId());
            PathMigration thumbnailPath = rewriteLegacyFilePath(photo.getThumbnailPath(), photo.getUserId());
            PathMigration webpPath = rewriteLegacyFilePath(photo.getWebpPath(), photo.getUserId());
            PathMigration smallThumbPath = rewriteLegacyFilePath(photo.getSmallThumbPath(), photo.getUserId());
            PathMigration mediumThumbPath = rewriteLegacyFilePath(photo.getMediumThumbPath(), photo.getUserId());
            PathMigration largeThumbPath = rewriteLegacyFilePath(photo.getLargeThumbPath(), photo.getUserId());
            PathMigration bgRemovedPath = rewriteLegacyFilePath(photo.getBackgroundRemovedPath(), photo.getUserId());

            movedCount += originalPath.movedCount
                + thumbnailPath.movedCount
                + webpPath.movedCount
                + smallThumbPath.movedCount
                + mediumThumbPath.movedCount
                + largeThumbPath.movedCount
                + bgRemovedPath.movedCount;

            if (!equalsNullable(originalPath.path, photo.getOriginalPath()) ||
                !equalsNullable(thumbnailPath.path, photo.getThumbnailPath()) ||
                !equalsNullable(webpPath.path, photo.getWebpPath()) ||
                !equalsNullable(smallThumbPath.path, photo.getSmallThumbPath()) ||
                !equalsNullable(mediumThumbPath.path, photo.getMediumThumbPath()) ||
                !equalsNullable(largeThumbPath.path, photo.getLargeThumbPath()) ||
                !equalsNullable(bgRemovedPath.path, photo.getBackgroundRemovedPath())) {
                photo.setOriginalPath(originalPath.path);
                photo.setThumbnailPath(thumbnailPath.path);
                photo.setWebpPath(webpPath.path);
                photo.setSmallThumbPath(smallThumbPath.path);
                photo.setMediumThumbPath(mediumThumbPath.path);
                photo.setLargeThumbPath(largeThumbPath.path);
                photo.setBackgroundRemovedPath(bgRemovedPath.path);
                if (originalPath.path != null) {
                    photo.setPathHash(sha256(originalPath.path));
                }
                photosToSave.add(photo);
                rewrittenCount++;
            }
        }

        if (!photosToSave.isEmpty()) {
            photoRepository.saveAll(photosToSave);
            log.info("已重写 {} 张照片的路径到所属用户目录，其中搬迁了 {} 个文件", rewrittenCount, movedCount);
        }
        return new RewriteSummary(movedCount, rewrittenCount);
    }

    private String rewriteLegacyPath(String rawPath, Long userId) {
        if (rawPath == null || rawPath.isBlank()) {
            return rawPath;
        }
        try {
            Path ownedPath = userPathService.toOwnedPhotoPath(rawPath, userId).normalize();
            return userPathService.tryBuildStoragePathReference(ownedPath.toString(), userId)
                .orElseGet(() -> ownedPath.toString());
        } catch (Exception e) {
            return rawPath;
        }
    }

    private PathMigration rewriteLegacyFilePath(String rawPath, Long userId) {
        if (rawPath == null || rawPath.isBlank()) {
            return new PathMigration(rawPath, 0);
        }
        String nextPath = rewriteLegacyPath(rawPath, userId);
        int movedCount = movePathIfNecessary(rawPath, nextPath) ? 1 : 0;
        return new PathMigration(nextPath, movedCount);
    }

    private PathReferenceMigration toStorageReference(String rawPath, Long userId, List<StorageProvider> localProviders) {
        if (rawPath == null || rawPath.isBlank() || userId == null || userPathService.isStoragePathReference(rawPath)) {
            return new PathReferenceMigration(rawPath);
        }
        try {
            Path absolutePath = userPathService.normalizeAbsolutePath(rawPath);
            for (StorageProvider provider : localProviders) {
                Path providerBase = userPathService.resolveStorageProviderBaseDirectory(provider);
                Path scopedRoot = providerBase.resolve(String.valueOf(userId)).normalize();
                if (!absolutePath.startsWith(scopedRoot)) {
                    continue;
                }
                Path relativePath = scopedRoot.relativize(absolutePath);
                return new PathReferenceMigration(
                    userPathService.buildStoragePathReference(provider.getId(), userId, relativePath.toString())
                );
            }
        } catch (Exception e) {
            log.debug("将路径转换为存储引用失败，保留原值: {}", rawPath, e);
        }
        return new PathReferenceMigration(rawPath);
    }

    private boolean movePathIfNecessary(String rawPath, String nextPath) {
        if (rawPath == null || rawPath.isBlank() || nextPath == null || nextPath.isBlank()) {
            return false;
        }

        Path source = userPathService.normalizeAbsolutePath(rawPath);
        Path target = userPathService.normalizeAbsolutePath(nextPath);
        if (source.equals(target) || !Files.exists(source) || Files.exists(target)) {
            return false;
        }

        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.move(source, target);
            log.info("已迁移旧文件路径: {} -> {}", source, target);
            return true;
        } catch (Exception e) {
            log.warn("迁移旧文件路径失败: {} -> {}", source, target, e);
            return false;
        }
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算路径哈希失败", e);
        }
    }

    private boolean equalsNullable(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private static final class RewriteSummary {
        private final int movedCount;
        private final int rewrittenCount;

        private RewriteSummary(int movedCount, int rewrittenCount) {
            this.movedCount = movedCount;
            this.rewrittenCount = rewrittenCount;
        }
    }

    private static final class PathMigration {
        private final String path;
        private final int movedCount;

        private PathMigration(String path, int movedCount) {
            this.path = path;
            this.movedCount = movedCount;
        }
    }

    private static final class PathReferenceMigration {
        private final String path;

        private PathReferenceMigration(String path) {
            this.path = path;
        }
    }

    private UserAccount ensureOwnerAccount() {
        Optional<UserAccount> superAdmin = userAccountRepository.findFirstByRoleOrderByIdAsc(UserRole.SUPER_ADMIN);
        if (superAdmin.isPresent()) {
            return superAdmin.get();
        }

        Optional<UserAccount> firstUser = userAccountRepository.findFirstByOrderByIdAsc();
        if (firstUser.isPresent()) {
            return firstUser.get();
        }

        Optional<AdminUser> legacyAdmin = adminUserRepository.findByUsername("admin");
        if (legacyAdmin.isPresent()) {
            AdminUser admin = legacyAdmin.get();
            UserAccount user = new UserAccount();
            user.setUsername(admin.getUsername());
            user.setSlug("admin");
            user.setPassword(admin.getPassword());
            user.setNickname("管理员");
            user.setRole(UserRole.SUPER_ADMIN);
            user.setStatus(Boolean.TRUE.equals(admin.getEnabled()) ? UserStatus.ACTIVE : UserStatus.DISABLED);
            user.setStorageQuotaBytes(Long.MAX_VALUE);
            user.setStorageUsedBytes(0L);
            user.setProjectNameZh("光忆集");
            user.setProjectNameEn("Photo Exhibition");
            return userAccountRepository.save(user);
        }

        if (adminUserRepository.count() == 0 && userAccountRepository.count() == 0) {
            AdminUser admin = new AdminUser();
            admin.setUsername("admin");
            admin.setPassword(new BCryptPasswordEncoder().encode("admin"));
            admin.setRole("ADMIN");
            admin.setEnabled(true);
            adminUserRepository.save(admin);

            UserAccount user = new UserAccount();
            user.setUsername("admin");
            user.setSlug("admin");
            user.setPassword(admin.getPassword());
            user.setNickname("管理员");
            user.setRole(UserRole.SUPER_ADMIN);
            user.setStatus(UserStatus.ACTIVE);
            user.setStorageQuotaBytes(Long.MAX_VALUE);
            user.setStorageUsedBytes(0L);
            user.setProjectNameZh("光忆集");
            user.setProjectNameEn("Photo Exhibition");
            return userAccountRepository.save(user);
        }

        return null;
    }
}
