package com.photoexhibition.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.DeleteObjectsRequest;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.StorageProvider;
import com.photoexhibition.entity.StorageType;
import com.photoexhibition.entity.UserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageUploadService {

    private final StorageProviderService storageProviderService;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;
    private final UserPathService userPathService;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public long resolveExistingSize(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        if (isLocalBackedProvider(provider)) {
            Path target = resolveLocalFilePath(provider, user, relativePath);
            return Files.exists(target) && Files.isRegularFile(target) ? Files.size(target) : 0L;
        }
        if (provider.getType() == StorageType.WEBDAV) {
            return resolveWebDavExistingSize(provider, user, relativePath);
        }
        if (provider.getType() == StorageType.FTP) {
            return resolveFtpExistingSize(provider, user, relativePath);
        }
        if (provider.getType() == StorageType.COS) {
            return resolveCosExistingSize(provider, user, relativePath);
        }
        if (provider.getType() == StorageType.AZURE_BLOB) {
            return resolveAzureBlobExistingSize(provider, user, relativePath);
        }
        if (provider.getType() == StorageType.DROPBOX) {
            return resolveDropboxExistingSize(provider, user, relativePath);
        }
        if (provider.getType() == StorageType.ONEDRIVE) {
            return resolveOneDriveExistingSize(provider, user, relativePath);
        }
        if (provider.getType() == StorageType.UPYUN) {
            return resolveUpyunExistingSize(provider, user, relativePath);
        }
        if (isS3CompatibleProvider(provider)) {
            return resolveS3ExistingSize(provider, user, relativePath);
        }
        return 0L;
    }

    public void storeFile(StorageProvider provider, UserAccount user, Path relativePath, MultipartFile file) throws Exception {
        if (isLocalBackedProvider(provider)) {
            Path target = resolveLocalFilePath(provider, user, relativePath);
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
            return;
        }
        if (provider.getType() == StorageType.WEBDAV) {
            uploadToWebDav(provider, user, relativePath, file);
            return;
        }
        if (provider.getType() == StorageType.FTP) {
            uploadToFtp(provider, user, relativePath, file);
            return;
        }
        if (provider.getType() == StorageType.COS) {
            uploadToCos(provider, user, relativePath, file);
            return;
        }
        if (provider.getType() == StorageType.AZURE_BLOB) {
            uploadToAzureBlob(provider, user, relativePath, file);
            return;
        }
        if (provider.getType() == StorageType.DROPBOX) {
            uploadToDropbox(provider, user, relativePath, file);
            return;
        }
        if (provider.getType() == StorageType.ONEDRIVE) {
            uploadToOneDrive(provider, user, relativePath, file);
            return;
        }
        if (provider.getType() == StorageType.UPYUN) {
            uploadToUpyun(provider, user, relativePath, file);
            return;
        }
        if (isS3CompatibleProvider(provider)) {
            uploadToS3Compatible(provider, user, relativePath, file);
            return;
        }
        throw new IllegalArgumentException("当前存储类型暂不支持真实上传: " + provider.getType());
    }

    public void storeDownloadedFile(StorageProvider provider, UserAccount user, Path relativePath, DownloadedFile file) throws Exception {
        if (file == null) {
            throw new IllegalArgumentException("待写入文件不能为空");
        }
        storeFile(provider, user, relativePath, new InMemoryMultipartFile(
            file.getFilename(),
            file.getFilename(),
            file.getContentType(),
            file.getBytes()
        ));
    }

    public DownloadedFile downloadFile(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        if (isLocalBackedProvider(provider)) {
            Path target = resolveLocalFilePath(provider, user, relativePath);
            if (!Files.exists(target) || !Files.isRegularFile(target)) {
                throw new IOException("文件不存在: " + target);
            }
            String contentType = Files.probeContentType(target);
            return new DownloadedFile(
                Files.readAllBytes(target),
                contentType,
                target.getFileName() != null ? target.getFileName().toString() : relativePath.getFileName().toString()
            );
        }
        if (provider.getType() == StorageType.WEBDAV) {
            HttpRequest request = authorizedBuilder(provider, resolveWebDavUri(provider, user, relativePath))
                .GET()
                .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("下载 WebDAV 文件失败，状态码: " + response.statusCode());
            }
            return new DownloadedFile(
                response.body(),
                response.headers().firstValue("Content-Type").orElse(null),
                relativePath.getFileName() != null ? relativePath.getFileName().toString() : null
            );
        }
        if (provider.getType() == StorageType.FTP) {
            FTPClient ftpClient = openFtpClient(provider);
            try {
                String remotePath = buildFtpDirectoryPath(provider, user, relativePath);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                if (!ftpClient.retrieveFile(remotePath, outputStream)) {
                    throw new IOException("下载 FTP 文件失败: " + ftpClient.getReplyString());
                }
                return new DownloadedFile(
                    outputStream.toByteArray(),
                    probeContentType(relativePath.getFileName() != null ? relativePath.getFileName().toString() : null),
                    relativePath.getFileName() != null ? relativePath.getFileName().toString() : null
                );
            } finally {
                closeFtpClient(ftpClient);
            }
        }
        if (provider.getType() == StorageType.COS) {
            COSClient cosClient = createCosClient(provider);
            try (var objectStream = cosClient.getObject(resolveCosBucket(provider), buildCosKey(provider, user, relativePath)).getObjectContent()) {
                return new DownloadedFile(
                    objectStream.readAllBytes(),
                    probeContentType(relativePath.getFileName() != null ? relativePath.getFileName().toString() : null),
                    relativePath.getFileName() != null ? relativePath.getFileName().toString() : null
                );
            } finally {
                cosClient.shutdown();
            }
        }
        if (provider.getType() == StorageType.AZURE_BLOB) {
            return downloadFromAzureBlob(provider, user, relativePath);
        }
        if (provider.getType() == StorageType.DROPBOX) {
            return downloadFromDropbox(provider, user, relativePath);
        }
        if (provider.getType() == StorageType.ONEDRIVE) {
            return downloadFromOneDrive(provider, user, relativePath);
        }
        if (provider.getType() == StorageType.UPYUN) {
            return downloadFromUpyun(provider, user, relativePath);
        }
        if (isS3CompatibleProvider(provider)) {
            return downloadFromS3Compatible(provider, user, relativePath);
        }
        throw new IllegalArgumentException("当前存储类型暂不支持下载: " + provider.getType());
    }

    public Map<String, Object> listDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        if (isLocalBackedProvider(provider)) {
            return listLocalBackedDirectory(provider, user, relativeDirectory);
        }
        if (provider.getType() == StorageType.WEBDAV) {
            HttpRequest request = authorizedBuilder(provider, resolveWebDavUri(provider, user, relativeDirectory))
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .method("PROPFIND", HttpRequest.BodyPublishers.ofString(
                    "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "<d:propfind xmlns:d=\"DAV:\">\n" +
                        "  <d:prop>\n" +
                        "    <d:displayname />\n" +
                        "    <d:getcontentlength />\n" +
                        "    <d:getlastmodified />\n" +
                        "    <d:resourcetype />\n" +
                        "  </d:prop>\n" +
                        "</d:propfind>"
                ))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("读取 WebDAV 目录失败，状态码: " + response.statusCode() + "，响应: " + response.body());
            }
            return parseWebDavListing(provider, user, relativeDirectory, response.body());
        }
        if (provider.getType() == StorageType.FTP) {
            return listFtpDirectory(provider, user, relativeDirectory);
        }
        if (provider.getType() == StorageType.COS) {
            return listCosDirectory(provider, user, relativeDirectory);
        }
        if (provider.getType() == StorageType.AZURE_BLOB) {
            return listAzureBlobDirectory(provider, user, relativeDirectory);
        }
        if (provider.getType() == StorageType.DROPBOX) {
            return listDropboxDirectory(provider, user, relativeDirectory);
        }
        if (provider.getType() == StorageType.ONEDRIVE) {
            return listOneDriveDirectory(provider, user, relativeDirectory);
        }
        if (provider.getType() == StorageType.UPYUN) {
            return listUpyunDirectory(provider, user, relativeDirectory);
        }
        if (isS3CompatibleProvider(provider)) {
            return listS3CompatibleDirectory(provider, user, relativeDirectory);
        }
        throw new IllegalArgumentException("当前存储类型暂不支持浏览: " + provider.getType());
    }

    public void createDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        if (isLocalBackedProvider(provider)) {
            Path dir = resolveLocalDirectoryPath(provider, user, relativeDirectory);
            Files.createDirectories(dir);
            return;
        }
        if (provider.getType() == StorageType.WEBDAV) {
            ensureWebDavDirectories(provider, user, relativeDirectory);
            return;
        }
        if (provider.getType() == StorageType.FTP) {
            FTPClient ftpClient = openFtpClient(provider);
            try {
                ensureFtpDirectoryPath(ftpClient, buildFtpDirectoryPath(provider, user, relativeDirectory));
                return;
            } finally {
                closeFtpClient(ftpClient);
            }
        }
        if (provider.getType() == StorageType.COS) {
            createCosDirectory(provider, user, relativeDirectory);
            return;
        }
        if (provider.getType() == StorageType.AZURE_BLOB) {
            createAzureBlobDirectory(provider, user, relativeDirectory);
            return;
        }
        if (provider.getType() == StorageType.DROPBOX) {
            createDropboxDirectory(provider, user, relativeDirectory);
            return;
        }
        if (provider.getType() == StorageType.ONEDRIVE) {
            createOneDriveDirectory(provider, user, relativeDirectory);
            return;
        }
        if (provider.getType() == StorageType.UPYUN) {
            createUpyunDirectory(provider, user, relativeDirectory);
            return;
        }
        if (isS3CompatibleProvider(provider)) {
            createS3CompatibleDirectory(provider, user, relativeDirectory);
            return;
        }
        throw new IllegalArgumentException("当前存储类型暂不支持创建目录: " + provider.getType());
    }

    public void deletePath(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        if (isLocalBackedProvider(provider)) {
            Path target = resolveLocalDirectoryPath(provider, user, relativePath);
            if (Files.exists(target)) {
                deleteLocalBackedPath(target);
            }
            return;
        }
        if (provider.getType() == StorageType.WEBDAV) {
            HttpRequest request = authorizedBuilder(provider, resolveWebDavUri(provider, user, relativePath))
                .DELETE()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if ((status >= 200 && status < 300) || status == 404) {
                return;
            }
            throw new IOException("删除 WebDAV 路径失败，状态码: " + status + "，响应: " + response.body());
        }
        if (provider.getType() == StorageType.FTP) {
            FTPClient ftpClient = openFtpClient(provider);
            try {
                deleteFtpPath(ftpClient, buildFtpDirectoryPath(provider, user, relativePath));
                return;
            } finally {
                closeFtpClient(ftpClient);
            }
        }
        if (provider.getType() == StorageType.COS) {
            deleteCosPath(provider, user, relativePath);
            return;
        }
        if (provider.getType() == StorageType.AZURE_BLOB) {
            deleteAzureBlobPath(provider, user, relativePath);
            return;
        }
        if (provider.getType() == StorageType.DROPBOX) {
            deleteDropboxPath(provider, user, relativePath);
            return;
        }
        if (provider.getType() == StorageType.ONEDRIVE) {
            deleteOneDrivePath(provider, user, relativePath);
            return;
        }
        if (provider.getType() == StorageType.UPYUN) {
            deleteUpyunPath(provider, user, relativePath);
            return;
        }
        if (isS3CompatibleProvider(provider)) {
            deleteS3CompatiblePath(provider, user, relativePath);
            return;
        }
        throw new IllegalArgumentException("当前存储类型暂不支持删除: " + provider.getType());
    }

    public void movePath(StorageProvider provider, UserAccount user, Path sourceRelativePath, Path targetRelativePath) throws Exception {
        if (isLocalBackedProvider(provider)) {
            Path source = resolveLocalDirectoryPath(provider, user, sourceRelativePath);
            Path target = resolveLocalDirectoryPath(provider, user, targetRelativePath);
            Files.createDirectories(target.getParent());
            Files.move(source, target);
            return;
        }
        if (provider.getType() == StorageType.WEBDAV) {
            ensureWebDavDirectories(provider, user, targetRelativePath.getParent());
            HttpRequest request = authorizedBuilder(provider, resolveWebDavUri(provider, user, sourceRelativePath))
                .header("Destination", resolveWebDavUri(provider, user, targetRelativePath).toString())
                .header("Overwrite", "T")
                .method("MOVE", HttpRequest.BodyPublishers.noBody())
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return;
            }
            throw new IOException("移动 WebDAV 路径失败，状态码: " + status + "，响应: " + response.body());
        }
        if (provider.getType() == StorageType.FTP) {
            FTPClient ftpClient = openFtpClient(provider);
            try {
                String source = buildFtpDirectoryPath(provider, user, sourceRelativePath);
                String target = buildFtpDirectoryPath(provider, user, targetRelativePath);
                ensureFtpDirectories(ftpClient, target);
                if (!ftpClient.rename(source, target)) {
                    throw new IOException("移动 FTP 路径失败: " + ftpClient.getReplyString());
                }
                return;
            } finally {
                closeFtpClient(ftpClient);
            }
        }
        if (provider.getType() == StorageType.COS) {
            moveCosPath(provider, user, sourceRelativePath, targetRelativePath);
            return;
        }
        if (provider.getType() == StorageType.AZURE_BLOB) {
            moveAzureBlobPath(provider, user, sourceRelativePath, targetRelativePath);
            return;
        }
        if (provider.getType() == StorageType.DROPBOX) {
            moveDropboxPath(provider, user, sourceRelativePath, targetRelativePath);
            return;
        }
        if (provider.getType() == StorageType.ONEDRIVE) {
            moveOneDrivePath(provider, user, sourceRelativePath, targetRelativePath);
            return;
        }
        if (provider.getType() == StorageType.UPYUN) {
            moveUpyunPath(provider, user, sourceRelativePath, targetRelativePath);
            return;
        }
        if (isS3CompatibleProvider(provider)) {
            moveS3CompatiblePath(provider, user, sourceRelativePath, targetRelativePath);
            return;
        }
        throw new IllegalArgumentException("当前存储类型暂不支持移动: " + provider.getType());
    }

    public Path resolveLocalDirectoryPath(StorageProvider provider, UserAccount user, Path relativeDirectory) {
        Path providerBase = storageProviderService.resolveAbsoluteBaseDirectory(provider);
        Path scopedRoot = resolveLocalScopedRoot(providerBase, user);
        if (relativeDirectory == null) {
            return scopedRoot;
        }
        Path relative = relativeDirectory.normalize();
        if (shouldStripLeadingUserSegment(user, scopedRoot, providerBase) && relative.getNameCount() > 0) {
            String first = relative.getName(0).toString();
            if (first.equals(String.valueOf(user.getId()))) {
                relative = relative.getNameCount() == 1 ? Path.of("") : relative.subpath(1, relative.getNameCount());
            }
        }
        return scopedRoot.resolve(relative).normalize();
    }

    private Path resolveLocalScopedRoot(Path providerBase, UserAccount user) {
        Path normalizedBase = providerBase == null ? null : providerBase.toAbsolutePath().normalize();
        if (normalizedBase == null || user == null) {
            return normalizedBase;
        }
        if (isMultiUserScoped(user)) {
            return normalizedBase.resolve(String.valueOf(user.getId())).normalize();
        }
        Path ownedRoot = normalizedBase.resolve(String.valueOf(user.getId())).normalize();
        if (Files.isDirectory(ownedRoot)) {
            return ownedRoot;
        }
        return normalizedBase;
    }

    private boolean shouldStripLeadingUserSegment(UserAccount user, Path scopedRoot, Path providerBase) {
        if (user == null || scopedRoot == null || providerBase == null) {
            return false;
        }
        if (isMultiUserScoped(user)) {
            return true;
        }
        return !scopedRoot.equals(providerBase.toAbsolutePath().normalize());
    }

    public String resolvePreviewUrl(StorageProvider provider, UserAccount user, Path relativePath) {
        if (isLocalBackedProvider(provider)) {
            throw new IllegalArgumentException("本地挂载存储无需通过远端预览接口打开");
        }
        if (provider.getType() != StorageType.COS) {
            if (provider.getType() == StorageType.AZURE_BLOB) {
                return resolveAzureBlobPreviewUrl(provider, user, relativePath);
            }
            if (provider.getType() == StorageType.DROPBOX) {
                return resolveDropboxPreviewUrl(provider, user, relativePath);
            }
            if (provider.getType() == StorageType.ONEDRIVE) {
                return resolveOneDrivePreviewUrl(provider, user, relativePath);
            }
            if (provider.getType() == StorageType.UPYUN) {
                return resolveUpyunPreviewUrl(provider, user, relativePath);
            }
            if (isS3CompatibleProvider(provider)) {
                return resolveS3PreviewUrl(provider, user, relativePath);
            }
            throw new IllegalArgumentException("当前存储类型暂不支持预览");
        }
        COSClient cosClient = createCosClient(provider);
        try {
            String bucket = resolveCosBucket(provider);
            String key = buildCosKey(provider, user, relativePath);
            if (!cosObjectExists(cosClient, bucket, key)) {
                throw new IllegalArgumentException("文件不存在");
            }
            return cosClient.generatePresignedUrl(bucket, key, new Date(System.currentTimeMillis() + 10L * 60 * 1000)).toString();
        } finally {
            cosClient.shutdown();
        }
    }

    private Path resolveLocalFilePath(StorageProvider provider, UserAccount user, Path relativePath) {
        Path scopedRoot = resolveLocalDirectoryPath(provider, user, null);
        return scopedRoot.resolve(relativePath).normalize();
    }

    private boolean isLocalBackedProvider(StorageProvider provider) {
        return provider != null && (provider.getType() == StorageType.LOCAL
            || provider.getType() == StorageType.SFTP
            || provider.getType() == StorageType.SMB
            || provider.getType() == StorageType.NFS);
    }

    private Map<String, Object> listLocalBackedDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        Path rootRelative = relativeDirectory == null ? Path.of("") : relativeDirectory.normalize();
        Path absoluteDirectory = resolveLocalDirectoryPath(provider, user, rootRelative);
        if (!Files.exists(absoluteDirectory)) {
            Files.createDirectories(absoluteDirectory);
        }
        if (!Files.isDirectory(absoluteDirectory)) {
            throw new IOException("目录不存在: " + absoluteDirectory);
        }

        List<Map<String, Object>> directories = new ArrayList<>();
        List<Map<String, Object>> files = new ArrayList<>();
        try (var stream = Files.list(absoluteDirectory)) {
            stream.sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .forEach(path -> {
                    String name = path.getFileName() == null ? null : path.getFileName().toString();
                    if (name == null || name.isBlank()) {
                        return;
                    }
                    Path childRelative = rootRelative.resolve(name).normalize();
                    LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                    item.put("name", name);
                    item.put("path", toMountedBrowserPath(user, childRelative));
                    boolean directory = Files.isDirectory(path);
                    item.put("isDirectory", directory);
                    try {
                        if (directory) {
                            item.put("photoCount", 0);
                            directories.add(item);
                        } else {
                            item.put("size", Files.size(path));
                            item.put("lastModified", Files.getLastModifiedTime(path).toMillis());
                            files.add(item);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("path", toMountedBrowserPath(user, rootRelative));
        result.put("parent", rootRelative.getNameCount() > 0 ? toMountedBrowserPath(user, rootRelative.getParent()) : null);
        result.put("directories", directories);
        result.put("files", files);
        return result;
    }

    private void deleteLocalBackedPath(Path target) throws IOException {
        if (Files.isDirectory(target)) {
            try (var stream = Files.list(target)) {
                for (Path child : stream.collect(java.util.stream.Collectors.toList())) {
                    deleteLocalBackedPath(child);
                }
            }
            Files.deleteIfExists(target);
            return;
        }
        Files.deleteIfExists(target);
    }

    private String toMountedBrowserPath(UserAccount user, Path relativePath) {
        Path base = Path.of("");
        if (isMultiUserScoped(user)) {
            base = base.resolve(String.valueOf(user.getId()));
        }
        Path fullPath = relativePath == null ? base : base.resolve(relativePath).normalize();
        String value = "/" + fullPath.toString().replace('\\', '/');
        return value.replaceAll("/+", "/");
    }

    private String toLogicalBrowserPath(Path relativePath) {
        if (relativePath == null) {
            return "/";
        }
        String normalized = relativePath.normalize().toString().replace('\\', '/');
        if (normalized.isBlank()) {
            return "/";
        }
        normalized = normalized.replaceAll("^/+", "");
        return "/" + normalized;
    }

    private long resolveWebDavExistingSize(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        HttpRequest request = authorizedBuilder(provider, resolveWebDavUri(provider, user, relativePath))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 404) {
            return 0L;
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            String header = response.headers().firstValue("Content-Length").orElse("0");
            try {
                return Long.parseLong(header);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        throw new IOException("获取 WebDAV 文件大小失败，状态码: " + response.statusCode());
    }

    private long resolveFtpExistingSize(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        FTPClient ftpClient = openFtpClient(provider);
        try {
            FTPFile file = resolveFtpFile(ftpClient, buildFtpDirectoryPath(provider, user, relativePath));
            return file != null && file.isFile() ? file.getSize() : 0L;
        } finally {
            closeFtpClient(ftpClient);
        }
    }

    private long resolveCosExistingSize(StorageProvider provider, UserAccount user, Path relativePath) {
        COSClient cosClient = createCosClient(provider);
        try {
            ObjectMetadata metadata = cosClient.getObjectMetadata(resolveCosBucket(provider), buildCosKey(provider, user, relativePath));
            return metadata != null ? metadata.getContentLength() : 0L;
        } catch (CosServiceException e) {
            if (e.getStatusCode() == 404 || "NoSuchKey".equalsIgnoreCase(e.getErrorCode())) {
                return 0L;
            }
            throw new RuntimeException("读取 COS 文件大小失败: " + userPathService.sanitizeVisibleText(e.getMessage()), e);
        } finally {
            cosClient.shutdown();
        }
    }

    private long resolveAzureBlobExistingSize(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        HttpResponse<byte[]> response = sendAzureBlobRequest(
            provider,
            "HEAD",
            buildAzureBlobKey(provider, user, relativePath),
            null,
            Map.of(),
            null
        );
        if (response.statusCode() == 404) {
            return 0L;
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            String header = response.headers().firstValue("Content-Length").orElse("0");
            try {
                return Long.parseLong(header);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        throw new IOException("读取 Azure Blob 文件大小失败，状态码: " + response.statusCode());
    }

    private void uploadToWebDav(StorageProvider provider, UserAccount user, Path relativePath, MultipartFile file) throws Exception {
        ensureWebDavDirectories(provider, user, relativePath.getParent());
        HttpRequest request = authorizedBuilder(provider, resolveWebDavUri(provider, user, relativePath))
            .PUT(HttpRequest.BodyPublishers.ofInputStream(() -> {
                try {
                    return file.getInputStream();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }))
            .header("Content-Type", contentType(file))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("WebDAV 上传失败，状态码: " + response.statusCode() + "，响应: " + response.body());
        }
    }

    private void uploadToFtp(StorageProvider provider, UserAccount user, Path relativePath, MultipartFile file) throws Exception {
        FTPClient ftpClient = openFtpClient(provider);
        String remotePath = buildFtpDirectoryPath(provider, user, relativePath);
        try {
            ensureFtpDirectories(ftpClient, remotePath);
            try (var inputStream = file.getInputStream()) {
                if (!ftpClient.storeFile(remotePath, inputStream)) {
                    throw new IOException("FTP 上传失败: " + ftpClient.getReplyString());
                }
            }
        } finally {
            closeFtpClient(ftpClient);
        }
    }

    private void uploadToCos(StorageProvider provider, UserAccount user, Path relativePath, MultipartFile file) throws Exception {
        COSClient cosClient = createCosClient(provider);
        try (var inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(contentType(file));
            cosClient.putObject(resolveCosBucket(provider), buildCosKey(provider, user, relativePath), inputStream, metadata);
        } finally {
            cosClient.shutdown();
        }
    }

    private void uploadToAzureBlob(StorageProvider provider, UserAccount user, Path relativePath, MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        sendAzureBlobRequest(
            provider,
            "PUT",
            buildAzureBlobKey(provider, user, relativePath),
            null,
            Map.of(
                "Content-Type", contentType(file),
                "x-ms-blob-type", "BlockBlob"
            ),
            bytes
        );
    }

    private Map<String, Object> listCosDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) {
        COSClient cosClient = createCosClient(provider);
        try {
            Path rootRelative = relativeDirectory == null ? Path.of("") : relativeDirectory.normalize();
            String prefix = buildCosDirectoryPrefix(provider, user, rootRelative);
            String delimiter = "/";
            String bucket = resolveCosBucket(provider);

            ListObjectsRequest request = new ListObjectsRequest();
            request.setBucketName(bucket);
            request.setPrefix(prefix);
            request.setDelimiter(delimiter);
            request.setMaxKeys(1000);

            ObjectListing listing = cosClient.listObjects(request);
            List<Map<String, Object>> directories = new ArrayList<>();
            List<Map<String, Object>> files = new ArrayList<>();

            while (true) {
                if (listing.getCommonPrefixes() != null) {
                    for (String commonPrefix : listing.getCommonPrefixes()) {
                        String name = extractLastName(commonPrefix);
                        if (name == null || name.isBlank()) {
                            continue;
                        }
                        Path childRelative = rootRelative.resolve(name).normalize();
                        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                        item.put("name", name);
                        item.put("path", toCosBrowserPath(provider, user, childRelative));
                        item.put("isDirectory", true);
                        item.put("photoCount", 0);
                        directories.add(item);
                    }
                }

                if (listing.getObjectSummaries() != null) {
                    for (COSObjectSummary summary : listing.getObjectSummaries()) {
                        if (summary == null || summary.getKey() == null || summary.getKey().equals(prefix)) {
                            continue;
                        }
                        String relativeKey = prefix == null || prefix.isBlank()
                            ? summary.getKey()
                            : summary.getKey().substring(prefix.length());
                        if (relativeKey.isBlank() || relativeKey.contains("/")) {
                            continue;
                        }
                        if (summary.getKey().endsWith("/") && summary.getSize() == 0) {
                            continue;
                        }
                        Path childRelative = rootRelative.resolve(relativeKey).normalize();
                        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                        item.put("name", relativeKey);
                        item.put("path", toCosBrowserPath(provider, user, childRelative));
                        item.put("isDirectory", false);
                        item.put("size", summary.getSize());
                        item.put("lastModified", summary.getLastModified() != null ? summary.getLastModified().getTime() : null);
                        files.add(item);
                    }
                }

                if (!listing.isTruncated()) {
                    break;
                }
                listing = cosClient.listNextBatchOfObjects(listing);
            }

            directories.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));
            files.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));

            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("path", toCosBrowserPath(provider, user, rootRelative));
            result.put("parent", rootRelative.getNameCount() > 0 ? toCosBrowserPath(provider, user, rootRelative.getParent()) : null);
            result.put("directories", directories);
            result.put("files", files);
            return result;
        } finally {
            cosClient.shutdown();
        }
    }

    private void createCosDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) {
        String key = buildCosDirectoryPrefix(provider, user, relativeDirectory);
        if (key.isBlank()) {
            return;
        }
        COSClient cosClient = createCosClient(provider);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(0);
            cosClient.putObject(new PutObjectRequest(
                resolveCosBucket(provider),
                key,
                new ByteArrayInputStream(new byte[0]),
                metadata
            ));
        } finally {
            cosClient.shutdown();
        }
    }

    private void deleteCosPath(StorageProvider provider, UserAccount user, Path relativePath) {
        COSClient cosClient = createCosClient(provider);
        try {
            String bucket = resolveCosBucket(provider);
            String key = buildCosKey(provider, user, relativePath);
            if (cosObjectExists(cosClient, bucket, key)) {
                cosClient.deleteObject(bucket, key);
                return;
            }
            String prefix = buildCosDirectoryPrefix(provider, user, relativePath);
            List<String> keys = listCosKeys(cosClient, bucket, prefix);
            if (keys.isEmpty() && cosObjectExists(cosClient, bucket, prefix)) {
                keys.add(prefix);
            }
            deleteCosKeys(cosClient, bucket, keys);
        } finally {
            cosClient.shutdown();
        }
    }

    private void moveCosPath(StorageProvider provider, UserAccount user, Path sourceRelativePath, Path targetRelativePath) {
        COSClient cosClient = createCosClient(provider);
        try {
            String bucket = resolveCosBucket(provider);
            String sourceKey = buildCosKey(provider, user, sourceRelativePath);
            String targetKey = buildCosKey(provider, user, targetRelativePath);
            if (cosObjectExists(cosClient, bucket, sourceKey)) {
                cosClient.copyObject(bucket, sourceKey, bucket, targetKey);
                cosClient.deleteObject(bucket, sourceKey);
                return;
            }

            String sourcePrefix = buildCosDirectoryPrefix(provider, user, sourceRelativePath);
            String targetPrefix = buildCosDirectoryPrefix(provider, user, targetRelativePath);
            List<String> keys = listCosKeys(cosClient, bucket, sourcePrefix);
            if (keys.isEmpty() && cosObjectExists(cosClient, bucket, sourcePrefix)) {
                keys.add(sourcePrefix);
            }
            for (String key : keys) {
                String suffix = key.substring(sourcePrefix.length());
                String destinationKey = targetPrefix + suffix;
                cosClient.copyObject(bucket, key, bucket, destinationKey);
            }
            deleteCosKeys(cosClient, bucket, keys);
        } finally {
            cosClient.shutdown();
        }
    }

    private DownloadedFile downloadFromAzureBlob(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        HttpResponse<byte[]> response = sendAzureBlobRequest(
            provider,
            "GET",
            buildAzureBlobKey(provider, user, relativePath),
            null,
            Map.of(),
            null
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("下载 Azure Blob 文件失败，状态码: " + response.statusCode());
        }
        String filename = relativePath.getFileName() != null ? relativePath.getFileName().toString() : null;
        return new DownloadedFile(
            response.body(),
            response.headers().firstValue("Content-Type").orElse(probeContentType(filename)),
            filename
        );
    }

    private Map<String, Object> listAzureBlobDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        Path rootRelative = relativeDirectory == null ? Path.of("") : relativeDirectory.normalize();
        String prefix = buildAzureBlobDirectoryPrefix(provider, user, rootRelative);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("restype", "container");
        query.put("comp", "list");
        query.put("delimiter", "/");
        if (prefix != null && !prefix.isBlank()) {
            query.put("prefix", prefix);
        }
        query.put("maxresults", "1000");
        HttpResponse<byte[]> response = sendAzureBlobRequest(provider, "GET", null, query, Map.of(), null);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("读取 Azure Blob 目录失败，状态码: " + response.statusCode());
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(response.body()));

        List<Map<String, Object>> directories = new ArrayList<>();
        List<Map<String, Object>> files = new ArrayList<>();

        NodeList blobPrefixes = document.getElementsByTagName("BlobPrefix");
        for (int i = 0; i < blobPrefixes.getLength(); i++) {
            Element element = (Element) blobPrefixes.item(i);
            String value = childText(element, null, "Name");
            String name = extractLastName(value);
            if (name == null || name.isBlank()) {
                continue;
            }
            Path childRelative = rootRelative.resolve(name).normalize();
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("path", toAzureBlobBrowserPath(provider, user, childRelative));
            item.put("isDirectory", true);
            item.put("photoCount", 0);
            directories.add(item);
        }

        NodeList blobs = document.getElementsByTagName("Blob");
        for (int i = 0; i < blobs.getLength(); i++) {
            Element element = (Element) blobs.item(i);
            String key = childText(element, null, "Name");
            if (key == null || key.equals(prefix)) {
                continue;
            }
            String relativeKey = prefix == null || prefix.isBlank() ? key : key.substring(prefix.length());
            if (relativeKey.isBlank() || relativeKey.contains("/")) {
                continue;
            }
            if (key.endsWith("/") && "0".equals(childText(element, null, "Content-Length"))) {
                continue;
            }
            Path childRelative = rootRelative.resolve(relativeKey).normalize();
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("name", relativeKey);
            item.put("path", toAzureBlobBrowserPath(provider, user, childRelative));
            item.put("isDirectory", false);
            item.put("size", parseLong(childText(element, null, "Content-Length")));
            item.put("lastModified", parseHttpDate(childText(element, null, "Last-Modified")));
            files.add(item);
        }

        directories.sort(Comparator.comparing(item -> String.valueOf(item.get("name")), String.CASE_INSENSITIVE_ORDER));
        files.sort(Comparator.comparing(item -> String.valueOf(item.get("name")), String.CASE_INSENSITIVE_ORDER));

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("path", toAzureBlobBrowserPath(provider, user, rootRelative));
        result.put("parent", rootRelative.getNameCount() > 0 ? toAzureBlobBrowserPath(provider, user, rootRelative.getParent()) : null);
        result.put("directories", directories);
        result.put("files", files);
        return result;
    }

    private void createAzureBlobDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        String key = buildAzureBlobDirectoryPrefix(provider, user, relativeDirectory);
        if (key == null || key.isBlank()) {
            return;
        }
        sendAzureBlobRequest(
            provider,
            "PUT",
            key,
            null,
            Map.of(
                "Content-Type", "application/octet-stream",
                "x-ms-blob-type", "BlockBlob"
            ),
            new byte[0]
        );
    }

    private void deleteAzureBlobPath(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        String key = buildAzureBlobKey(provider, user, relativePath);
        if (azureBlobExists(provider, key)) {
            sendAzureBlobRequest(provider, "DELETE", key, null, Map.of(), null);
            return;
        }
        String prefix = buildAzureBlobDirectoryPrefix(provider, user, relativePath);
        List<String> keys = listAzureBlobKeys(provider, prefix);
        if (keys.isEmpty() && azureBlobExists(provider, prefix)) {
            keys.add(prefix);
        }
        for (String blobKey : keys) {
            sendAzureBlobRequest(provider, "DELETE", blobKey, null, Map.of(), null);
        }
    }

    private void moveAzureBlobPath(StorageProvider provider, UserAccount user, Path sourceRelativePath, Path targetRelativePath) throws Exception {
        String sourceKey = buildAzureBlobKey(provider, user, sourceRelativePath);
        String targetKey = buildAzureBlobKey(provider, user, targetRelativePath);
        if (azureBlobExists(provider, sourceKey)) {
            copyAzureBlob(provider, sourceKey, targetKey);
            sendAzureBlobRequest(provider, "DELETE", sourceKey, null, Map.of(), null);
            return;
        }
        String sourcePrefix = buildAzureBlobDirectoryPrefix(provider, user, sourceRelativePath);
        String targetPrefix = buildAzureBlobDirectoryPrefix(provider, user, targetRelativePath);
        List<String> keys = listAzureBlobKeys(provider, sourcePrefix);
        if (keys.isEmpty() && azureBlobExists(provider, sourcePrefix)) {
            keys.add(sourcePrefix);
        }
        for (String key : keys) {
            String suffix = key.substring(sourcePrefix.length());
            copyAzureBlob(provider, key, targetPrefix + suffix);
        }
        for (String key : keys) {
            sendAzureBlobRequest(provider, "DELETE", key, null, Map.of(), null);
        }
    }

    private String resolveAzureBlobPreviewUrl(StorageProvider provider, UserAccount user, Path relativePath) {
        String key = buildAzureBlobKey(provider, user, relativePath);
        try {
            if (!azureBlobExists(provider, key)) {
                throw new IllegalArgumentException("文件不存在");
            }
            String sasToken = parseConfigValue(provider, "sasToken", "sharedAccessSignature");
            if (sasToken == null) {
                throw new IllegalArgumentException("Azure Blob 预览需配置 SAS Token，或直接使用后端预览流");
            }
            URI uri = buildAzureBlobUri(provider, key, null);
            return appendQueryParameters(uri, Map.of(), sasToken).toString();
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("生成 Azure Blob 预览链接失败: " + userPathService.sanitizeVisibleText(e.getMessage()), e);
        }
    }

    private long resolveDropboxExistingSize(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        Map<String, Object> metadata = fetchDropboxMetadata(provider, user, relativePath);
        if (metadata.isEmpty()) {
            return 0L;
        }
        if (!"file".equalsIgnoreCase(asString(metadata.get(".tag")))) {
            return 0L;
        }
        return parseLong(asString(metadata.get("size"))) == null ? 0L : parseLong(asString(metadata.get("size")));
    }

    private void uploadToDropbox(StorageProvider provider, UserAccount user, Path relativePath, MultipartFile file) throws Exception {
        URI uri = resolveDropboxContentUri(provider, "/2/files/upload");
        String dropboxPath = buildDropboxPath(provider, user, relativePath);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", dropboxPath);
        args.put("mode", "overwrite");
        args.put("autorename", false);
        args.put("mute", true);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer " + resolveDropboxAccessToken(provider))
            .header("Content-Type", "application/octet-stream")
            .header("Dropbox-API-Arg", objectMapper.writeValueAsString(args))
            .POST(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()));
        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("上传 Dropbox 文件失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
    }

    private DownloadedFile downloadFromDropbox(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        URI uri = resolveDropboxContentUri(provider, "/2/files/download");
        Map<String, Object> args = Map.of("path", buildDropboxPath(provider, user, relativePath));
        HttpRequest request = HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer " + resolveDropboxAccessToken(provider))
            .header("Dropbox-API-Arg", objectMapper.writeValueAsString(args))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("下载 Dropbox 文件失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
        String filename = relativePath.getFileName() != null ? relativePath.getFileName().toString() : null;
        return new DownloadedFile(
            response.body(),
            response.headers().firstValue("Content-Type").orElse(probeContentType(filename)),
            filename
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> listDropboxDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        Path rootRelative = relativeDirectory == null ? Path.of("") : relativeDirectory.normalize();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", buildDropboxPath(provider, user, rootRelative));
        payload.put("recursive", false);
        payload.put("include_deleted", false);
        payload.put("include_mounted_folders", true);
        payload.put("include_non_downloadable_files", true);
        Map<String, Object> response = sendDropboxApiRequest(provider, "/2/files/list_folder", payload);

        List<Map<String, Object>> directories = new ArrayList<>();
        List<Map<String, Object>> files = new ArrayList<>();
        Object dropboxEntries = response.get("entries");
        List<Map<String, Object>> entries = dropboxEntries instanceof List<?>
            ? (List<Map<String, Object>>) dropboxEntries
            : List.of();
        for (Map<String, Object> entry : entries) {
            String name = trimToNull(asString(entry.get("name")));
            String tag = trimToNull(asString(entry.get(".tag")));
            if (name == null || tag == null) {
                continue;
            }
            Path childRelative = rootRelative.resolve(name).normalize();
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("path", toDropboxBrowserPath(provider, user, childRelative));
            if ("folder".equalsIgnoreCase(tag)) {
                item.put("isDirectory", true);
                item.put("photoCount", 0);
                directories.add(item);
            } else if ("file".equalsIgnoreCase(tag)) {
                item.put("isDirectory", false);
                item.put("size", parseLong(asString(entry.get("size"))));
                item.put("lastModified", parseIsoDate(asString(entry.get("server_modified"))));
                files.add(item);
            }
        }
        directories.sort(Comparator.comparing(item -> String.valueOf(item.get("name")), String.CASE_INSENSITIVE_ORDER));
        files.sort(Comparator.comparing(item -> String.valueOf(item.get("name")), String.CASE_INSENSITIVE_ORDER));
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("path", toDropboxBrowserPath(provider, user, rootRelative));
        result.put("parent", rootRelative.getNameCount() > 0 ? toDropboxBrowserPath(provider, user, rootRelative.getParent()) : null);
        result.put("directories", directories);
        result.put("files", files);
        return result;
    }

    private void createDropboxDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        if (relativeDirectory == null || relativeDirectory.normalize().toString().isBlank()) {
            return;
        }
        sendDropboxApiRequest(provider, "/2/files/create_folder_v2", Map.of(
            "path", buildDropboxPath(provider, user, relativeDirectory),
            "autorename", false
        ));
    }

    private void deleteDropboxPath(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        sendDropboxApiRequest(provider, "/2/files/delete_v2", Map.of(
            "path", buildDropboxPath(provider, user, relativePath)
        ));
    }

    private void moveDropboxPath(StorageProvider provider, UserAccount user, Path sourceRelativePath, Path targetRelativePath) throws Exception {
        sendDropboxApiRequest(provider, "/2/files/move_v2", Map.of(
            "from_path", buildDropboxPath(provider, user, sourceRelativePath),
            "to_path", buildDropboxPath(provider, user, targetRelativePath),
            "autorename", false,
            "allow_shared_folder", true,
            "allow_ownership_transfer", false
        ));
    }

    @SuppressWarnings("unchecked")
    private String resolveDropboxPreviewUrl(StorageProvider provider, UserAccount user, Path relativePath) {
        try {
            Map<String, Object> resp = sendDropboxApiRequest(provider, "/2/files/get_temporary_link", Map.of(
                "path", buildDropboxPath(provider, user, relativePath)
            ));
            String link = trimToNull(asString(resp.get("link")));
            if (link == null) {
                throw new IllegalArgumentException("Dropbox 未返回临时预览链接");
            }
            return link;
        } catch (Exception e) {
            throw new RuntimeException("生成 Dropbox 预览链接失败: " + userPathService.sanitizeVisibleText(e.getMessage()), e);
        }
    }

    private Map<String, Object> fetchDropboxMetadata(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        try {
            return sendDropboxApiRequest(provider, "/2/files/get_metadata", Map.of(
                "path", buildDropboxPath(provider, user, relativePath),
                "include_deleted", false
            ));
        } catch (IOException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("409")) {
                return Collections.emptyMap();
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sendDropboxApiRequest(StorageProvider provider, String apiPath, Map<String, Object> payload) throws Exception {
        URI uri = resolveDropboxApiUri(provider, apiPath);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer " + resolveDropboxAccessToken(provider))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload == null ? Collections.emptyMap() : payload)));
        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Dropbox 请求失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
        if (response.body() == null || response.body().length == 0) {
            return Collections.emptyMap();
        }
        return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    }

    private URI resolveDropboxApiUri(StorageProvider provider, String apiPath) {
        String endpoint = trimToNull(provider.getEndpoint());
        String base = endpoint == null ? "https://api.dropboxapi.com" : endpoint;
        return URI.create(stripTrailingSlash(base) + apiPath);
    }

    private URI resolveDropboxContentUri(StorageProvider provider, String apiPath) {
        String contentEndpoint = firstNonBlank(
            parseConfigValue(provider, "contentEndpoint"),
            "https://content.dropboxapi.com"
        );
        return URI.create(stripTrailingSlash(contentEndpoint) + apiPath);
    }

    private String resolveDropboxAccessToken(StorageProvider provider) {
        String accessToken = parseConfigValue(provider, "accessToken", "token");
        if (accessToken == null) {
            throw new IllegalArgumentException("Dropbox 缺少 accessToken 配置");
        }
        return accessToken;
    }

    private String buildDropboxPath(StorageProvider provider, UserAccount user, Path relativePath) {
        String path = combineRemoteSegments(provider.getBaseDirectory(), resolveTenantPrefix(user), toUnixPath(relativePath));
        return path.isBlank() ? "" : path;
    }

    private String toDropboxBrowserPath(StorageProvider provider, UserAccount user, Path relativePath) {
        return toLogicalBrowserPath(relativePath);
    }

    private long resolveOneDriveExistingSize(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        Map<String, Object> metadata = fetchOneDriveMetadata(provider, user, relativePath);
        if (metadata.isEmpty() || !metadata.containsKey("file")) {
            return 0L;
        }
        return parseLong(asString(metadata.get("size"))) == null ? 0L : parseLong(asString(metadata.get("size")));
    }

    private void uploadToOneDrive(StorageProvider provider, UserAccount user, Path relativePath, MultipartFile file) throws Exception {
        HttpRequest request = oneDriveRequestBuilder(provider, buildOneDriveItemUri(provider, user, relativePath, "/content"))
            .header("Content-Type", contentType(file))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
            .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("上传 OneDrive 文件失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
    }

    private DownloadedFile downloadFromOneDrive(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        HttpRequest request = oneDriveRequestBuilder(provider, buildOneDriveItemUri(provider, user, relativePath, "/content"))
            .GET()
            .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("下载 OneDrive 文件失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
        String filename = relativePath.getFileName() != null ? relativePath.getFileName().toString() : null;
        return new DownloadedFile(
            response.body(),
            response.headers().firstValue("Content-Type").orElse(probeContentType(filename)),
            filename
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> listOneDriveDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        Path rootRelative = relativeDirectory == null ? Path.of("") : relativeDirectory.normalize();
        HttpRequest request = oneDriveRequestBuilder(provider, buildOneDriveChildrenUri(provider, user, rootRelative))
            .GET()
            .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("读取 OneDrive 目录失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
        Map<String, Object> body = response.body() == null || response.body().length == 0
            ? Collections.emptyMap()
            : objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> directories = new ArrayList<>();
        List<Map<String, Object>> files = new ArrayList<>();
        Object oneDriveEntries = body.get("value");
        List<Map<String, Object>> entries = oneDriveEntries instanceof List<?>
            ? (List<Map<String, Object>>) oneDriveEntries
            : List.of();
        for (Map<String, Object> entry : entries) {
            String name = trimToNull(asString(entry.get("name")));
            if (name == null) {
                continue;
            }
            Path childRelative = rootRelative.resolve(name).normalize();
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("path", toOneDriveBrowserPath(provider, user, childRelative));
            if (entry.containsKey("folder")) {
                item.put("isDirectory", true);
                item.put("photoCount", 0);
                directories.add(item);
            } else if (entry.containsKey("file")) {
                item.put("isDirectory", false);
                item.put("size", parseLong(asString(entry.get("size"))));
                item.put("lastModified", parseIsoDate(asString(entry.get("lastModifiedDateTime"))));
                files.add(item);
            }
        }
        directories.sort(Comparator.comparing(item -> String.valueOf(item.get("name")), String.CASE_INSENSITIVE_ORDER));
        files.sort(Comparator.comparing(item -> String.valueOf(item.get("name")), String.CASE_INSENSITIVE_ORDER));
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("path", toOneDriveBrowserPath(provider, user, rootRelative));
        result.put("parent", rootRelative.getNameCount() > 0 ? toOneDriveBrowserPath(provider, user, rootRelative.getParent()) : null);
        result.put("directories", directories);
        result.put("files", files);
        return result;
    }

    private void createOneDriveDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        if (relativeDirectory == null || relativeDirectory.normalize().toString().isBlank()) {
            return;
        }
        Path normalized = relativeDirectory.normalize();
        Path parent = normalized.getParent();
        String name = normalized.getFileName() == null ? null : normalized.getFileName().toString();
        if (name == null || name.isBlank()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("folder", Collections.emptyMap());
        payload.put("@microsoft.graph.conflictBehavior", "replace");
        HttpRequest request = oneDriveRequestBuilder(provider, buildOneDriveChildrenUri(provider, user, parent))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("创建 OneDrive 目录失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
    }

    private void deleteOneDrivePath(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        HttpRequest request = oneDriveRequestBuilder(provider, buildOneDriveItemUri(provider, user, relativePath, null))
            .DELETE()
            .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (!(response.statusCode() >= 200 && response.statusCode() < 300) && response.statusCode() != 204) {
            throw new IOException("删除 OneDrive 路径失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
    }

    private void moveOneDrivePath(StorageProvider provider, UserAccount user, Path sourceRelativePath, Path targetRelativePath) throws Exception {
        Path targetParent = targetRelativePath == null ? null : targetRelativePath.normalize().getParent();
        String targetName = targetRelativePath != null && targetRelativePath.getFileName() != null ? targetRelativePath.getFileName().toString() : null;
        Map<String, Object> payload = new LinkedHashMap<>();
        if (targetName != null) {
            payload.put("name", targetName);
        }
        Map<String, Object> parentReference = new LinkedHashMap<>();
        parentReference.put("path", buildOneDriveParentReferencePath(provider, user, targetParent));
        payload.put("parentReference", parentReference);
        HttpRequest request = oneDriveRequestBuilder(provider, buildOneDriveItemUri(provider, user, sourceRelativePath, null))
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("移动 OneDrive 路径失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
    }

    private String resolveOneDrivePreviewUrl(StorageProvider provider, UserAccount user, Path relativePath) {
        try {
            Map<String, Object> metadata = fetchOneDriveMetadata(provider, user, relativePath);
            String url = trimToNull(asString(metadata.get("@microsoft.graph.downloadUrl")));
            if (url != null) {
                return url;
            }
            throw new IllegalArgumentException("OneDrive 未返回临时下载地址");
        } catch (Exception e) {
            throw new RuntimeException("生成 OneDrive 预览链接失败: " + userPathService.sanitizeVisibleText(e.getMessage()), e);
        }
    }

    private Map<String, Object> fetchOneDriveMetadata(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        HttpRequest request = oneDriveRequestBuilder(provider, buildOneDriveItemUri(provider, user, relativePath, null))
            .GET()
            .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 404) {
            return Collections.emptyMap();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("读取 OneDrive 元数据失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
        return response.body() == null || response.body().length == 0
            ? Collections.emptyMap()
            : objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    }

    private HttpRequest.Builder oneDriveRequestBuilder(StorageProvider provider, URI uri) throws Exception {
        return HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer " + resolveOneDriveAccessToken(provider));
    }

    private String resolveOneDriveAccessToken(StorageProvider provider) throws Exception {
        String accessToken = parseConfigValue(provider, "accessToken", "token");
        if (accessToken != null) {
            return accessToken;
        }
        String tenantId = parseConfigValue(provider, "tenantId");
        String clientId = parseConfigValue(provider, "clientId");
        String clientSecret = parseConfigValue(provider, "clientSecret");
        if (tenantId == null || clientId == null || clientSecret == null) {
            throw new IllegalArgumentException("OneDrive 缺少 accessToken 或 tenantId/clientId/clientSecret 配置");
        }
        String scope = firstNonBlank(parseConfigValue(provider, "scope"), "https://graph.microsoft.com/.default");
        String body = "client_id=" + encodeQueryValue(clientId)
            + "&client_secret=" + encodeQueryValue(clientSecret)
            + "&grant_type=client_credentials"
            + "&scope=" + encodeQueryValue(scope);
        URI uri = URI.create("https://login.microsoftonline.com/" + encodePathSegment(tenantId) + "/oauth2/v2.0/token");
        HttpRequest request = HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("获取 OneDrive Access Token 失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
        Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        String token = trimToNull(asString(payload.get("access_token")));
        if (token == null) {
            throw new IOException("OneDrive Token 响应缺少 access_token");
        }
        return token;
    }

    private URI buildOneDriveItemUri(StorageProvider provider, UserAccount user, Path relativePath, String suffix) {
        String endpoint = stripTrailingSlash(firstNonBlank(provider.getEndpoint(), "https://graph.microsoft.com/v1.0"));
        String drivePrefix = resolveOneDriveDrivePrefix(provider);
        String fullPath = buildOneDrivePath(provider, user, relativePath);
        StringBuilder builder = new StringBuilder(endpoint).append(drivePrefix);
        if (fullPath == null || fullPath.isBlank() || "/".equals(fullPath)) {
            builder.append("/root");
        } else {
            builder.append("/root:").append(encodeGraphPath(fullPath)).append(":");
        }
        if (suffix != null && !suffix.isBlank()) {
            builder.append(suffix.startsWith("/") ? suffix : "/" + suffix);
        }
        return URI.create(builder.toString());
    }

    private URI buildOneDriveChildrenUri(StorageProvider provider, UserAccount user, Path relativeDirectory) {
        return buildOneDriveItemUri(provider, user, relativeDirectory, "/children");
    }

    private String buildOneDrivePath(StorageProvider provider, UserAccount user, Path relativePath) {
        String baseDirectory = normalizeOneDriveBaseDirectory(provider.getBaseDirectory());
        String value = combineRemoteSegments(baseDirectory, resolveTenantPrefix(user), toUnixPath(relativePath));
        return value.isBlank() ? "/" : value;
    }

    private String buildOneDriveParentReferencePath(StorageProvider provider, UserAccount user, Path relativeDirectory) {
        String drivePrefix = resolveOneDriveDrivePrefix(provider);
        String fullPath = buildOneDrivePath(provider, user, relativeDirectory);
        if (fullPath == null || fullPath.isBlank() || "/".equals(fullPath)) {
            return drivePrefix + "/root";
        }
        return drivePrefix + "/root:" + fullPath;
    }

    private String normalizeOneDriveBaseDirectory(String baseDirectory) {
        String value = trimToNull(baseDirectory);
        if (value == null) {
            return "/";
        }
        if (value.startsWith("/drive/root:")) {
            value = value.substring("/drive/root:".length());
        } else if (value.startsWith("drive/root:")) {
            value = value.substring("drive/root:".length());
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (value.endsWith(":")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String resolveOneDriveDrivePrefix(StorageProvider provider) {
        String prefix = firstNonBlank(parseConfigValue(provider, "drivePrefix"), "/me/drive");
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        return prefix;
    }

    private String encodeGraphPath(String path) {
        String normalized = trimToNull(path);
        if (normalized == null || normalized.isBlank() || "/".equals(normalized)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String segment : normalized.split("/")) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            builder.append('/').append(encodePathSegment(segment));
        }
        return builder.toString();
    }

    private String toOneDriveBrowserPath(StorageProvider provider, UserAccount user, Path relativePath) {
        return toLogicalBrowserPath(relativePath);
    }

    private long resolveUpyunExistingSize(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        HttpResponse<byte[]> response = sendUpyunRequest(provider, "HEAD", buildUpyunKey(provider, user, relativePath), null, Map.of(), null);
        if (response.statusCode() == 404) {
            return 0L;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("读取又拍云文件大小失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
        String size = firstNonBlank(
            response.headers().firstValue("x-upyun-file-size").orElse(null),
            response.headers().firstValue("Content-Length").orElse(null)
        );
        Long parsed = parseLong(size);
        return parsed == null ? 0L : parsed;
    }

    private void uploadToUpyun(StorageProvider provider, UserAccount user, Path relativePath, MultipartFile file) throws Exception {
        HttpResponse<byte[]> response = sendUpyunRequest(
            provider,
            "PUT",
            buildUpyunKey(provider, user, relativePath),
            null,
            Map.of("Content-Type", contentType(file)),
            file.getBytes()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("上传又拍云文件失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
    }

    private DownloadedFile downloadFromUpyun(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        HttpResponse<byte[]> response = sendUpyunRequest(provider, "GET", buildUpyunKey(provider, user, relativePath), null, Map.of(), null);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("下载又拍云文件失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
        String filename = relativePath.getFileName() != null ? relativePath.getFileName().toString() : null;
        return new DownloadedFile(
            response.body(),
            response.headers().firstValue("Content-Type").orElse(probeContentType(filename)),
            filename
        );
    }

    private Map<String, Object> listUpyunDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        Path rootRelative = relativeDirectory == null ? Path.of("") : relativeDirectory.normalize();
        Map<String, String> query = new LinkedHashMap<>();
        query.put("list", "");
        HttpResponse<byte[]> response = sendUpyunRequest(
            provider,
            "GET",
            buildUpyunDirectoryKey(provider, user, rootRelative),
            query,
            Map.of("x-list-limit", "1000"),
            null
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("读取又拍云目录失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }

        List<Map<String, Object>> directories = new ArrayList<>();
        List<Map<String, Object>> files = new ArrayList<>();
        String body = responseBodyText(response);
        for (String line : body.split("\\r?\\n")) {
            String value = trimToNull(line);
            if (value == null) {
                continue;
            }
            String[] parts = value.split("\\t");
            if (parts.length < 2) {
                continue;
            }
            String name = trimToNull(parts[0]);
            if (name == null) {
                continue;
            }
            String type = trimToNull(parts[1]);
            Path childRelative = rootRelative.resolve(name).normalize();
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("path", toUpyunBrowserPath(provider, user, childRelative));
            if ("F".equalsIgnoreCase(type)) {
                item.put("isDirectory", true);
                item.put("photoCount", 0);
                directories.add(item);
            } else {
                item.put("isDirectory", false);
                item.put("size", parts.length > 2 ? parseLong(parts[2]) : null);
                item.put("lastModified", parts.length > 3 ? parseUnixSeconds(parts[3]) : null);
                files.add(item);
            }
        }
        directories.sort(Comparator.comparing(item -> String.valueOf(item.get("name")), String.CASE_INSENSITIVE_ORDER));
        files.sort(Comparator.comparing(item -> String.valueOf(item.get("name")), String.CASE_INSENSITIVE_ORDER));
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("path", toUpyunBrowserPath(provider, user, rootRelative));
        result.put("parent", rootRelative.getNameCount() > 0 ? toUpyunBrowserPath(provider, user, rootRelative.getParent()) : null);
        result.put("directories", directories);
        result.put("files", files);
        return result;
    }

    private void createUpyunDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        if (relativeDirectory == null || relativeDirectory.normalize().toString().isBlank()) {
            return;
        }
        HttpResponse<byte[]> response = sendUpyunRequest(
            provider,
            "POST",
            buildUpyunDirectoryKey(provider, user, relativeDirectory),
            null,
            Map.of("Folder", "true"),
            new byte[0]
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("创建又拍云目录失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
    }

    private void deleteUpyunPath(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        HttpResponse<byte[]> response = sendUpyunRequest(provider, "DELETE", buildUpyunKey(provider, user, relativePath), null, Map.of(), null);
        if (response.statusCode() == 404) {
            return;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("删除又拍云路径失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
    }

    private void moveUpyunPath(StorageProvider provider, UserAccount user, Path sourceRelativePath, Path targetRelativePath) throws Exception {
        HttpResponse<byte[]> response = sendUpyunRequest(
            provider,
            "PUT",
            buildUpyunKey(provider, user, targetRelativePath),
            null,
            Map.of("X-Upyun-Move-Source", buildUpyunRequestPath(provider, user, sourceRelativePath)),
            new byte[0]
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("移动又拍云路径失败，状态码: " + response.statusCode() + "，响应: " + responseBodyText(response));
        }
    }

    private String resolveUpyunPreviewUrl(StorageProvider provider, UserAccount user, Path relativePath) {
        String publicBaseUrl = firstNonBlank(
            parseConfigValue(provider, "publicBaseUrl"),
            parseConfigValue(provider, "cdnDomain")
        );
        if (publicBaseUrl == null) {
            throw new IllegalArgumentException("又拍云预览需配置 publicBaseUrl 或 cdnDomain");
        }
        String key = buildUpyunKey(provider, user, relativePath);
        String base = stripTrailingSlash(publicBaseUrl);
        return base + (key.startsWith("/") ? key : "/" + key);
    }

    private HttpResponse<byte[]> sendUpyunRequest(StorageProvider provider,
                                                  String method,
                                                  String key,
                                                  Map<String, String> query,
                                                  Map<String, String> extraHeaders,
                                                  byte[] body) throws Exception {
        URI uri = buildUpyunUri(provider, key, query);
        String operator = parseConfigValue(provider, "operator", "username", "user");
        String password = parseConfigValue(provider, "password");
        if (operator == null || password == null) {
            throw new IllegalArgumentException("又拍云缺少 operator/password 配置");
        }
        String token = Base64.getEncoder().encodeToString((operator + ":" + password).getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .header("Authorization", "Basic " + token);
        if (extraHeaders != null) {
            extraHeaders.forEach(builder::header);
        }
        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else if ("HEAD".equalsIgnoreCase(method)) {
            builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
        } else if ("DELETE".equalsIgnoreCase(method)) {
            builder.DELETE();
        } else if ("PUT".equalsIgnoreCase(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));
        } else {
            builder.method(method.toUpperCase(), HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private URI buildUpyunUri(StorageProvider provider, String key, Map<String, String> query) {
        String endpoint = firstNonBlank(provider.getEndpoint(), "https://v0.api.upyun.com");
        String service = trimToNull(provider.getBucketName());
        if (service == null) {
            throw new IllegalArgumentException("又拍云缺少服务名配置");
        }
        StringBuilder builder = new StringBuilder(stripTrailingSlash(endpoint))
            .append('/')
            .append(encodePathSegment(service));
        String normalizedKey = trimSlashes(trimToNull(key));
        if (normalizedKey != null && !normalizedKey.isBlank()) {
            for (String segment : normalizedKey.split("/")) {
                if (!segment.isBlank()) {
                    builder.append('/').append(encodePathSegment(segment));
                }
            }
        }
        String queryString = toCanonicalQuery(query);
        if (!queryString.isBlank()) {
            builder.append('?').append(queryString);
        }
        return URI.create(builder.toString());
    }

    private String buildUpyunKey(StorageProvider provider, UserAccount user, Path relativePath) {
        String key = combineRemoteSegments(provider.getBaseDirectory(), resolveTenantPrefix(user), toUnixPath(relativePath));
        return key.isBlank() ? "" : key.substring(1);
    }

    private String buildUpyunDirectoryKey(StorageProvider provider, UserAccount user, Path relativePath) {
        String key = buildUpyunKey(provider, user, relativePath);
        if (key.isBlank()) {
            return "";
        }
        return key.endsWith("/") ? key : key + "/";
    }

    private String buildUpyunRequestPath(StorageProvider provider, UserAccount user, Path relativePath) {
        String service = trimToNull(provider.getBucketName());
        if (service == null) {
            throw new IllegalArgumentException("又拍云缺少服务名配置");
        }
        String key = buildUpyunKey(provider, user, relativePath);
        return "/" + service + (key.isBlank() ? "" : "/" + key);
    }

    private String toUpyunBrowserPath(StorageProvider provider, UserAccount user, Path relativePath) {
        return toLogicalBrowserPath(relativePath);
    }

    private long resolveS3ExistingSize(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        HttpResponse<byte[]> response = sendS3Request(
            provider,
            "HEAD",
            buildS3Key(provider, user, relativePath),
            null,
            Map.of(),
            null,
            null
        );
        if (response.statusCode() == 404) {
            return 0L;
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            String header = response.headers().firstValue("Content-Length").orElse("0");
            try {
                return Long.parseLong(header);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        throw new IOException("读取对象存储文件大小失败，状态码: " + response.statusCode());
    }

    private void uploadToS3Compatible(StorageProvider provider, UserAccount user, Path relativePath, MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", contentType(file));
        sendS3Request(
            provider,
            "PUT",
            buildS3Key(provider, user, relativePath),
            null,
            headers,
            bytes,
            null
        );
    }

    private DownloadedFile downloadFromS3Compatible(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        HttpResponse<byte[]> response = sendS3Request(
            provider,
            "GET",
            buildS3Key(provider, user, relativePath),
            null,
            Map.of(),
            null,
            null
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("下载对象存储文件失败，状态码: " + response.statusCode());
        }
        String filename = relativePath.getFileName() != null ? relativePath.getFileName().toString() : null;
        return new DownloadedFile(
            response.body(),
            response.headers().firstValue("Content-Type").orElse(probeContentType(filename)),
            filename
        );
    }

    private Map<String, Object> listS3CompatibleDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        Path rootRelative = relativeDirectory == null ? Path.of("") : relativeDirectory.normalize();
        String prefix = buildS3DirectoryPrefix(provider, user, rootRelative);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("list-type", "2");
        query.put("delimiter", "/");
        query.put("prefix", prefix);
        query.put("max-keys", "1000");
        HttpResponse<byte[]> response = sendS3Request(provider, "GET", null, query, Map.of(), null, null);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("读取对象存储目录失败，状态码: " + response.statusCode());
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document document = factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(response.body()));

        List<Map<String, Object>> directories = new ArrayList<>();
        List<Map<String, Object>> files = new ArrayList<>();

        NodeList commonPrefixes = document.getElementsByTagName("CommonPrefixes");
        for (int i = 0; i < commonPrefixes.getLength(); i++) {
            Element element = (Element) commonPrefixes.item(i);
            String value = childText(element, null, "Prefix");
            String name = extractLastName(value);
            if (name == null || name.isBlank()) {
                continue;
            }
            Path childRelative = rootRelative.resolve(name).normalize();
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("path", toS3BrowserPath(provider, user, childRelative));
            item.put("isDirectory", true);
            item.put("photoCount", 0);
            directories.add(item);
        }

        NodeList contents = document.getElementsByTagName("Contents");
        for (int i = 0; i < contents.getLength(); i++) {
            Element element = (Element) contents.item(i);
            String key = childText(element, null, "Key");
            if (key == null || key.equals(prefix)) {
                continue;
            }
            String relativeKey = prefix == null || prefix.isBlank() ? key : key.substring(prefix.length());
            if (relativeKey.isBlank() || relativeKey.contains("/")) {
                continue;
            }
            if (key.endsWith("/") && "0".equals(childText(element, null, "Size"))) {
                continue;
            }
            Path childRelative = rootRelative.resolve(relativeKey).normalize();
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("name", relativeKey);
            item.put("path", toS3BrowserPath(provider, user, childRelative));
            item.put("isDirectory", false);
            item.put("size", parseLong(childText(element, null, "Size")));
            item.put("lastModified", parseIsoDate(childText(element, null, "LastModified")));
            files.add(item);
        }

        directories.sort(Comparator.comparing(item -> String.valueOf(item.get("name")), String.CASE_INSENSITIVE_ORDER));
        files.sort(Comparator.comparing(item -> String.valueOf(item.get("name")), String.CASE_INSENSITIVE_ORDER));

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("path", toS3BrowserPath(provider, user, rootRelative));
        result.put("parent", rootRelative.getNameCount() > 0 ? toS3BrowserPath(provider, user, rootRelative.getParent()) : null);
        result.put("directories", directories);
        result.put("files", files);
        return result;
    }

    private void createS3CompatibleDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        String key = buildS3DirectoryPrefix(provider, user, relativeDirectory);
        if (key.isBlank()) {
            return;
        }
        sendS3Request(provider, "PUT", key, null, Map.of("Content-Type", "application/octet-stream"), new byte[0], null);
    }

    private void deleteS3CompatiblePath(StorageProvider provider, UserAccount user, Path relativePath) throws Exception {
        String key = buildS3Key(provider, user, relativePath);
        if (s3ObjectExists(provider, key)) {
            sendS3Request(provider, "DELETE", key, null, Map.of(), null, null);
            return;
        }
        String prefix = buildS3DirectoryPrefix(provider, user, relativePath);
        List<String> keys = listS3Keys(provider, prefix);
        if (keys.isEmpty() && s3ObjectExists(provider, prefix)) {
            keys.add(prefix);
        }
        deleteS3Keys(provider, keys);
    }

    private void moveS3CompatiblePath(StorageProvider provider, UserAccount user, Path sourceRelativePath, Path targetRelativePath) throws Exception {
        String sourceKey = buildS3Key(provider, user, sourceRelativePath);
        String targetKey = buildS3Key(provider, user, targetRelativePath);
        if (s3ObjectExists(provider, sourceKey)) {
            copyS3Object(provider, sourceKey, targetKey);
            sendS3Request(provider, "DELETE", sourceKey, null, Map.of(), null, null);
            return;
        }
        String sourcePrefix = buildS3DirectoryPrefix(provider, user, sourceRelativePath);
        String targetPrefix = buildS3DirectoryPrefix(provider, user, targetRelativePath);
        List<String> keys = listS3Keys(provider, sourcePrefix);
        if (keys.isEmpty() && s3ObjectExists(provider, sourcePrefix)) {
            keys.add(sourcePrefix);
        }
        for (String key : keys) {
            String suffix = key.substring(sourcePrefix.length());
            copyS3Object(provider, key, targetPrefix + suffix);
        }
        deleteS3Keys(provider, keys);
    }

    private String resolveS3PreviewUrl(StorageProvider provider, UserAccount user, Path relativePath) {
        String key = buildS3Key(provider, user, relativePath);
        try {
            if (!s3ObjectExists(provider, key)) {
                throw new IllegalArgumentException("文件不存在");
            }
            return buildS3PresignedUrl(provider, key, 600);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("生成对象存储预览链接失败: " + userPathService.sanitizeVisibleText(e.getMessage()), e);
        }
    }

    private void ensureWebDavDirectories(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        Path tenantRoot = resolveTenantRelativeRoot(user);
        Path current = Path.of("");
        for (Path segment : tenantRoot) {
            current = current.resolve(segment.toString());
            createWebDavDirectory(provider, current);
        }
        if (relativeDirectory == null) {
            return;
        }
        for (Path segment : relativeDirectory) {
            current = current.resolve(segment.toString());
            createWebDavDirectory(provider, current);
        }
    }

    private void createWebDavDirectory(StorageProvider provider, Path relativeDirectory) throws Exception {
        HttpRequest request = authorizedBuilder(provider, resolveWebDavUri(provider, null, relativeDirectory))
            .method("MKCOL", HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if ((status >= 200 && status < 300) || status == 301 || status == 302 || status == 405) {
            return;
        }
        throw new IOException("创建 WebDAV 目录失败，状态码: " + status + "，响应: " + response.body());
    }

    private HttpRequest.Builder authorizedBuilder(StorageProvider provider, URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
        String username = parseConfigValue(provider, "username", "user");
        String password = parseConfigValue(provider, "password");
        if (username != null && password != null) {
            String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + token);
        }
        return builder;
    }

    private Map<String, Object> parseWebDavListing(StorageProvider provider, UserAccount user, Path relativeDirectory, String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        Path rootRelative = relativeDirectory == null ? Path.of("") : relativeDirectory.normalize();
        String currentPath = toBrowserPath(provider, user, rootRelative);
        String currentHref = normalizeHref(resolveWebDavUri(provider, user, rootRelative).getPath());

        List<Map<String, Object>> directories = new ArrayList<>();
        List<Map<String, Object>> files = new ArrayList<>();

        NodeList responses = document.getElementsByTagNameNS("DAV:", "response");
        for (int i = 0; i < responses.getLength(); i++) {
            Element response = (Element) responses.item(i);
            String href = childText(response, "DAV:", "href");
            if (href == null) {
                continue;
            }
            String normalizedHref = normalizeHref(href);
            if (normalizedHref.equals(currentHref)) {
                continue;
            }

            boolean directory = isDirectoryResponse(response);
            String name = decodeLastSegment(normalizedHref);
            if (name == null || name.isBlank()) {
                continue;
            }

            Path childRelative = rootRelative.resolve(name).normalize();
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("path", toBrowserPath(provider, user, childRelative));
            item.put("isDirectory", directory);
            if (directory) {
                item.put("photoCount", 0);
                directories.add(item);
            } else {
                item.put("size", parseLong(childText(response, "DAV:", "getcontentlength")));
                item.put("lastModified", parseHttpDate(childText(response, "DAV:", "getlastmodified")));
                files.add(item);
            }
        }

        directories.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));
        files.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("path", currentPath);
        result.put("parent", rootRelative.getNameCount() > 0 ? toBrowserPath(provider, user, rootRelative.getParent()) : null);
        result.put("directories", directories);
        result.put("files", files);
        return result;
    }

    private String toBrowserPath(StorageProvider provider, UserAccount user, Path relativePath) {
        Path base = resolveWebDavRoot(provider);
        if (isMultiUserScoped(user)) {
            base = base.resolve(String.valueOf(user.getId()));
        }
        Path fullPath = relativePath == null ? base : base.resolve(relativePath).normalize();
        String value = "/" + fullPath.toString().replace('\\', '/');
        return value.replaceAll("/+", "/");
    }

    private String normalizeHref(String href) {
        if (href == null || href.isBlank()) {
            return "/";
        }
        String value = URI.create(href).getPath();
        if (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean isDirectoryResponse(Element response) {
        NodeList resourceTypes = response.getElementsByTagNameNS("DAV:", "resourcetype");
        for (int i = 0; i < resourceTypes.getLength(); i++) {
            NodeList children = resourceTypes.item(i).getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child instanceof Element && "collection".equalsIgnoreCase(child.getLocalName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String childText(Element root, String namespace, String localName) {
        NodeList list = namespace == null
            ? root.getElementsByTagName(localName)
            : root.getElementsByTagNameNS(namespace, localName);
        if (list.getLength() == 0) {
            return null;
        }
        String value = list.item(0).getTextContent();
        return value == null ? null : value.trim();
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseHttpDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Long parseIsoDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String decodeLastSegment(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        int index = normalized.lastIndexOf('/');
        String segment = index >= 0 ? normalized.substring(index + 1) : normalized;
        return java.net.URLDecoder.decode(segment, StandardCharsets.UTF_8);
    }

    private URI resolveWebDavUri(StorageProvider provider, UserAccount user, Path relativePath) {
        String endpoint = trimToNull(provider.getEndpoint());
        if (endpoint == null) {
            throw new IllegalArgumentException("WebDAV endpoint 未配置");
        }
        StringBuilder builder = new StringBuilder(endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint);
        Path root = resolveWebDavRoot(provider);
        Path combined = root;
        if (user != null && isMultiUserScoped(user)) {
            combined = combined.resolve(String.valueOf(user.getId()));
        }
        if (relativePath != null) {
            combined = combined.resolve(relativePath).normalize();
        }
        for (Path segment : combined) {
            builder.append('/').append(encodeSegment(segment.toString()));
        }
        return URI.create(builder.toString());
    }

    private Path resolveTenantRelativeRoot(UserAccount user) {
        if (user != null && isMultiUserScoped(user)) {
            return Path.of(String.valueOf(user.getId()));
        }
        return Path.of("");
    }

    private Path resolveWebDavRoot(StorageProvider provider) {
        String base = trimToNull(provider.getBaseDirectory());
        if (base == null) {
            base = trimToNull(provider.getBucketName());
        }
        if (base == null) {
            return Path.of("");
        }
        return Path.of(base.startsWith("/") ? base.substring(1) : base).normalize();
    }

    private boolean isMultiUserScoped(UserAccount user) {
        return user != null && systemConfigService.isMultiUserEnabled();
    }

    private String parseConfigValue(StorageProvider provider, String... keys) {
        Map<String, Object> config = parseConfig(provider.getConfigJson());
        for (String key : keys) {
            String value = trimToNull(config.get(key) == null ? null : String.valueOf(config.get(key)));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean isS3CompatibleProvider(StorageProvider provider) {
        if (provider == null || provider.getType() == null) {
            return false;
        }
        return provider.getType() == StorageType.S3_COMPATIBLE
            || provider.getType() == StorageType.MINIO
            || provider.getType() == StorageType.OSS
            || provider.getType() == StorageType.R2
            || provider.getType() == StorageType.GCS
            || provider.getType() == StorageType.OBS
            || provider.getType() == StorageType.TOS
            || provider.getType() == StorageType.BOS
            || provider.getType() == StorageType.UCLOUD_US3
            || provider.getType() == StorageType.JD_JSS
            || provider.getType() == StorageType.WASABI
            || provider.getType() == StorageType.QINIU_KODO
            || provider.getType() == StorageType.B2;
    }

    private HttpResponse<byte[]> sendAzureBlobRequest(StorageProvider provider,
                                                      String method,
                                                      String blobKey,
                                                      Map<String, String> query,
                                                      Map<String, String> extraHeaders,
                                                      byte[] body) throws Exception {
        URI uri = buildAzureBlobUri(provider, blobKey, query);
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }
        String contentLength = body == null ? null : String.valueOf(body.length);
        if (contentLength != null && !"0".equals(contentLength)) {
            headers.put("Content-Length", contentLength);
        }
        headers.put("x-ms-date", DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.now()));
        headers.put("x-ms-version", "2021-12-02");
        String authorization = buildAzureBlobAuthorization(provider, method, uri, query, headers, contentLength);
        if (authorization != null) {
            headers.put("Authorization", authorization);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
        headers.forEach(builder::header);
        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else if ("HEAD".equalsIgnoreCase(method)) {
            builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
        } else if ("DELETE".equalsIgnoreCase(method)) {
            builder.DELETE();
        } else if ("PUT".equalsIgnoreCase(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));
        } else {
            builder.method(method.toUpperCase(), HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private URI buildAzureBlobUri(StorageProvider provider, String blobKey, Map<String, String> query) {
        String endpoint = trimToNull(provider.getEndpoint());
        if (endpoint == null) {
            throw new IllegalArgumentException("Azure Blob endpoint 未配置");
        }
        String normalizedEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        String container = resolveAzureBlobContainer(provider);
        StringBuilder path = new StringBuilder();
        path.append('/').append(encodePathSegment(container));
        if (blobKey != null && !blobKey.isBlank()) {
            for (String segment : blobKey.split("/")) {
                if (!segment.isBlank()) {
                    path.append('/').append(encodePathSegment(segment));
                }
            }
        }
        URI baseUri = URI.create(normalizedEndpoint + path);
        String sasToken = parseConfigValue(provider, "sasToken", "sharedAccessSignature");
        return appendQueryParameters(baseUri, query, sasToken);
    }

    private URI appendQueryParameters(URI baseUri, Map<String, String> query, String rawQueryString) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (rawQueryString != null && !rawQueryString.isBlank()) {
            String normalized = rawQueryString.startsWith("?") ? rawQueryString.substring(1) : rawQueryString;
            for (String pair : normalized.split("&")) {
                if (pair.isBlank()) {
                    continue;
                }
                int index = pair.indexOf('=');
                if (index < 0) {
                    merged.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                } else {
                    merged.put(
                        URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8)
                    );
                }
            }
        }
        if (query != null) {
            merged.putAll(query);
        }
        String queryString = merged.isEmpty() ? null : toCanonicalQuery(merged);
        try {
            return new URI(baseUri.getScheme(), baseUri.getAuthority(), baseUri.getPath(), queryString, null);
        } catch (Exception e) {
            throw new IllegalArgumentException("Azure Blob URI 构建失败", e);
        }
    }

    private String buildAzureBlobAuthorization(StorageProvider provider,
                                               String method,
                                               URI uri,
                                               Map<String, String> query,
                                               Map<String, String> headers,
                                               String contentLength) {
        String accountKey = parseConfigValue(provider, "accountKey");
        if (accountKey == null || accountKey.isBlank()) {
            return null;
        }
        String accountName = resolveAzureBlobAccountName(provider);
        String canonicalizedHeaders = headers.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().toLowerCase().startsWith("x-ms-"))
            .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
            .map(entry -> entry.getKey().toLowerCase() + ":" + (entry.getValue() == null ? "" : entry.getValue().trim()))
            .collect(java.util.stream.Collectors.joining("\n"));
        if (!canonicalizedHeaders.isBlank()) {
            canonicalizedHeaders = canonicalizedHeaders + "\n";
        }
        String canonicalizedResource = buildAzureCanonicalizedResource(accountName, uri.getPath(), query);
        String stringToSign = method.toUpperCase() + "\n"
            + "\n"
            + "\n"
            + (contentLength == null || "0".equals(contentLength) ? "" : contentLength) + "\n"
            + "\n"
            + (headers.getOrDefault("Content-Type", headers.getOrDefault("content-type", ""))) + "\n"
            + "\n"
            + "\n"
            + "\n"
            + "\n"
            + "\n"
            + "\n"
            + canonicalizedHeaders
            + canonicalizedResource;
        try {
            byte[] decodedKey = Base64.getDecoder().decode(accountKey);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(decodedKey, "HmacSHA256"));
            String signature = Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
            return "SharedKey " + accountName + ":" + signature;
        } catch (Exception e) {
            throw new IllegalArgumentException("Azure Blob 账号密钥无效: " + userPathService.sanitizeVisibleText(e.getMessage()), e);
        }
    }

    private String buildAzureCanonicalizedResource(String accountName, String path, Map<String, String> query) {
        StringBuilder resource = new StringBuilder("/").append(accountName).append(path == null ? "" : path);
        if (query != null && !query.isEmpty()) {
            query.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> resource.append('\n')
                    .append(entry.getKey().toLowerCase())
                    .append(':')
                    .append(entry.getValue() == null ? "" : entry.getValue()));
        }
        return resource.toString();
    }

    private String resolveAzureBlobAccountName(StorageProvider provider) {
        String accountName = parseConfigValue(provider, "accountName");
        if (accountName != null) {
            return accountName;
        }
        String endpoint = trimToNull(provider.getEndpoint());
        if (endpoint == null) {
            throw new IllegalArgumentException("Azure Blob endpoint 未配置");
        }
        URI uri = URI.create(endpoint);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Azure Blob endpoint 缺少主机名");
        }
        int dot = host.indexOf('.');
        return dot > 0 ? host.substring(0, dot) : host;
    }

    private String resolveAzureBlobContainer(StorageProvider provider) {
        String container = trimToNull(provider.getBucketName());
        if (container == null) {
            throw new IllegalArgumentException("Azure Blob 缺少容器名配置");
        }
        return container;
    }

    private String buildAzureBlobKey(StorageProvider provider, UserAccount user, Path relativePath) {
        return joinStorageKey(resolveTenantRelativeRoot(user), resolveRemotePath(provider, relativePath));
    }

    private String buildAzureBlobDirectoryPrefix(StorageProvider provider, UserAccount user, Path relativePath) {
        String prefix = buildAzureBlobKey(provider, user, relativePath);
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private Path resolveRemoteBase(StorageProvider provider) {
        String base = trimToNull(provider.getBaseDirectory());
        if (base == null) {
            base = trimToNull(provider.getBucketName());
        }
        if (base == null) {
            base = "/";
        }
        if (!base.startsWith("/")) {
            base = "/" + base;
        }
        return Path.of(base).normalize();
    }

    private String resolveRemotePath(StorageProvider provider, Path relativePath) {
        Path base = resolveRemoteBase(provider);
        Path combined = (relativePath == null ? base : base.resolve(relativePath).normalize());
        String value = combined.toString().replace('\\', '/');
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private String joinStorageKey(Path tenantRoot, String remotePath) {
        String tenant = tenantRoot == null ? "" : tenantRoot.toString().replace('\\', '/');
        String remote = remotePath == null ? "" : remotePath;
        if (tenant.isBlank()) {
            return remote;
        }
        if (remote.isBlank()) {
            return tenant;
        }
        return tenant + "/" + remote;
    }

    private String toAzureBlobBrowserPath(StorageProvider provider, UserAccount user, Path relativePath) {
        return toLogicalBrowserPath(relativePath);
    }

    private boolean azureBlobExists(StorageProvider provider, String key) throws Exception {
        HttpResponse<byte[]> response = sendAzureBlobRequest(provider, "HEAD", key, null, Map.of(), null);
        if (response.statusCode() == 404) {
            return false;
        }
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private List<String> listAzureBlobKeys(StorageProvider provider, String prefix) throws Exception {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("restype", "container");
        query.put("comp", "list");
        if (prefix != null && !prefix.isBlank()) {
            query.put("prefix", prefix);
        }
        query.put("maxresults", "1000");
        HttpResponse<byte[]> response = sendAzureBlobRequest(provider, "GET", null, query, Map.of(), null);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("读取 Azure Blob 列表失败，状态码: " + response.statusCode());
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(response.body()));
        List<String> keys = new ArrayList<>();
        NodeList blobs = document.getElementsByTagName("Blob");
        for (int i = 0; i < blobs.getLength(); i++) {
            Element element = (Element) blobs.item(i);
            String key = childText(element, null, "Name");
            if (key != null && !key.isBlank()) {
                keys.add(key);
            }
        }
        return keys;
    }

    private void copyAzureBlob(StorageProvider provider, String sourceKey, String targetKey) throws Exception {
        URI sourceUri = buildAzureBlobUri(provider, sourceKey, null);
        HttpResponse<byte[]> response = sendAzureBlobRequest(
            provider,
            "PUT",
            targetKey,
            null,
            Map.of(
                "x-ms-copy-source", sourceUri.toString(),
                "x-ms-blob-type", "BlockBlob"
            ),
            new byte[0]
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("复制 Azure Blob 失败，状态码: " + response.statusCode());
        }
    }

    private HttpResponse<byte[]> sendS3Request(StorageProvider provider,
                                               String method,
                                               String key,
                                               Map<String, String> query,
                                               Map<String, String> extraHeaders,
                                               byte[] body,
                                               String overridePayloadHash) throws Exception {
        S3RequestContext context = buildS3RequestContext(provider, key, query);
        String payloadHash = overridePayloadHash != null
            ? overridePayloadHash
            : sha256Hex(body == null ? new byte[0] : body);

        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }
        headers.put("Host", context.hostHeader);
        headers.put("x-amz-content-sha256", payloadHash);
        headers.put("x-amz-date", context.amzDate);
        if (context.sessionToken != null) {
            headers.put("x-amz-security-token", context.sessionToken);
        }
        headers.put("Authorization", buildS3Authorization(provider, context, method, payloadHash, headers));

        HttpRequest.Builder builder = HttpRequest.newBuilder(context.uri);
        headers.forEach(builder::header);
        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else if ("HEAD".equalsIgnoreCase(method)) {
            builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
        } else if ("DELETE".equalsIgnoreCase(method)) {
            builder.DELETE();
        } else if (body != null) {
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            if (status == 404 && "HEAD".equalsIgnoreCase(method)) {
                return response;
            }
            String message = response.body() == null ? "" : new String(response.body(), StandardCharsets.UTF_8);
            throw new IOException("对象存储请求失败，状态码: " + status + "，响应: " + userPathService.sanitizeVisibleText(message));
        }
        return response;
    }

    private S3RequestContext buildS3RequestContext(StorageProvider provider, String key, Map<String, String> query) {
        URI endpoint = normalizeS3Endpoint(provider.getEndpoint());
        String bucket = resolveS3Bucket(provider);
        String path = endpoint.getPath() == null ? "" : endpoint.getPath();
        StringBuilder pathBuilder = new StringBuilder();
        if (!path.isBlank() && !"/".equals(path)) {
            pathBuilder.append(path.startsWith("/") ? path : "/" + path);
        }
        pathBuilder.append('/').append(encodePathSegment(bucket));
        if (key != null && !key.isBlank()) {
            for (String segment : key.split("/")) {
                if (segment.isBlank()) {
                    continue;
                }
                pathBuilder.append('/').append(encodePathSegment(segment));
            }
        }
        String canonicalUri = pathBuilder.toString().replaceAll("//+", "/");
        String canonicalQuery = toCanonicalQuery(query);
        String uriValue = endpoint.getScheme() + "://" + endpoint.getAuthority() + canonicalUri
            + (canonicalQuery.isBlank() ? "" : "?" + canonicalQuery);
        Instant now = Instant.now();
        String amzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(now);
        String dateStamp = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(now);
        Map<String, Object> config = parseConfig(provider.getConfigJson());
        String region = firstNonBlank(trimToNull(asString(config.get("region"))), provider.getType() == StorageType.R2 ? "auto" : "us-east-1");
        String accessKeyId = firstNonBlank(
            trimToNull(asString(config.get("accessKeyId"))),
            trimToNull(asString(config.get("accessKey"))),
            trimToNull(asString(config.get("ak")))
        );
        String accessKeySecret = firstNonBlank(
            trimToNull(asString(config.get("accessKeySecret"))),
            trimToNull(asString(config.get("secretKey"))),
            trimToNull(asString(config.get("sk")))
        );
        String sessionToken = firstNonBlank(
            trimToNull(asString(config.get("sessionToken"))),
            trimToNull(asString(config.get("securityToken")))
        );
        if (accessKeyId == null || accessKeySecret == null) {
            throw new IllegalArgumentException("对象存储缺少 accessKey / secretKey 配置");
        }
        return new S3RequestContext(
            URI.create(uriValue),
            endpoint.getAuthority(),
            region,
            accessKeyId,
            accessKeySecret,
            sessionToken,
            amzDate,
            dateStamp,
            canonicalUri,
            canonicalQuery
        );
    }

    private String buildS3Authorization(StorageProvider provider,
                                        S3RequestContext context,
                                        String method,
                                        String payloadHash,
                                        Map<String, String> headers) {
        Map<String, String> canonicalHeadersMap = new TreeMap<>();
        headers.forEach((key, value) -> canonicalHeadersMap.put(key.toLowerCase(), value == null ? "" : value.trim().replaceAll("\\s+", " ")));
        StringBuilder canonicalHeaders = new StringBuilder();
        List<String> signedHeaderNames = new ArrayList<>();
        canonicalHeadersMap.forEach((key, value) -> {
            canonicalHeaders.append(key).append(':').append(value).append('\n');
            signedHeaderNames.add(key);
        });
        String signedHeaders = String.join(";", signedHeaderNames);
        String canonicalRequest = method.toUpperCase() + "\n"
            + context.canonicalUri + "\n"
            + context.canonicalQuery + "\n"
            + canonicalHeaders + "\n"
            + signedHeaders + "\n"
            + payloadHash;
        String scope = context.dateStamp + "/" + context.region + "/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n"
            + context.amzDate + "\n"
            + scope + "\n"
            + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] signingKey = hmacSha256(("AWS4" + context.secretKey).getBytes(StandardCharsets.UTF_8), context.dateStamp);
        signingKey = hmacSha256(signingKey, context.region);
        signingKey = hmacSha256(signingKey, "s3");
        signingKey = hmacSha256(signingKey, "aws4_request");
        String signature = bytesToHex(hmacSha256(signingKey, stringToSign));
        return "AWS4-HMAC-SHA256 Credential=" + context.accessKeyId + "/" + scope
            + ", SignedHeaders=" + signedHeaders
            + ", Signature=" + signature;
    }

    private String buildS3PresignedUrl(StorageProvider provider, String key, long expiresSeconds) {
        Map<String, String> query = new LinkedHashMap<>();
        Instant now = Instant.now();
        String amzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(now);
        String dateStamp = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(now);
        Map<String, Object> config = parseConfig(provider.getConfigJson());
        String region = firstNonBlank(trimToNull(asString(config.get("region"))), provider.getType() == StorageType.R2 ? "auto" : "us-east-1");
        String accessKeyId = firstNonBlank(trimToNull(asString(config.get("accessKeyId"))), trimToNull(asString(config.get("accessKey"))), trimToNull(asString(config.get("ak"))));
        String accessKeySecret = firstNonBlank(trimToNull(asString(config.get("accessKeySecret"))), trimToNull(asString(config.get("secretKey"))), trimToNull(asString(config.get("sk"))));
        String sessionToken = firstNonBlank(trimToNull(asString(config.get("sessionToken"))), trimToNull(asString(config.get("securityToken"))));
        URI endpoint = normalizeS3Endpoint(provider.getEndpoint());
        String bucket = resolveS3Bucket(provider);
        StringBuilder canonicalUri = new StringBuilder();
        if (endpoint.getPath() != null && !endpoint.getPath().isBlank() && !"/".equals(endpoint.getPath())) {
            canonicalUri.append(endpoint.getPath().startsWith("/") ? endpoint.getPath() : "/" + endpoint.getPath());
        }
        canonicalUri.append('/').append(encodePathSegment(bucket));
        for (String segment : key.split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            canonicalUri.append('/').append(encodePathSegment(segment));
        }
        String scope = dateStamp + "/" + region + "/s3/aws4_request";
        query.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
        query.put("X-Amz-Credential", accessKeyId + "/" + scope);
        query.put("X-Amz-Date", amzDate);
        query.put("X-Amz-Expires", String.valueOf(expiresSeconds));
        query.put("X-Amz-SignedHeaders", "host");
        if (sessionToken != null) {
            query.put("X-Amz-Security-Token", sessionToken);
        }
        String canonicalQuery = toCanonicalQuery(query);
        String canonicalRequest = "GET\n"
            + canonicalUri + "\n"
            + canonicalQuery + "\n"
            + "host:" + endpoint.getAuthority() + "\n\n"
            + "host\nUNSIGNED-PAYLOAD";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
            + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] signingKey = hmacSha256(("AWS4" + accessKeySecret).getBytes(StandardCharsets.UTF_8), dateStamp);
        signingKey = hmacSha256(signingKey, region);
        signingKey = hmacSha256(signingKey, "s3");
        signingKey = hmacSha256(signingKey, "aws4_request");
        String signature = bytesToHex(hmacSha256(signingKey, stringToSign));
        return endpoint.getScheme() + "://" + endpoint.getAuthority() + canonicalUri + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
    }

    private List<String> listS3Keys(StorageProvider provider, String prefix) throws Exception {
        List<String> keys = new ArrayList<>();
        String continuationToken = null;
        while (true) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("list-type", "2");
            query.put("prefix", prefix == null ? "" : prefix);
            query.put("max-keys", "1000");
            if (continuationToken != null) {
                query.put("continuation-token", continuationToken);
            }
            HttpResponse<byte[]> response = sendS3Request(provider, "GET", null, query, Map.of(), null, null);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(response.body()));
            NodeList contents = document.getElementsByTagName("Contents");
            for (int i = 0; i < contents.getLength(); i++) {
                Element element = (Element) contents.item(i);
                String key = childText(element, null, "Key");
                if (key != null && !key.isBlank()) {
                    keys.add(key);
                }
            }
            String truncated = childText(document.getDocumentElement(), null, "IsTruncated");
            if (!"true".equalsIgnoreCase(truncated)) {
                break;
            }
            continuationToken = childText(document.getDocumentElement(), null, "NextContinuationToken");
            if (continuationToken == null || continuationToken.isBlank()) {
                break;
            }
        }
        return keys;
    }

    private void deleteS3Keys(StorageProvider provider, List<String> keys) throws Exception {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        int batchSize = 1000;
        for (int i = 0; i < keys.size(); i += batchSize) {
            List<String> batch = new ArrayList<>(keys.subList(i, Math.min(keys.size(), i + batchSize)));
            StringBuilder xml = new StringBuilder("<Delete>");
            for (String key : batch) {
                xml.append("<Object><Key>")
                    .append(escapeXml(key))
                    .append("</Key></Object>");
            }
            xml.append("<Quiet>true</Quiet></Delete>");
            Map<String, String> query = new LinkedHashMap<>();
            query.put("delete", "");
            sendS3Request(
                provider,
                "POST",
                null,
                query,
                Map.of("Content-Type", "application/xml"),
                xml.toString().getBytes(StandardCharsets.UTF_8),
                null
            );
        }
    }

    private void copyS3Object(StorageProvider provider, String sourceKey, String targetKey) throws Exception {
        String bucket = resolveS3Bucket(provider);
        sendS3Request(
            provider,
            "PUT",
            targetKey,
            null,
            Map.of("x-amz-copy-source", "/" + bucket + "/" + sourceKey),
            new byte[0],
            sha256Hex(new byte[0])
        );
    }

    private boolean s3ObjectExists(StorageProvider provider, String key) {
        try {
            HttpResponse<byte[]> response = sendS3Request(provider, "HEAD", key, null, Map.of(), null, null);
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private URI normalizeS3Endpoint(String endpointValue) {
        String endpoint = trimToNull(endpointValue);
        if (endpoint == null) {
            throw new IllegalArgumentException("对象存储 endpoint 未配置");
        }
        if (!endpoint.contains("://")) {
            endpoint = "https://" + endpoint;
        }
        return URI.create(endpoint);
    }

    private String resolveS3Bucket(StorageProvider provider) {
        String bucket = trimToNull(provider.getBucketName());
        if (bucket == null) {
            throw new IllegalArgumentException("对象存储 bucketName 未配置");
        }
        return bucket;
    }

    private String buildS3Key(StorageProvider provider, UserAccount user, Path relativePath) {
        String key = combineRemoteSegments(provider.getBaseDirectory(), resolveTenantPrefix(user), toUnixPath(relativePath));
        return key.startsWith("/") ? key.substring(1) : key;
    }

    private String buildS3DirectoryPrefix(StorageProvider provider, UserAccount user, Path relativePath) {
        String prefix = buildS3Key(provider, user, relativePath);
        if (prefix.isBlank()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private String toS3BrowserPath(StorageProvider provider, UserAccount user, Path relativePath) {
        return toLogicalBrowserPath(relativePath);
    }

    private String toCanonicalQuery(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        return query.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> encodeQueryValue(entry.getKey()) + "=" + encodeQueryValue(entry.getValue() == null ? "" : entry.getValue()))
            .reduce((left, right) -> left + "&" + right)
            .orElse("");
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("%2F", "/");
    }

    private String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("计算 SHA-256 失败", e);
        }
    }

    private byte[] hmacSha256(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("计算 HMAC-SHA256 失败", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private String escapeXml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private Map<String, Object> parseConfig(String rawJson) {
        String configJson = trimToNull(rawJson);
        if (configJson == null) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析存储配置失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String contentType(MultipartFile file) {
        return trimToNull(file.getContentType()) != null ? file.getContentType() : "application/octet-stream";
    }

    private String probeContentType(String filename) {
        if (filename == null || filename.isBlank()) {
            return "application/octet-stream";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    private String responseBodyText(HttpResponse<byte[]> response) {
        if (response == null || response.body() == null || response.body().length == 0) {
            return "";
        }
        return userPathService.sanitizeVisibleText(new String(response.body(), StandardCharsets.UTF_8));
    }

    private Long parseUnixSeconds(String value) {
        Long parsed = parseLong(value);
        return parsed == null ? null : parsed * 1000L;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String stripTrailingSlash(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String encodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private FTPClient openFtpClient(StorageProvider provider) throws IOException {
        Map<String, Object> config = parseConfig(provider.getConfigJson());
        FtpEndpoint endpoint = parseFtpEndpoint(provider.getEndpoint());
        String username = firstNonBlank(parseConfigValue(provider, "username", "user"), "anonymous");
        String password = firstNonBlank(parseConfigValue(provider, "password"), "");

        FTPClient ftpClient = new FTPClient();
        Integer connectTimeout = parseInteger(config.get("connectTimeoutMillis"));
        Integer dataTimeout = parseInteger(config.get("dataTimeoutMillis"));
        String controlEncoding = trimToNull(asString(config.get("controlEncoding")));
        if (connectTimeout != null) {
            ftpClient.setConnectTimeout(connectTimeout);
        }
        if (dataTimeout != null) {
            ftpClient.setDataTimeout(dataTimeout);
        }
        if (controlEncoding != null) {
            ftpClient.setControlEncoding(controlEncoding);
        }

        ftpClient.connect(endpoint.host, endpoint.port);
        if (!ftpClient.login(username, password)) {
            throw new IOException("FTP 登录失败: " + ftpClient.getReplyString());
        }
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        if (!Boolean.FALSE.equals(parseBoolean(config.get("passiveMode")))) {
            ftpClient.enterLocalPassiveMode();
        }
        return ftpClient;
    }

    private void closeFtpClient(FTPClient ftpClient) {
        if (ftpClient == null) {
            return;
        }
        try {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.logout();
                } finally {
                    ftpClient.disconnect();
                }
            }
        } catch (IOException e) {
            log.warn("关闭 FTP 连接失败: {}", e.getMessage());
        }
    }

    private FTPFile resolveFtpFile(FTPClient ftpClient, String remotePath) throws IOException {
        FTPFile exact = ftpClient.mlistFile(remotePath);
        if (exact != null) {
            return exact;
        }
        FTPFile[] files = ftpClient.listFiles(remotePath);
        if (files != null && files.length == 1) {
            return files[0];
        }
        return null;
    }

    private void ensureFtpDirectories(FTPClient ftpClient, String remoteFilePath) throws IOException {
        int lastSlash = remoteFilePath.lastIndexOf('/');
        if (lastSlash <= 0) {
            return;
        }
        String directoryPath = remoteFilePath.substring(0, lastSlash);
        ensureFtpDirectoryPath(ftpClient, directoryPath);
    }

    private void ensureFtpDirectoryPath(FTPClient ftpClient, String directoryPath) throws IOException {
        if (directoryPath == null || directoryPath.isBlank() || "/".equals(directoryPath)) {
            return;
        }
        String[] segments = directoryPath.split("/");
        StringBuilder current = new StringBuilder();
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            current.append('/').append(segment);
            String currentPath = current.toString();
            if (ftpClient.changeWorkingDirectory(currentPath)) {
                continue;
            }
            if (!ftpClient.makeDirectory(currentPath) && !ftpClient.changeWorkingDirectory(currentPath)) {
                throw new IOException("创建 FTP 目录失败: " + currentPath + "，响应: " + ftpClient.getReplyString());
            }
        }
    }

    private Map<String, Object> listFtpDirectory(StorageProvider provider, UserAccount user, Path relativeDirectory) throws Exception {
        FTPClient ftpClient = openFtpClient(provider);
        try {
            Path rootRelative = relativeDirectory == null ? Path.of("") : relativeDirectory.normalize();
            String currentPath = toFtpBrowserPath(provider, user, rootRelative);
            String remotePath = buildFtpDirectoryPath(provider, user, rootRelative);
            FTPFile[] entries = ftpClient.listFiles(remotePath);
            if (entries == null) {
                throw new IOException("读取 FTP 目录失败: " + ftpClient.getReplyString());
            }

            List<Map<String, Object>> directories = new ArrayList<>();
            List<Map<String, Object>> files = new ArrayList<>();
            for (FTPFile entry : entries) {
                if (entry == null) {
                    continue;
                }
                String name = trimToNull(entry.getName());
                if (name == null || ".".equals(name) || "..".equals(name)) {
                    continue;
                }
                Path childRelative = rootRelative.resolve(name).normalize();
                LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                item.put("name", name);
                item.put("path", toFtpBrowserPath(provider, user, childRelative));
                item.put("isDirectory", entry.isDirectory());
                if (entry.isDirectory()) {
                    item.put("photoCount", 0);
                    directories.add(item);
                } else {
                    item.put("size", entry.getSize());
                    item.put("lastModified", entry.getTimestamp() != null ? entry.getTimestamp().getTimeInMillis() : null);
                    files.add(item);
                }
            }

            directories.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));
            files.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));

            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("path", currentPath);
            result.put("parent", rootRelative.getNameCount() > 0 ? toFtpBrowserPath(provider, user, rootRelative.getParent()) : null);
            result.put("directories", directories);
            result.put("files", files);
            return result;
        } finally {
            closeFtpClient(ftpClient);
        }
    }

    private void deleteFtpPath(FTPClient ftpClient, String remotePath) throws IOException {
        FTPFile file = resolveFtpFile(ftpClient, remotePath);
        if (file == null) {
            return;
        }
        if (file.isDirectory()) {
            FTPFile[] children = ftpClient.listFiles(remotePath);
            if (children == null) {
                throw new IOException("读取 FTP 目录失败: " + ftpClient.getReplyString());
            }
            for (FTPFile child : children) {
                if (child == null || ".".equals(child.getName()) || "..".equals(child.getName())) {
                    continue;
                }
                deleteFtpPath(ftpClient, remotePath + "/" + child.getName());
            }
            if (!ftpClient.removeDirectory(remotePath)) {
                throw new IOException("删除 FTP 目录失败: " + ftpClient.getReplyString());
            }
            return;
        }
        if (!ftpClient.deleteFile(remotePath)) {
            throw new IOException("删除 FTP 文件失败: " + ftpClient.getReplyString());
        }
    }

    private String buildFtpDirectoryPath(StorageProvider provider, UserAccount user, Path relativePath) {
        FtpEndpoint endpoint = parseFtpEndpoint(provider.getEndpoint());
        String combined = combineRemoteSegments(endpoint.basePath, provider.getBaseDirectory(), resolveTenantPrefix(user), toUnixPath(relativePath));
        return combined.isBlank() ? "/" : combined;
    }

    private String toFtpBrowserPath(StorageProvider provider, UserAccount user, Path relativePath) {
        FtpEndpoint endpoint = parseFtpEndpoint(provider.getEndpoint());
        String combined = combineRemoteSegments(endpoint.basePath, provider.getBaseDirectory(), resolveTenantPrefix(user), toUnixPath(relativePath));
        return combined.isBlank() ? "/" : combined;
    }

    private COSClient createCosClient(StorageProvider provider) {
        Map<String, Object> config = parseConfig(provider.getConfigJson());
        String accessKeyId = firstNonBlank(
            trimToNull(asString(config.get("accessKeyId"))),
            trimToNull(asString(config.get("secretId")))
        );
        String accessKeySecret = firstNonBlank(
            trimToNull(asString(config.get("accessKeySecret"))),
            trimToNull(asString(config.get("secretKey")))
        );
        String region = trimToNull(asString(config.get("region")));
        if (accessKeyId == null || accessKeySecret == null || region == null) {
            throw new IllegalArgumentException("COS 存储配置不完整，至少需要 accessKeyId/secretId、accessKeySecret/secretKey、region");
        }
        COSCredentials credentials = new BasicCOSCredentials(accessKeyId, accessKeySecret);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        return new COSClient(credentials, clientConfig);
    }

    private String resolveCosBucket(StorageProvider provider) {
        String bucket = trimToNull(provider.getBucketName());
        if (bucket == null) {
            throw new IllegalArgumentException("COS bucketName 未配置");
        }
        return bucket;
    }

    private String buildCosKey(StorageProvider provider, UserAccount user, Path relativePath) {
        String key = combineRemoteSegments(provider.getBaseDirectory(), resolveTenantPrefix(user), toUnixPath(relativePath));
        return key.startsWith("/") ? key.substring(1) : key;
    }

    private String buildCosDirectoryPrefix(StorageProvider provider, UserAccount user, Path relativePath) {
        String prefix = buildCosKey(provider, user, relativePath);
        if (prefix.isBlank()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private String toCosBrowserPath(StorageProvider provider, UserAccount user, Path relativePath) {
        return toLogicalBrowserPath(relativePath);
    }

    private String resolveTenantPrefix(UserAccount user) {
        return isMultiUserScoped(user) ? String.valueOf(user.getId()) : null;
    }

    private List<String> listCosKeys(COSClient cosClient, String bucket, String prefix) {
        List<String> keys = new ArrayList<>();
        ListObjectsRequest request = new ListObjectsRequest();
        request.setBucketName(bucket);
        request.setPrefix(prefix);
        request.setMaxKeys(1000);
        ObjectListing listing = cosClient.listObjects(request);
        while (true) {
            if (listing.getObjectSummaries() != null) {
                for (COSObjectSummary summary : listing.getObjectSummaries()) {
                    if (summary != null && summary.getKey() != null) {
                        keys.add(summary.getKey());
                    }
                }
            }
            if (!listing.isTruncated()) {
                break;
            }
            listing = cosClient.listNextBatchOfObjects(listing);
        }
        return keys;
    }

    private void deleteCosKeys(COSClient cosClient, String bucket, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        int batchSize = 1000;
        for (int i = 0; i < keys.size(); i += batchSize) {
            List<String> batch = new ArrayList<>(keys.subList(i, Math.min(keys.size(), i + batchSize)));
            DeleteObjectsRequest request = new DeleteObjectsRequest(bucket);
            List<DeleteObjectsRequest.KeyVersion> keyVersions = new ArrayList<>();
            for (String key : batch) {
                keyVersions.add(new DeleteObjectsRequest.KeyVersion(key));
            }
            request.setKeys(keyVersions);
            cosClient.deleteObjects(request);
        }
    }

    private boolean cosObjectExists(COSClient cosClient, String bucket, String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        try {
            return cosClient.doesObjectExist(bucket, key);
        } catch (Exception e) {
            return false;
        }
    }

    private String extractLastName(String key) {
        String normalized = trimToNull(key);
        if (normalized == null) {
            return null;
        }
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private String combineRemoteSegments(String... segments) {
        List<String> values = new ArrayList<>();
        for (String segment : segments) {
            String normalized = trimSlashes(trimToNull(segment));
            if (normalized != null && !normalized.isBlank()) {
                values.add(normalized);
            }
        }
        if (values.isEmpty()) {
            return "";
        }
        return "/" + String.join("/", values);
    }

    private String trimSlashes(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String toUnixPath(Path path) {
        return path == null ? null : path.normalize().toString().replace('\\', '/');
    }

    private Integer parseInteger(Object value) {
        String raw = trimToNull(asString(value));
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean parseBoolean(Object value) {
        String raw = trimToNull(asString(value));
        if (raw == null) {
            return null;
        }
        return Boolean.parseBoolean(raw);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private FtpEndpoint parseFtpEndpoint(String rawEndpoint) {
        String endpoint = trimToNull(rawEndpoint);
        if (endpoint == null) {
            throw new IllegalArgumentException("FTP endpoint 未配置");
        }
        if (!endpoint.contains("://")) {
            endpoint = "ftp://" + endpoint;
        }
        URI uri = URI.create(endpoint);
        String host = trimToNull(uri.getHost());
        if (host == null) {
            throw new IllegalArgumentException("FTP endpoint 缺少主机名");
        }
        int port = uri.getPort() > 0 ? uri.getPort() : 21;
        return new FtpEndpoint(host, port, uri.getPath());
    }

    private static class FtpEndpoint {
        private final String host;
        private final int port;
        private final String basePath;

        private FtpEndpoint(String host, int port, String basePath) {
            this.host = host;
            this.port = port;
            this.basePath = basePath;
        }
    }

    private static class S3RequestContext {
        private final URI uri;
        private final String hostHeader;
        private final String region;
        private final String accessKeyId;
        private final String secretKey;
        private final String sessionToken;
        private final String amzDate;
        private final String dateStamp;
        private final String canonicalUri;
        private final String canonicalQuery;

        private S3RequestContext(URI uri,
                                 String hostHeader,
                                 String region,
                                 String accessKeyId,
                                 String secretKey,
                                 String sessionToken,
                                 String amzDate,
                                 String dateStamp,
                                 String canonicalUri,
                                 String canonicalQuery) {
            this.uri = uri;
            this.hostHeader = hostHeader;
            this.region = region;
            this.accessKeyId = accessKeyId;
            this.secretKey = secretKey;
            this.sessionToken = sessionToken;
            this.amzDate = amzDate;
            this.dateStamp = dateStamp;
            this.canonicalUri = canonicalUri;
            this.canonicalQuery = canonicalQuery;
        }
    }

    public static class DownloadedFile {
        private final byte[] bytes;
        private final String contentType;
        private final String filename;

        public DownloadedFile(byte[] bytes, String contentType, String filename) {
            this.bytes = bytes == null ? new byte[0] : bytes;
            this.contentType = contentType;
            this.filename = filename;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public String getContentType() {
            return contentType;
        }

        public String getFilename() {
            return filename;
        }
    }

    private static final class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;

        private InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] bytes) {
            this.name = name == null || name.isBlank() ? "file" : name;
            this.originalFilename = originalFilename == null || originalFilename.isBlank() ? this.name : originalFilename;
            this.contentType = contentType;
            this.bytes = bytes == null ? new byte[0] : bytes;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }
}
