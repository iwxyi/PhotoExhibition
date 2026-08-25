package com.photoexhibition.controller;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.OperationType;
import com.photoexhibition.service.AuthService;
import com.photoexhibition.service.FolderService;
import com.photoexhibition.service.OperationLogService;
import com.photoexhibition.service.PhotoDedupService;
import com.photoexhibition.service.StorageProviderService;
import com.photoexhibition.service.UserPathService;
import com.photoexhibition.service.UserStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/admin/folders")
@RequiredArgsConstructor
public class FolderController {

    private static final Pattern EMBEDDED_PATH_PATTERN =
        Pattern.compile("(storage://[^\\s,;]+|[A-Za-z]:\\\\[^\\s,;]+|/(?:[^\\s,;])+)");

    private final FolderService folderService;
    private final AuthService authService;
    private final UserStorageService userStorageService;
    private final OperationLogService operationLogService;
    private final PhotoDedupService photoDedupService;
    private final StorageProviderService storageProviderService;
    private final UserPathService userPathService;

    @GetMapping("/base-path")
    public ResponseEntity<Map<String, Object>> getBasePath(@RequestHeader("Authorization") String authorization,
                                                           @RequestParam(required = false) Long providerId) {
        UserAccount user = requireCurrentUser(authorization);
        return ResponseEntity.ok(resolveStorageContext(user, normalizeProviderId(user, providerId)).toResponse());
    }

    @PostMapping("/move")
    public ResponseEntity<Map<String, Object>> moveFolder(@RequestHeader("Authorization") String authorization,
                                                          HttpServletRequest request,
                                                          @RequestParam String source,
                                                          @RequestParam String target,
                                                          @RequestParam(required = false) Long providerId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            folderService.moveFolder(resolveScopedPath(source, user, normalizedProviderId).toString(), resolveScopedPath(target, user, normalizedProviderId).toString());
            operationLogService.log(user, OperationType.UPDATE, "FOLDER", null, target,
                metadata("source", source, "target", target, "action", "moveFolder", "providerId", normalizedProviderId), request.getRemoteAddr());
            resp.put("message", "移动完成");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage()));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteFolder(@RequestHeader("Authorization") String authorization,
                                                            HttpServletRequest request,
                                                            @RequestParam String path,
                                                            @RequestParam(required = false) Long providerId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            StorageProviderService.BrowserStorageContext storageContext = resolveStorageContext(user, normalizedProviderId);
            folderService.deleteFolder(resolveScopedPath(path, user, normalizedProviderId).toString());
            if (isUsageRefreshableStorage(storageContext.getProvider().getType())) {
                userStorageService.refreshStorageUsage(user.getId());
            }
            operationLogService.log(user, OperationType.DELETE, "FOLDER", null, path,
                metadata("path", path, "action", "deleteFolder", "providerId", normalizedProviderId), request.getRemoteAddr());
            resp.put("message", "删除完成");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage()));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list(@RequestHeader("Authorization") String authorization,
                                                    @RequestParam(required = false) String path,
                                                    @RequestParam(required = false) Long providerId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            StorageProviderService.BrowserStorageContext storageContext = resolveStorageContext(user, normalizedProviderId);
            String target = resolveScopedPath(path, user, normalizedProviderId).toString();
            List<String> dirs = folderService.listDirectories(target, storageContext.getScopedRoot());
            resp.put("base", toBrowserPath(Path.of(target), storageContext.getScopedRoot()));
            resp.put("dirs", dirs);
            resp.putAll(storageContext.toResponse());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage()));
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 文件浏览器：列出指定路径下的文件和文件夹
     */
    @GetMapping("/browser/list")
    public ResponseEntity<Map<String, Object>> browserList(@RequestHeader("Authorization") String authorization,
                                                           @RequestParam(required = false) String path,
                                                           @RequestParam(required = false) Long providerId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            String target = resolveScopedPath(path, user, normalizedProviderId).toString();
            StorageProviderService.BrowserStorageContext storageContext = resolveStorageContext(user, normalizedProviderId);
            Map<String, Object> result = folderService.listFilesAndDirectories(target, storageContext.getProvider(), user, storageContext.getScopedRoot());
            result.putAll(storageContext.toResponse());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage()));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @GetMapping("/upload-precheck")
    public ResponseEntity<Map<String, Object>> uploadPrecheck(@RequestHeader("Authorization") String authorization,
                                                              @RequestParam String contentHash) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            resp.putAll(photoDedupService.precheckContentHash(user, contentHash));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage()));
            return ResponseEntity.status(400).body(resp);
        }
    }

    /**
     * 创建文件夹
     */
    @PostMapping("/browser/create")
    public ResponseEntity<Map<String, Object>> createDirectory(@RequestHeader("Authorization") String authorization,
                                                               HttpServletRequest request,
                                                               @RequestParam String path,
                                                               @RequestParam String name,
                                                               @RequestParam(required = false) Long providerId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            StorageProviderService.BrowserStorageContext storageContext = resolveStorageContext(user, normalizedProviderId);
            java.nio.file.Path parentPath = resolveScopedPath(path, user, normalizedProviderId);
            java.nio.file.Path fullPath = parentPath.resolve(name);
            folderService.createDirectory(fullPath.toString(), storageContext.getProvider(), user, storageContext.getScopedRoot());
            operationLogService.log(user, OperationType.UPDATE, "FOLDER", null, fullPath.toString(),
                metadata("parent", path, "name", name, "action", "createDirectory", "providerId", normalizedProviderId), request.getRemoteAddr());
            resp.put("message", "创建成功");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage()));
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 重命名文件或文件夹
     */
    @PostMapping("/browser/rename")
    public ResponseEntity<Map<String, Object>> renameItem(@RequestHeader("Authorization") String authorization,
                                                          HttpServletRequest request,
                                                          @RequestParam String path,
                                                          @RequestParam String newName,
                                                          @RequestParam(required = false) Long providerId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            StorageProviderService.BrowserStorageContext storageContext = resolveStorageContext(user, normalizedProviderId);
            folderService.renameItem(resolveScopedPath(path, user, normalizedProviderId).toString(), newName,
                storageContext.getProvider(), user, storageContext.getScopedRoot());
            operationLogService.log(user, OperationType.UPDATE, "FILE_BROWSER_ITEM", null, path,
                metadata("path", path, "newName", newName, "action", "rename", "providerId", normalizedProviderId), request.getRemoteAddr());
            resp.put("message", "重命名成功");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage()));
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 删除文件或文件夹（复用现有方法）
     */
    @DeleteMapping("/browser/delete")
    public ResponseEntity<Map<String, Object>> deleteItem(@RequestHeader("Authorization") String authorization,
                                                          HttpServletRequest request,
                                                          @RequestParam String path,
                                                          @RequestParam(required = false) Long providerId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            StorageProviderService.BrowserStorageContext storageContext = resolveStorageContext(user, normalizedProviderId);
            Path resolvedPath = resolveScopedPath(path, user, normalizedProviderId);
            folderService.deleteItems(List.of(resolvedPath.toString()), storageContext.getProvider(), user, storageContext.getScopedRoot());
            if (isUsageRefreshableStorage(storageContext.getProvider().getType())) {
                userStorageService.refreshStorageUsage(user.getId());
            }
            operationLogService.log(user, OperationType.DELETE, "FILE_BROWSER_ITEM", null, path,
                metadata("path", path, "action", "deleteItem", "providerId", normalizedProviderId), request.getRemoteAddr());
            resp.put("message", "删除成功");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage()));
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 批量删除文件或文件夹
     */
    @DeleteMapping("/browser/delete-items")
    public ResponseEntity<Map<String, Object>> deleteItems(@RequestHeader("Authorization") String authorization,
                                                           HttpServletRequest request,
                                                           @RequestParam List<String> paths,
                                                           @RequestParam(required = false) Long providerId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            StorageProviderService.BrowserStorageContext storageContext = resolveStorageContext(user, normalizedProviderId);
            folderService.deleteItems(
                paths.stream().map(path -> resolveScopedPath(path, user, normalizedProviderId).toString()).collect(Collectors.toList()),
                storageContext.getProvider(),
                user,
                storageContext.getScopedRoot()
            );
            if (isUsageRefreshableStorage(storageContext.getProvider().getType())) {
                userStorageService.refreshStorageUsage(user.getId());
            }
            operationLogService.log(user, OperationType.DELETE, "FILE_BROWSER_BATCH", null, null,
                metadata("paths", paths, "action", "deleteItems", "providerId", normalizedProviderId), request.getRemoteAddr());
            resp.put("message", "删除成功");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage()));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @PostMapping("/browser/bind-album")
    public ResponseEntity<Map<String, Object>> bindDirectoryAlbum(@RequestHeader("Authorization") String authorization,
                                                                  HttpServletRequest request,
                                                                  @RequestParam String path,
                                                                  @RequestParam(required = false) Long providerId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            StorageProviderService.BrowserStorageContext storageContext = resolveStorageContext(user, normalizedProviderId);
            Path resolvedPath = resolveScopedPath(path, user, normalizedProviderId);
            resp.putAll(folderService.bindDirectoryAlbum(resolvedPath.toString(), storageContext.getProvider(), user, storageContext.getScopedRoot()));
            operationLogService.log(user, OperationType.UPDATE, "ALBUM", null, path,
                metadata("path", path, "action", "bindDirectoryAlbum", "providerId", normalizedProviderId), request.getRemoteAddr());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage()));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @PostMapping("/browser/delete-preview")
    public ResponseEntity<Map<String, Object>> previewDeleteItems(@RequestHeader("Authorization") String authorization,
                                                                  @RequestBody(required = false) Map<String, Object> request,
                                                                  @RequestParam(required = false) Long providerId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            StorageProviderService.BrowserStorageContext storageContext = resolveStorageContext(user, normalizedProviderId);
            List<String> paths = request != null && request.get("paths") instanceof List<?>
                ? ((List<?>) request.get("paths")).stream().map(String::valueOf).collect(Collectors.toList())
                : List.of();
            Map<String, Object> result = folderService.previewDeleteItems(
                paths.stream().map(path -> resolveScopedPath(path, user, normalizedProviderId).toString()).collect(Collectors.toList()),
                storageContext.getProvider(),
                user,
                storageContext.getScopedRoot()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage()));
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 批量移动文件或文件夹
     */
    @PostMapping("/browser/move-items")
    public ResponseEntity<Map<String, Object>> moveItems(@RequestHeader("Authorization") String authorization,
                                                         HttpServletRequest request,
                                                         @RequestParam List<String> paths,
                                                         @RequestParam String target,
                                                         @RequestParam(required = false) Long providerId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            StorageProviderService.BrowserStorageContext storageContext = resolveStorageContext(user, normalizedProviderId);
            folderService.moveItems(
                paths.stream().map(path -> resolveScopedPath(path, user, normalizedProviderId).toString()).collect(Collectors.toList()),
                resolveScopedPath(target, user, normalizedProviderId).toString(),
                storageContext.getProvider(),
                user,
                storageContext.getScopedRoot()
            );
            operationLogService.log(user, OperationType.UPDATE, "FILE_BROWSER_BATCH", null, target,
                metadata("paths", paths, "target", target, "action", "moveItems", "providerId", normalizedProviderId), request.getRemoteAddr());
            resp.put("message", "移动成功");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage()));
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 上传文件或文件夹（支持文件夹内路径）
     */
    @PostMapping("/browser/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestHeader("Authorization") String authorization,
                                                      HttpServletRequest request,
                                                      @RequestParam("files") List<MultipartFile> files,
                                                      @RequestParam String target,
                                                      @RequestParam(required = false) List<String> relativePaths,
                                                      @RequestParam(required = false) Long providerId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            FolderService.UploadResult result = folderService.uploadFiles(
                files,
                resolveScopedPath(target, user, normalizedProviderId).toString(),
                relativePaths,
                user,
                normalizedProviderId
            );
            operationLogService.log(user, OperationType.UPLOAD, "FILE_BROWSER_UPLOAD", null, target,
                metadata(
                    "target", target,
                    "saved", result.getSaved(),
                    "fileCount", files.size(),
                    "relativePaths", relativePaths,
                    "providerId", normalizedProviderId,
                    "scanQueued", result.isScanQueued(),
                    "scanMessage", result.getScanMessage()
                ),
                request.getRemoteAddr()
            );
            resp.put("message", result.getScanMessage());
            resp.put("saved", result.getSaved());
            resp.put("scanQueued", result.isScanQueued());
            resp.put("scanMessage", result.getScanMessage());
            resp.put("storageProviderId", result.getStorageProviderId());
            resp.put("storageProviderName", result.getStorageProviderName());
            resp.put("storageProviderType", result.getStorageProviderType());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage()));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @GetMapping("/browser/open-url")
    public ResponseEntity<Map<String, Object>> openUrl(@RequestHeader("Authorization") String authorization,
                                                       @RequestParam String path,
                                                       @RequestParam(required = false) Long providerId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            StorageProviderService.BrowserStorageContext storageContext = resolveStorageContext(user, normalizedProviderId);
            String url = folderService.resolvePreviewUrl(
                resolveScopedPath(path, user, normalizedProviderId).toString(),
                storageContext.getProvider(),
                user,
                storageContext.getScopedRoot()
            );
            resp.put("url", url);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage()));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @GetMapping("/browser/preview")
    public ResponseEntity<byte[]> previewFile(@RequestHeader("Authorization") String authorization,
                                              @RequestParam String path,
                                              @RequestParam(required = false) Long providerId) {
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            StorageProviderService.BrowserStorageContext storageContext = resolveStorageContext(user, normalizedProviderId);
            var downloadedFile = folderService.downloadPreviewFile(
                resolveScopedPath(path, user, normalizedProviderId).toString(),
                storageContext.getProvider(),
                user,
                storageContext.getScopedRoot()
            );

            MediaType mediaType;
            try {
                mediaType = downloadedFile.getContentType() != null
                    ? MediaType.parseMediaType(downloadedFile.getContentType())
                    : MediaType.APPLICATION_OCTET_STREAM;
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            String filename = sanitizeFilename(downloadedFile.getFilename(), "preview");
            return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition("inline", filename))
                .body(downloadedFile.getBytes());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .contentType(MediaType.TEXT_PLAIN)
                .body(sanitizeErrorMessage(e.getMessage(), "预览失败").getBytes(StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/browser/download")
    public ResponseEntity<byte[]> downloadFile(@RequestHeader("Authorization") String authorization,
                                               @RequestParam String path,
                                               @RequestParam(required = false) Long providerId) {
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long normalizedProviderId = normalizeProviderId(user, providerId);
            StorageProviderService.BrowserStorageContext storageContext = resolveStorageContext(user, normalizedProviderId);
            var downloadedFile = folderService.downloadPreviewFile(
                resolveScopedPath(path, user, normalizedProviderId).toString(),
                storageContext.getProvider(),
                user,
                storageContext.getScopedRoot()
            );

            MediaType mediaType;
            try {
                mediaType = downloadedFile.getContentType() != null
                    ? MediaType.parseMediaType(downloadedFile.getContentType())
                    : MediaType.APPLICATION_OCTET_STREAM;
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            String filename = sanitizeFilename(downloadedFile.getFilename(), "download");
            return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition("attachment", filename))
                .body(downloadedFile.getBytes());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .contentType(MediaType.TEXT_PLAIN)
                .body(sanitizeErrorMessage(e.getMessage(), "下载失败").getBytes(StandardCharsets.UTF_8));
        }
    }

    private String sanitizeFilename(String filename, String fallback) {
        String safe = filename == null ? "" : filename
            .replace("\\", "_")
            .replace("/", "_")
            .replace("\"", "")
            .replace("\r", "")
            .replace("\n", "")
            .trim();
        return safe.isEmpty() ? fallback : safe;
    }

    private boolean isUsageRefreshableStorage(com.photoexhibition.entity.StorageType type) {
        return type == com.photoexhibition.entity.StorageType.LOCAL
            || type == com.photoexhibition.entity.StorageType.SFTP
            || type == com.photoexhibition.entity.StorageType.SMB
            || type == com.photoexhibition.entity.StorageType.NFS;
    }

    private String buildContentDisposition(String dispositionType, String filename) {
        return dispositionType + "; filename=\"" + filename + "\"; filename*=UTF-8''"
            + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private UserAccount requireCurrentUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new RuntimeException("未授权，请先登录");
        }
        return authService.getCurrentUserEntity(authorization.substring(7));
    }

    private StorageProviderService.BrowserStorageContext resolveStorageContext(UserAccount user, Long providerId) {
        return storageProviderService.resolveBrowserStorage(user, providerId);
    }

    private Long normalizeProviderId(UserAccount user, Long providerId) {
        return storageProviderService.normalizeRequestedProviderId(user, providerId);
    }

    private Path resolveScopedPath(String requestedPath, UserAccount user, Long providerId) {
        Path scopedRoot = resolveStorageContext(user, providerId).getScopedRoot();
        if (requestedPath == null || requestedPath.trim().isEmpty()) {
            return scopedRoot;
        }

        String normalizedRequest = requestedPath.trim().replace("\\", "/");
        Path candidate;
        if ("/".equals(normalizedRequest)) {
            candidate = scopedRoot;
        } else if (normalizedRequest.startsWith("/")) {
            Path relative = Path.of(normalizedRequest.substring(1)).normalize();
            if (user != null) {
                relative = userPathService.stripLeadingUserSegment(relative, user.getId());
            }
            candidate = scopedRoot.resolve(relative);
        } else {
            Path rawCandidate = Path.of(requestedPath.trim());
            if (!rawCandidate.isAbsolute()) {
                String clean = requestedPath.startsWith("./") ? requestedPath.substring(2) : requestedPath;
                Path relative = Path.of(clean).normalize();
                if (user != null) {
                    relative = userPathService.stripLeadingUserSegment(relative, user.getId());
                }
                candidate = scopedRoot.resolve(relative);
            } else {
                candidate = rawCandidate;
            }
        }

        candidate = candidate.toAbsolutePath().normalize();
        if (!candidate.startsWith(scopedRoot)) {
            throw new IllegalArgumentException("路径超出当前用户可操作范围");
        }
        return candidate;
    }

    private Map<String, Object> metadata(Object... keyValues) {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            values.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return values;
    }

    private String toBrowserPath(Path path, Path scopedRoot) {
        if (path == null) {
            return null;
        }
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (scopedRoot != null) {
            Path normalizedRoot = scopedRoot.toAbsolutePath().normalize();
            if (normalizedPath.startsWith(normalizedRoot)) {
                Path relative = normalizedRoot.relativize(normalizedPath);
                String normalized = relative.toString().replace("\\", "/");
                return normalized.isEmpty() ? "/" : "/" + normalized;
            }
        }
        return userPathService.toDisplayPath(normalizedPath.toString(), true);
    }

    private String sanitizeErrorMessage(String message) {
        return sanitizeErrorMessage(message, "操作失败");
    }

    private String sanitizeErrorMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        Matcher matcher = EMBEDDED_PATH_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        boolean replaced = false;
        while (matcher.find()) {
            String candidate = matcher.group(1);
            String sanitizedCandidate = userPathService.toDisplayPath(candidate, true);
            if (!candidate.equals(sanitizedCandidate)) {
                replaced = true;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(sanitizedCandidate));
        }
        matcher.appendTail(buffer);
        return replaced ? buffer.toString() : message;
    }
}
