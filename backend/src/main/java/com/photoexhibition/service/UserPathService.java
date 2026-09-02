package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.StorageProvider;
import com.photoexhibition.entity.StorageType;
import com.photoexhibition.repository.StorageProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserPathService {

    private static final String STORAGE_REF_PREFIX = "storage://";

    @Value("${photo.scan.base-path}")
    private String photoBasePath;

    private final SystemConfigService systemConfigService;
    private final StorageProviderRepository storageProviderRepository;

    public Path resolvePhotoBasePath() {
        return resolveConfiguredPath(photoBasePath);
    }

    public Path resolveUserDataBasePath() {
        return resolveConfiguredPath(systemConfigService.getUserDataRoot());
    }

    public Path getScopedPhotoRoot(UserAccount user) {
        Path base = resolvePhotoBasePath();
        // 超级管理员始终使用全局根目录。普通用户只有在多用户模式下
        // 才会进入 base/<uid>，关闭多用户后必须保留历史目录中的 UID 段。
        if (user == null || user.getRole() == com.photoexhibition.entity.UserRole.SUPER_ADMIN
            || !systemConfigService.isMultiUserEnabled()) {
            return base;
        }
        return base.resolve(String.valueOf(user.getId())).normalize();
    }

    public Path getScopedUserDataRoot(UserAccount user) {
        Path base = resolveUserDataBasePath();
        if (user == null) {
            return base;
        }
        return base.resolve(String.valueOf(user.getId())).normalize();
    }

    public Path getOwnedPhotoRoot(Long userId) {
        Path base = resolvePhotoBasePath();
        if (userId == null) {
            return base;
        }
        return base.resolve(String.valueOf(userId)).normalize();
    }

    public Path getRelativePhotoPath(String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) {
            return null;
        }
        try {
            Path base = resolvePhotoBasePath();
            Path full = Paths.get(absolutePath).toAbsolutePath().normalize();
            if (!full.startsWith(base)) {
                return null;
            }
            return base.relativize(full);
        } catch (Exception e) {
            return null;
        }
    }

    public Path stripLeadingUserSegment(Path relativePath) {
        return stripLeadingUserSegment(relativePath, null);
    }

    public Path resolveScopedPath(String requestedPath, UserAccount user) {
        Path scopedRoot = getScopedPhotoRoot(user);
        if (requestedPath == null || requestedPath.trim().isEmpty()) {
            return scopedRoot;
        }

        Path candidate = Paths.get(requestedPath.trim());
        if (!candidate.isAbsolute()) {
            String clean = requestedPath.startsWith("./") ? requestedPath.substring(2) : requestedPath;
            Path relative = Paths.get(clean).normalize();
            if (shouldStripUserSegment(user)) {
                relative = stripLeadingUserSegment(relative, user.getId());
            }
            candidate = scopedRoot.resolve(relative);
        } else {
            // 前端展示路径形如 /1/分类/相册，并不是文件系统根目录下的
            // /1/...。仅当绝对路径确实位于照片根目录（或已存在于磁盘）
            // 时按绝对路径处理，否则将其视为相对照片根目录的展示路径。
            Path absolute = candidate.toAbsolutePath().normalize();
            Path photoBase = resolvePhotoBasePath();
            if (absolute.startsWith(photoBase) || Files.exists(absolute)) {
                candidate = absolute;
            } else {
                String clean = requestedPath.trim().replace('\\', '/');
                while (clean.startsWith("/")) clean = clean.substring(1);
                Path relative = Paths.get(clean).normalize();
                if (shouldStripUserSegment(user)) {
                    relative = stripLeadingUserSegment(relative, user.getId());
                }
                candidate = scopedRoot.resolve(relative);
            }
        }

        candidate = candidate.toAbsolutePath().normalize();
        if (!candidate.startsWith(scopedRoot)) {
            throw new IllegalArgumentException("路径超出当前用户可操作范围");
        }
        return candidate;
    }

    private boolean shouldStripUserSegment(UserAccount user) {
        return user != null
            && user.getRole() != com.photoexhibition.entity.UserRole.SUPER_ADMIN
            && systemConfigService.isMultiUserEnabled();
    }

    public Long extractUserIdFromPath(String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) {
            return null;
        }

        StoragePathReference reference = parseStoragePathReference(absolutePath);
        if (reference != null) {
            return reference.getUserId();
        }

        try {
            Path base = resolvePhotoBasePath();
            Path full = Paths.get(absolutePath).toAbsolutePath().normalize();
            if (!full.startsWith(base)) {
                return null;
            }
            Path relative = base.relativize(full);
            if (relative.getNameCount() == 0) {
                return null;
            }

            String first = relative.getName(0).toString();
            if (first.matches("\\d+")) {
                return Long.parseLong(first);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public Path stripLeadingUserSegment(Path relativePath, Long userId) {
        if (relativePath == null) {
            return null;
        }
        if (relativePath.getNameCount() > 0) {
            String first = relativePath.getName(0).toString();
            if (first.matches("\\d+") && (userId == null || first.equals(String.valueOf(userId)))) {
                if (relativePath.getNameCount() == 1) {
                    return Paths.get("");
                }
                return relativePath.subpath(1, relativePath.getNameCount());
            }
        }
        return relativePath;
    }

    public String toRelativePhotoPath(String absolutePath, boolean keepUserSegment) {
        if (absolutePath == null || absolutePath.isBlank()) {
            return absolutePath;
        }
        StoragePathReference reference = parseStoragePathReference(absolutePath);
        if (reference != null) {
            String normalized = reference.getRelativePath().toString().replace("\\", "/");
            if (keepUserSegment) {
                Long userId = reference.getUserId();
                if (userId != null) {
                    normalized = userId + (normalized.isEmpty() ? "" : "/" + normalized);
                }
            }
            if (!normalized.startsWith("/")) {
                normalized = "/" + normalized;
            }
            return normalized;
        }
        Path relative = getRelativePhotoPath(absolutePath);
        if (relative == null) {
            return absolutePath;
        }
        if (!keepUserSegment) {
            relative = stripLeadingUserSegment(relative);
        }
        String normalized = relative.toString().replace("\\", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    public String toTenantRelativePhotoPath(String absolutePath) {
        String extracted = extractTenantRelativePhotoPath(absolutePath);
        if (extracted == null) {
            return absolutePath;
        }
        return extracted;
    }

    public String sanitizeVisibleText(String value) {
        if (value == null || value.isBlank()) {
            return "系统异常";
        }
        try {
            String displayPath = toDisplayPath(value, true);
            if (!Objects.equals(value, displayPath)) {
                return displayPath;
            }
        } catch (Exception ignored) {
        }

        String normalized = value.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        if (index >= 0 && index + 1 < normalized.length()) {
            return normalized.substring(index + 1);
        }
        return normalized;
    }

    public String toDisplayPath(String rawPath, boolean keepUserSegment) {
        if (rawPath == null || rawPath.isBlank()) {
            return rawPath;
        }
        String relativePath = toRelativePhotoPath(rawPath, keepUserSegment);
        if (!Objects.equals(rawPath, relativePath)) {
            return relativePath;
        }

        String extracted = extractScopedSubpathFromAnyPath(rawPath, keepUserSegment);
        if (extracted != null && !extracted.isBlank()) {
            return extracted;
        }

        return rawPath;
    }

    public String extractTenantRelativePhotoPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        StoragePathReference reference = parseStoragePathReference(path);
        if (reference != null) {
            return reference.getRelativePath().toString().replace("\\", "/");
        }
        Path relative = stripLeadingUserSegment(getRelativePhotoPath(path));
        if (relative == null) {
            return null;
        }
        return relative.toString().replace("\\", "/");
    }

    private String extractScopedSubpathFromAnyPath(String path, boolean keepUserSegment) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.replace('\\', '/').trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.matches("^[A-Za-z]:/.*")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            return "/";
        }

        String[] segments = normalized.split("/+");
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (!segment.matches("\\d+")) {
                continue;
            }
            int start = keepUserSegment ? i : i + 1;
            if (start >= segments.length) {
                return keepUserSegment ? "/" + segment : "/";
            }
            return "/" + String.join("/", java.util.Arrays.copyOfRange(segments, start, segments.length));
        }

        if (path.startsWith("/") || path.matches("^[A-Za-z]:\\\\.*") || path.matches("^[A-Za-z]:/.*")) {
            return "/" + normalized;
        }
        return null;
    }

    public Path toOwnedPhotoPath(String absolutePath, Long userId) {
        if (absolutePath == null || absolutePath.isBlank()) {
            return null;
        }
        StoragePathReference reference = parseStoragePathReference(absolutePath);
        if (reference != null) {
            return getOwnedPhotoRoot(userId).resolve(reference.getRelativePath()).normalize();
        }
        Path full = Paths.get(absolutePath).toAbsolutePath().normalize();
        Path relative = getRelativePhotoPath(absolutePath);
        if (relative == null) {
            return full;
        }
        relative = stripLeadingUserSegment(relative);
        return getOwnedPhotoRoot(userId).resolve(relative).normalize();
    }

    public int calculateLogicalAlbumDepth(String absolutePath) {
        StoragePathReference reference = parseStoragePathReference(absolutePath);
        if (reference != null) {
            return Math.max(0, reference.getRelativePath().getNameCount() - 2);
        }
        Path relative = stripLeadingUserSegment(getRelativePhotoPath(absolutePath));
        if (relative == null) {
            return 0;
        }
        return Math.max(0, relative.getNameCount() - 2);
    }

    public String buildStoragePathReference(Long storageProviderId, Long userId, String relativePath) {
        if (storageProviderId == null) {
            throw new IllegalArgumentException("storageProviderId 不能为空");
        }
        String normalized = normalizeReferencePath(relativePath);
        StringBuilder builder = new StringBuilder(STORAGE_REF_PREFIX)
            .append(storageProviderId)
            .append('/');
        if (userId != null) {
            builder.append(userId).append('/');
        }
        builder.append(normalized);
        return builder.toString();
    }

    public StoragePathReference parseStoragePathReference(String path) {
        if (path == null || !path.startsWith(STORAGE_REF_PREFIX)) {
            return null;
        }
        String payload = path.substring(STORAGE_REF_PREFIX.length());
        String[] parts = payload.split("/", 3);
        if (parts.length < 2) {
            return null;
        }
        Long storageProviderId = parseLong(parts[0]);
        if (storageProviderId == null) {
            return null;
        }

        Long userId = null;
        String relativePart;
        if (parts.length == 2) {
            relativePart = parts[1];
        } else {
            Long parsedUserId = parseLong(parts[1]);
            if (parsedUserId != null) {
                userId = parsedUserId;
                relativePart = parts[2];
            } else {
                relativePart = parts[1] + "/" + parts[2];
            }
        }

        Path relativePath = Paths.get(normalizeReferencePath(relativePart)).normalize();
        return new StoragePathReference(storageProviderId, userId, relativePath);
    }

    public boolean isStoragePathReference(String path) {
        return parseStoragePathReference(path) != null;
    }

    public Path resolveStoredPhotoPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        StoragePathReference reference = parseStoragePathReference(path);
        if (reference == null) {
            return Paths.get(path).toAbsolutePath().normalize();
        }
        StorageProvider provider = storageProviderRepository.findById(reference.getStorageProviderId())
            .orElseThrow(() -> new IllegalArgumentException("存储提供者不存在: " + reference.getStorageProviderId()));
        if (provider.getType() != StorageType.LOCAL) {
            throw new IllegalArgumentException("当前路径引用指向非本地存储，无法映射为本地绝对路径");
        }
        Path providerRoot = resolveStorageProviderLocalRoot(provider, reference.getUserId());
        return providerRoot.resolve(reference.getRelativePath()).normalize();
    }

    public Optional<Path> tryResolveLocalStoredPhotoPath(String path) {
        try {
            return Optional.ofNullable(resolveStoredPhotoPath(path));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean isResolvableLocalStoredPhotoPath(String path) {
        return tryResolveLocalStoredPhotoPath(path).isPresent();
    }

    public Optional<String> tryBuildStoragePathReference(String absolutePath, Long userId) {
        if (absolutePath == null || absolutePath.isBlank()) {
            return Optional.empty();
        }
        if (isStoragePathReference(absolutePath)) {
            return Optional.of(absolutePath);
        }
        try {
            Path fullPath = Paths.get(absolutePath).toAbsolutePath().normalize();
            List<StorageProvider> localProviders = storageProviderRepository.findByTypeOrderByPriorityAscIdAsc(StorageType.LOCAL).stream()
                .sorted(Comparator.comparingInt((StorageProvider provider) ->
                    resolveStorageProviderLocalRoot(provider, userId).getNameCount()).reversed())
                .collect(Collectors.toList());

            for (StorageProvider provider : localProviders) {
                Path scopedRoot = resolveStorageProviderLocalRoot(provider, userId);
                if (!fullPath.startsWith(scopedRoot)) {
                    continue;
                }
                Path relativePath = scopedRoot.relativize(fullPath);
                return Optional.of(buildStoragePathReference(provider.getId(), userId, relativePath.toString()));
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private String normalizeReferencePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        String normalized = relativePath.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Path resolveStorageProviderLocalRoot(StorageProvider provider, Long userId) {
        Path base = resolveStorageProviderBaseDirectory(provider);
        if (userId != null) {
            return base.resolve(String.valueOf(userId)).normalize();
        }
        return base;
    }

    public Path resolveStorageProviderBaseDirectory(StorageProvider provider) {
        if (provider == null) {
            return resolvePhotoBasePath();
        }
        String baseDirectory = provider.getBaseDirectory();
        if (baseDirectory == null || baseDirectory.isBlank()) {
            baseDirectory = systemConfigService.getLocalStorageRoot();
        }
        Path configuredBase = resolveConfiguredPath(baseDirectory);
        if (provider.getType() == StorageType.LOCAL
            && sameConfiguredPath(baseDirectory, systemConfigService.getLocalStorageRoot())) {
            Path photoBase = resolvePhotoBasePath();
            if (Files.isDirectory(photoBase) && !photoBase.equals(configuredBase)) {
                return photoBase;
            }
        }
        return configuredBase;
    }

    public Path normalizeAbsolutePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        StoragePathReference reference = parseStoragePathReference(rawPath);
        if (reference != null) {
            return resolveStoredPhotoPath(rawPath);
        }
        return Paths.get(rawPath).toAbsolutePath().normalize();
    }

    private Path resolveConfiguredPath(String rawPath) {
        Path base = Paths.get(rawPath);
        if (!base.isAbsolute()) {
            String projectRoot = System.getProperty("user.dir");
            if (projectRoot.endsWith("backend")) {
                projectRoot = Paths.get(projectRoot).getParent().toString();
            }
            String clean = rawPath.startsWith("./") ? rawPath.substring(2) : rawPath;
            base = Paths.get(projectRoot, clean);
        }
        return base.toAbsolutePath().normalize();
    }

    private boolean sameConfiguredPath(String left, String right) {
        String normalizedLeft = normalizeConfigPath(left);
        String normalizedRight = normalizeConfigPath(right);
        return Objects.equals(normalizedLeft, normalizedRight);
    }

    private String normalizeConfigPath(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replace("\\", "/");
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? null : normalized;
    }

    public static class StoragePathReference {
        private final Long storageProviderId;
        private final Long userId;
        private final Path relativePath;

        public StoragePathReference(Long storageProviderId, Long userId, Path relativePath) {
            this.storageProviderId = storageProviderId;
            this.userId = userId;
            this.relativePath = relativePath == null ? Paths.get("") : relativePath.normalize();
        }

        public Long getStorageProviderId() {
            return storageProviderId;
        }

        public Long getUserId() {
            return userId;
        }

        public Path getRelativePath() {
            return relativePath;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StoragePathReference)) {
                return false;
            }
            StoragePathReference other = (StoragePathReference) obj;
            return Objects.equals(storageProviderId, other.storageProviderId)
                && Objects.equals(userId, other.userId)
                && Objects.equals(relativePath, other.relativePath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(storageProviderId, userId, relativePath);
        }
    }
}
