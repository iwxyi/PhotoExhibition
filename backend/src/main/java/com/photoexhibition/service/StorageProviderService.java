package com.photoexhibition.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.StorageProvider;
import com.photoexhibition.entity.StorageType;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.region.Region;
import com.photoexhibition.repository.StorageProviderRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageProviderService {

    private final StorageProviderRepository storageProviderRepository;
    private final SystemConfigService systemConfigService;
    private final UserPathService userPathService;
    private final ObjectMapper objectMapper;

    @Transactional
    public StorageProvider ensureDefaultLocalStorageProvider() {
        Optional<StorageProvider> existingDefault = storageProviderRepository.findFirstByIsDefaultTrue();
        if (existingDefault.isPresent()) {
            return existingDefault.get();
        }

        StorageProvider provider = storageProviderRepository.findByName("local-default").orElseGet(StorageProvider::new);
        provider.setName(provider.getName() == null ? "local-default" : provider.getName());
        provider.setType(StorageType.LOCAL);
        provider.setEnabled(true);
        provider.setIsDefault(true);
        provider.setPriority(provider.getPriority() == null ? 0 : provider.getPriority());
        provider.setBaseDirectory(systemConfigService.getLocalStorageRoot());
        provider.setBucketName(null);
        provider.setEndpoint(null);
        StorageProvider saved = storageProviderRepository.save(provider);
        log.info("已初始化默认本地存储提供者: {}", saved.getName());
        return saved;
    }

    @Transactional(readOnly = true)
    public BrowserStorageContext resolveBrowserStorage(UserAccount user, Long requestedProviderId) {
        List<StorageProvider> providers = new ArrayList<>(storageProviderRepository.findAllByOrderByPriorityAscIdAsc());
        if (providers.isEmpty()) {
            providers.add(ensureDefaultLocalStorageProvider());
        }

        StorageProvider selected = selectBrowserProvider(user, requestedProviderId, providers);
        if (selected == null) {
            throw new RuntimeException("当前没有可用于文件浏览器的本地存储提供者");
        }

        ProviderCapability selectedCapability = evaluateProvider(selected, user);
        if (!selectedCapability.isBrowserSupported()) {
            throw new RuntimeException(selectedCapability.getBrowserSupportMessage());
        }

        List<BrowserProviderOption> options = providers.stream()
            .map(provider -> {
                ProviderCapability capability = evaluateProvider(provider, user);
                return new BrowserProviderOption(
                    provider.getId(),
                    provider.getName(),
                    provider.getType(),
                    Boolean.TRUE.equals(provider.getEnabled()),
                    provider.getBaseDirectory(),
                    capability.isBrowserSupported(),
                    capability.isUploadSupported(),
                    capability.isScanSupported(),
                    capability.isPreviewSupported(),
                    capability.getPrimarySupportMessage(),
                    toBrowserScopedBasePath(capability.getScopedRoot())
                );
            })
            .collect(Collectors.toList());

        boolean superAdmin = user != null && user.getRole() == UserRole.SUPER_ADMIN;
        if (!superAdmin) {
            options = options.stream()
                .filter(option -> Objects.equals(option.getId(), selected.getId()))
                .collect(Collectors.toList());
        }

        Path responseBasePath = superAdmin ? selectedCapability.getProviderBasePath() : selectedCapability.getScopedRoot();

        return new BrowserStorageContext(
            selected,
            responseBasePath,
            selectedCapability.getScopedRoot(),
            options
        );
    }

    public Long normalizeRequestedProviderId(UserAccount user, Long requestedProviderId) {
        if (user == null) {
            return requestedProviderId;
        }
        return user.getRole() == UserRole.SUPER_ADMIN ? requestedProviderId : null;
    }

    @Transactional(readOnly = true)
    public StorageProvider resolveUploadProvider(UserAccount user, Long requestedProviderId) {
        List<StorageProvider> providers = new ArrayList<>(storageProviderRepository.findAllByOrderByPriorityAscIdAsc());
        if (providers.isEmpty()) {
            providers.add(ensureDefaultLocalStorageProvider());
        }

        StorageProvider selected = selectUploadProvider(user, requestedProviderId, providers);
        if (selected == null) {
            throw new RuntimeException("当前没有可用于上传的存储提供者");
        }

        ProviderCapability capability = evaluateProvider(selected, user);
        if (!capability.isUploadSupported()) {
            throw new RuntimeException(capability.getUploadSupportMessage());
        }
        return selected;
    }

    @Transactional(readOnly = true)
    public Path resolveAbsoluteBaseDirectory(StorageProvider provider) {
        return userPathService.resolveStorageProviderBaseDirectory(provider);
    }

    @Transactional(readOnly = true)
    public LinkedHashMap<String, Object> describeProviderCapabilities(StorageProvider provider) {
        return describeProviderCapabilities(provider, null);
    }

    @Transactional(readOnly = true)
    public LinkedHashMap<String, Object> describeProviderCapabilities(StorageProvider provider, UserAccount user) {
        LinkedHashMap<String, Object> resp = new LinkedHashMap<>();
        ProviderCapability capability = evaluateProvider(provider, user);
        resp.put("browserSupported", capability.isBrowserSupported());
        resp.put("uploadSupported", capability.isUploadSupported());
        resp.put("scanSupported", capability.isScanSupported());
        resp.put("previewSupported", capability.isPreviewSupported());
        resp.put("supportMessage", capability.getPrimarySupportMessage());
        resp.put("resolvedBaseDirectory", provider == null ? null : provider.getBaseDirectory());
        return resp;
    }

    @Transactional(readOnly = true)
    public LinkedHashMap<String, Object> testProviderAvailability(StorageProvider provider, UserAccount user) {
        LinkedHashMap<String, Object> resp = describeProviderCapabilities(provider, user);
        ProviderCapability capability = evaluateProvider(provider, user);

        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(buildTestCheck("browser", "浏览", capability.isBrowserSupported(), capability.getBrowserSupportMessage()));
        checks.add(buildTestCheck("upload", "上传", capability.isUploadSupported(), capability.getUploadSupportMessage()));
        checks.add(buildTestCheck("scan", "扫描", capability.isScanSupported(), capability.isScanSupported() ? "扫描能力已接通" : capability.getPrimarySupportMessage()));
        checks.add(buildTestCheck("preview", "预览", capability.isPreviewSupported(), capability.isPreviewSupported() ? "预览能力已接通" : capability.getPrimarySupportMessage()));
        String configSummary = buildProviderConfigSummary(provider);
        if (configSummary != null) {
            checks.add(buildTestCheck("config", "当前配置", true, configSummary));
        }

        boolean reachable = false;
        String connectivityMessage;
        try {
            connectivityMessage = performConnectivityProbe(provider, capability);
            reachable = true;
        } catch (Exception e) {
            connectivityMessage = e.getMessage() == null || e.getMessage().isBlank() ? "连通性测试失败" : e.getMessage();
        }
        checks.add(buildTestCheck("connectivity", "连通性", reachable, connectivityMessage));

        boolean authenticated = false;
        String authenticationMessage;
        try {
            authenticationMessage = performAuthenticatedProbe(provider);
            authenticated = true;
        } catch (Exception e) {
            authenticationMessage = e.getMessage() == null || e.getMessage().isBlank() ? "鉴权测试失败" : e.getMessage();
        }
        checks.add(buildTestCheck("authentication", "鉴权", authenticated, authenticationMessage));

        boolean capabilityReady = checks.stream()
            .filter(item -> !"connectivity".equals(item.get("key")) && !"authentication".equals(item.get("key")))
            .allMatch(item -> Boolean.TRUE.equals(item.get("success")));
        boolean success = capabilityReady && reachable && authenticated;

        resp.put("success", success);
        resp.put("reachable", reachable);
        resp.put("authenticated", authenticated);
        resp.put("checks", checks);
        resp.put("message", success ? "存储测试通过，可实际使用" : "存储测试未通过，请检查失败项");
        return resp;
    }

    private Map<String, Object> buildTestCheck(String key, String label, boolean success, String message) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("success", success);
        item.put("message", message);
        return item;
    }

    private String performConnectivityProbe(StorageProvider provider, ProviderCapability capability) throws IOException {
        if (provider == null) {
            throw new IOException("存储提供者不存在");
        }
        switch (provider.getType()) {
            case LOCAL:
            case SFTP:
            case SMB:
            case NFS:
                return probeFilesystem(capability.getScopedRoot());
            case FTP:
                return probeSocketEndpoint(provider.getEndpoint(), "ftp", 21);
            case COS:
                return probeHttpEndpoint(normalizeCosEndpoint(provider.getEndpoint(), provider.getBucketName()));
            case WEBDAV:
            case S3_COMPATIBLE:
            case MINIO:
            case OSS:
            case R2:
            case AZURE_BLOB:
            case OBS:
            case B2:
            case UPYUN:
                return probeHttpEndpoint(provider.getEndpoint());
            default:
                throw new IOException("暂未实现该存储类型的测试探针");
        }
    }

    private String performAuthenticatedProbe(StorageProvider provider) throws IOException {
        if (provider == null || provider.getType() == null) {
            throw new IOException("存储类型不存在");
        }
        switch (provider.getType()) {
            case LOCAL:
            case SFTP:
            case SMB:
            case NFS:
                return probeFilesystem(resolveAbsoluteBaseDirectory(provider));
            case COS:
                return probeCosAccess(provider);
            default:
                return "当前类型暂未实现独立鉴权探测，已完成基础可用性检查";
        }
    }

    private String buildProviderConfigSummary(StorageProvider provider) {
        if (provider == null || provider.getType() == null) {
            return null;
        }
        if (provider.getType() == StorageType.COS) {
            Map<String, Object> config = parseConfig(provider.getConfigJson());
            String region = trimToNull(asString(config.get("region")));
            String secretId = firstNonBlank(
                trimToNull(asString(config.get("secretId"))),
                trimToNull(asString(config.get("accessKeyId")))
            );
            String bucket = trimToNull(provider.getBucketName());
            String endpoint = normalizeCosEndpoint(provider.getEndpoint(), provider.getBucketName());
            List<String> parts = new ArrayList<>();
            if (bucket != null) {
                parts.add("Bucket " + bucket);
            }
            if (region != null) {
                parts.add("Region " + region);
            }
            if (endpoint != null) {
                parts.add("Endpoint " + endpoint);
            }
            if (secretId != null) {
                parts.add("SecretId " + maskSensitiveValue(secretId, 4, 4));
            }
            return parts.isEmpty() ? "当前未解析出 COS 关键配置" : String.join("，", parts);
        }
        return null;
    }

    private String probeFilesystem(Path path) throws IOException {
        if (path == null) {
            throw new IOException("目录路径为空");
        }
        Files.createDirectories(path);
        if (!Files.isDirectory(path)) {
            throw new IOException("目标路径不是目录");
        }
        if (!Files.isReadable(path)) {
            throw new IOException("目录不可读");
        }
        if (!Files.isWritable(path)) {
            throw new IOException("目录不可写");
        }
        return "目录存在，且可读可写";
    }

    private String probeSocketEndpoint(String endpoint, String defaultScheme, int defaultPort) throws IOException {
        URI uri = normalizeEndpointUri(endpoint, defaultScheme, defaultPort);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()), 4000);
        }
        return "主机与端口连接成功";
    }

    private String probeHttpEndpoint(String endpoint) throws IOException {
        URI uri = normalizeEndpointUri(endpoint, "https", 443);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("HEAD");
        int code = connection.getResponseCode();
        if (code >= 200 && code < 500) {
            return "HTTP 端点可达，响应码 " + code;
        }
        throw new IOException("HTTP 端点不可用，响应码 " + code);
    }

    private URI normalizeEndpointUri(String endpoint, String defaultScheme, int defaultPort) {
        String raw = trimToNull(endpoint);
        if (raw == null) {
            throw new RuntimeException("endpoint 不能为空");
        }
        if (!raw.contains("://")) {
            raw = defaultScheme + "://" + raw;
        }
        URI uri = URI.create(raw);
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new RuntimeException("endpoint 主机名无效");
        }
        if (uri.getPort() < 0) {
            try {
                return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    defaultPort,
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
                );
            } catch (Exception e) {
                throw new RuntimeException("endpoint 格式不正确");
            }
        }
        return uri;
    }

    private String probeCosAccess(StorageProvider provider) throws IOException {
        COSClient cosClient = createCosClientForProbe(provider);
        try {
            String bucket = resolveCosBucketForProbe(provider);
            ListObjectsRequest request = new ListObjectsRequest();
            request.setBucketName(bucket);
            request.setMaxKeys(1);
            cosClient.listObjects(request);
            return "鉴权通过，可访问存储桶 " + bucket;
        } catch (CosServiceException e) {
            String code = trimToNull(e.getErrorCode());
            String requestId = trimToNull(e.getRequestId());
            String detail = buildCosErrorDetail(code, e.getStatusCode(), requestId);
            if ("SignatureDoesNotMatch".equals(code)
                || "InvalidAccessKeyId".equals(code)
                || "AccessDenied".equals(code)
                || "InvalidRequest".equals(code)) {
                throw new IOException("COS 鉴权失败，请检查 SecretId / SecretKey、地域和存储桶权限" + detail);
            }
            if ("NoSuchBucket".equals(code)) {
                throw new IOException("COS 存储桶不存在，请检查 Bucket / APPID 配置" + detail);
            }
            String message = firstNonBlank(trimToNull(e.getErrorMessage()), trimToNull(e.getMessage()));
            throw new IOException(firstNonBlank(message, "COS 服务端返回错误") + detail);
        } catch (CosClientException e) {
            throw new IOException(firstNonBlank(trimToNull(e.getMessage()), "COS 客户端访问失败"));
        } finally {
            cosClient.shutdown();
        }
    }

    private String buildCosErrorDetail(String code, int statusCode, String requestId) {
        List<String> parts = new ArrayList<>();
        if (code != null) {
            parts.add("错误码 " + code);
        }
        if (statusCode > 0) {
            parts.add("HTTP " + statusCode);
        }
        if (requestId != null) {
            parts.add("RequestId " + requestId);
        }
        if (parts.isEmpty()) {
            return "";
        }
        return "（" + String.join("，", parts) + "）";
    }

    private String normalizeCosEndpoint(String endpoint, String bucketName) {
        String raw = trimToNull(endpoint);
        if (raw == null) {
            return null;
        }
        String normalized = raw.contains("://") ? raw : "https://" + raw;
        try {
            URI uri = URI.create(normalized);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return raw;
            }
            String bucket = trimToNull(bucketName);
            if (bucket != null) {
                String prefix = bucket.toLowerCase() + ".";
                if (host.toLowerCase().startsWith(prefix)) {
                    host = host.substring(prefix.length());
                }
            }
            URI cleaned = new URI(
                uri.getScheme() == null ? "https" : uri.getScheme(),
                uri.getUserInfo(),
                host,
                uri.getPort(),
                null,
                null,
                null
            );
            return cleaned.toString();
        } catch (Exception ignored) {
            return raw;
        }
    }

    private String parseCosRegion(StorageProvider provider) {
        if (provider == null) {
            return null;
        }
        Map<String, Object> config = parseConfig(provider.getConfigJson());
        return trimToNull(asString(config.get("region")));
    }

    private String buildDefaultCosEndpoint(String region) {
        String normalizedRegion = trimToNull(region);
        return normalizedRegion == null ? null : "https://cos." + normalizedRegion + ".myqcloud.com";
    }

    private COSClient createCosClientForProbe(StorageProvider provider) throws IOException {
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
            throw new IOException("COS 存储缺少 SecretId / SecretKey / Region 配置");
        }
        try {
            COSCredentials credentials = new BasicCOSCredentials(accessKeyId, accessKeySecret);
            ClientConfig clientConfig = new ClientConfig(new Region(region));
            return new COSClient(credentials, clientConfig);
        } catch (IllegalArgumentException e) {
            throw new IOException(firstNonBlank(trimToNull(e.getMessage()), "COS 配置格式不正确"));
        }
    }

    private String resolveCosBucketForProbe(StorageProvider provider) throws IOException {
        String bucket = trimToNull(provider.getBucketName());
        if (bucket == null) {
            throw new IOException("COS 存储缺少 Bucket / APPID 配置");
        }
        return bucket;
    }

    private String maskSensitiveValue(String value, int prefix, int suffix) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "—";
        }
        if (normalized.length() <= prefix + suffix) {
            return normalized.charAt(0) + "***";
        }
        return normalized.substring(0, prefix) + "***" + normalized.substring(normalized.length() - suffix);
    }

    private String toBrowserScopedBasePath(Path scopedRoot) {
        return scopedRoot == null ? null : "/";
    }

    private StorageProvider selectBrowserProvider(UserAccount user, Long requestedProviderId, List<StorageProvider> providers) {
        if (user != null && user.getRole() != UserRole.SUPER_ADMIN) {
            return resolveManagedProvider(user, providers);
        }
        if (requestedProviderId != null) {
            StorageProvider requested = providers.stream()
                .filter(provider -> Objects.equals(provider.getId(), requestedProviderId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("存储提供者不存在"));
            if (!Boolean.TRUE.equals(requested.getEnabled())) {
                throw new RuntimeException("存储提供者已禁用");
            }
            return requested;
        }

        List<Long> candidateIds = new ArrayList<>();
        if (user != null && user.getPreferredStorageProviderId() != null) {
            candidateIds.add(user.getPreferredStorageProviderId());
        }
        providers.stream()
            .filter(provider -> Boolean.TRUE.equals(provider.getIsDefault()))
            .map(StorageProvider::getId)
            .filter(Objects::nonNull)
            .findFirst()
            .ifPresent(candidateIds::add);

        for (Long candidateId : candidateIds) {
            Optional<StorageProvider> provider = providers.stream()
                .filter(item -> Objects.equals(item.getId(), candidateId))
                .findFirst();
            if (provider.isPresent()) {
                ProviderCapability capability = evaluateProvider(provider.get(), user);
                if (capability.isBrowserSupported()) {
                    return provider.get();
                }
            }
        }

        return providers.stream()
            .filter(provider -> evaluateProvider(provider, user).isBrowserSupported())
            .findFirst()
            .orElse(null);
    }

    private StorageProvider selectUploadProvider(UserAccount user, Long requestedProviderId, List<StorageProvider> providers) {
        if (user != null && user.getRole() != UserRole.SUPER_ADMIN) {
            StorageProvider managedProvider = resolveManagedUploadProvider(user, providers);
            if (managedProvider == null) {
                return null;
            }
            if (!evaluateProvider(managedProvider, user).isUploadSupported()) {
                throw new RuntimeException(evaluateProvider(managedProvider, user).getUploadSupportMessage());
            }
            return managedProvider;
        }
        if (requestedProviderId != null) {
            StorageProvider requested = providers.stream()
                .filter(provider -> Objects.equals(provider.getId(), requestedProviderId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("存储提供者不存在"));
            if (!Boolean.TRUE.equals(requested.getEnabled())) {
                throw new RuntimeException("存储提供者已禁用");
            }
            return requested;
        }

        List<Long> candidateIds = new ArrayList<>();
        if (user != null && user.getPreferredStorageProviderId() != null) {
            candidateIds.add(user.getPreferredStorageProviderId());
        }
        providers.stream()
            .filter(provider -> Boolean.TRUE.equals(provider.getIsDefault()))
            .map(StorageProvider::getId)
            .filter(Objects::nonNull)
            .findFirst()
            .ifPresent(candidateIds::add);

        for (Long candidateId : candidateIds) {
            Optional<StorageProvider> provider = providers.stream()
                .filter(item -> Objects.equals(item.getId(), candidateId))
                .findFirst();
            if (provider.isPresent() && evaluateProvider(provider.get(), user).isUploadSupported()) {
                return provider.get();
            }
        }

        return providers.stream()
            .filter(provider -> evaluateProvider(provider, user).isUploadSupported())
            .findFirst()
            .orElse(null);
    }

    private StorageProvider resolveManagedProvider(UserAccount user, List<StorageProvider> providers) {
        Optional<StorageProvider> defaultProvider = providers.stream()
            .filter(provider -> Boolean.TRUE.equals(provider.getIsDefault()))
            .findFirst();
        if (defaultProvider.isPresent() && evaluateProvider(defaultProvider.get(), user).isBrowserSupported()) {
            return defaultProvider.get();
        }

        return providers.stream()
            .filter(provider -> evaluateProvider(provider, user).isBrowserSupported())
            .findFirst()
            .orElse(null);
    }

    private StorageProvider resolveManagedUploadProvider(UserAccount user, List<StorageProvider> providers) {
        Optional<StorageProvider> defaultProvider = providers.stream()
            .filter(provider -> Boolean.TRUE.equals(provider.getIsDefault()))
            .findFirst();
        if (defaultProvider.isPresent() && evaluateProvider(defaultProvider.get(), user).isUploadSupported()) {
            return defaultProvider.get();
        }

        return providers.stream()
            .filter(provider -> evaluateProvider(provider, user).isUploadSupported())
            .findFirst()
            .orElse(null);
    }

    private ProviderCapability evaluateProvider(StorageProvider provider, UserAccount user) {
        if (provider == null) {
            return ProviderCapability.unsupported("存储提供者不存在");
        }
        if (!Boolean.TRUE.equals(provider.getEnabled())) {
            return ProviderCapability.unsupported("存储提供者已禁用");
        }
        if (provider.getType() == StorageType.LOCAL) {
            Path providerBase = resolveAbsoluteBaseDirectory(provider);
            Path scopedRoot = resolveLocalBrowserScopedRoot(providerBase, user);
            return ProviderCapability.partialSupportedWithPreview(
                providerBase,
                scopedRoot,
                true,
                true,
                true,
                "本地存储浏览/管理/上传/扫描/预览已接通",
                "本地存储浏览/管理/上传/扫描/预览已接通"
            );
        }
        if (provider.getType() == StorageType.SFTP
            || provider.getType() == StorageType.SMB
            || provider.getType() == StorageType.NFS) {
            Path providerBase = resolveAbsoluteBaseDirectory(provider);
            Path scanBase = userPathService.resolvePhotoBasePath();
            Path scopedRoot = resolveLocalBrowserScopedRoot(providerBase, user);
            String label = storageTypeLabel(provider.getType());
            if (providerBase.startsWith(scanBase)) {
                return ProviderCapability.partialSupportedWithPreview(
                    providerBase,
                    scopedRoot,
                    true,
                    true,
                    true,
                    label + "（已挂载目录模式）浏览/管理/上传/扫描/预览已接通",
                    label + "（已挂载目录模式）浏览/管理/上传/扫描/预览已接通"
                );
            }
            return ProviderCapability.partialSupportedWithPreview(
                providerBase,
                scopedRoot,
                true,
                false,
                true,
                label + "（已挂载目录模式）浏览/管理/上传/预览已接通，自动扫描仅支持扫描根目录内的路径",
                label + "（已挂载目录模式）浏览/管理/上传/预览已接通，自动扫描仅支持扫描根目录内的路径"
            );
        }

        if (provider.getType() == StorageType.WEBDAV) {
            Map<String, Object> config = parseConfig(provider.getConfigJson());
            String endpoint = trimToNull(provider.getEndpoint());
            String username = firstNonBlank(
                trimToNull(asString(config.get("username"))),
                trimToNull(asString(config.get("user")))
            );
            String password = trimToNull(asString(config.get("password")));
            if (endpoint == null) {
                return ProviderCapability.uploadOnlyUnsupported("WebDAV 存储缺少 endpoint 配置");
            }
            if (username == null || password == null) {
                return ProviderCapability.uploadOnlyUnsupported("WebDAV 存储缺少账号或密码配置");
            }
            Path scopedRoot = resolveRemoteBase(provider);
            if (systemConfigService.isMultiUserEnabled() && user != null) {
                scopedRoot = scopedRoot.resolve(String.valueOf(user.getId())).normalize();
            }
            return ProviderCapability.partialSupportedWithPreview(
                resolveRemoteBase(provider),
                scopedRoot,
                true,
                true,
                true,
                "WebDAV 浏览/管理/上传/扫描/预览已接通",
                "WebDAV 浏览/管理/上传/扫描/预览已接通"
            );
        }

        if (provider.getType() == StorageType.FTP) {
            Map<String, Object> config = parseConfig(provider.getConfigJson());
            String endpoint = trimToNull(provider.getEndpoint());
            String username = firstNonBlank(
                trimToNull(asString(config.get("username"))),
                trimToNull(asString(config.get("user")))
            );
            String password = trimToNull(asString(config.get("password")));
            if (endpoint == null) {
                return ProviderCapability.uploadOnlyUnsupported("FTP 存储缺少 endpoint 配置");
            }
            if (username == null || password == null) {
                return ProviderCapability.uploadOnlyUnsupported("FTP 存储缺少账号或密码配置");
            }
            Path scopedRoot = resolveRemoteBase(provider);
            if (systemConfigService.isMultiUserEnabled() && user != null) {
                scopedRoot = scopedRoot.resolve(String.valueOf(user.getId())).normalize();
            }
            return ProviderCapability.partialSupportedWithPreview(
                resolveRemoteBase(provider),
                scopedRoot,
                true,
                true,
                true,
                "FTP 浏览/管理/上传/扫描/预览已接通",
                "FTP 浏览/管理/上传/扫描/预览已接通"
            );
        }
        if (provider.getType() == StorageType.COS) {
            Map<String, Object> config = parseConfig(provider.getConfigJson());
            List<String> missing = new ArrayList<>();
            String accessKeyId = firstNonBlank(
                trimToNull(asString(config.get("accessKeyId"))),
                trimToNull(asString(config.get("secretId")))
            );
            String accessKeySecret = firstNonBlank(
                trimToNull(asString(config.get("accessKeySecret"))),
                trimToNull(asString(config.get("secretKey")))
            );
            String region = trimToNull(asString(config.get("region")));
            if (trimToNull(provider.getBucketName()) == null) {
                missing.add("bucketName");
            }
            if (region == null) {
                missing.add("region");
            }
            if (accessKeyId == null) {
                missing.add("secretId / accessKeyId");
            }
            if (accessKeySecret == null) {
                missing.add("secretKey / accessKeySecret");
            }
            if (!missing.isEmpty()) {
                return ProviderCapability.uploadOnlyUnsupported("COS 存储缺少配置：" + String.join("、", missing));
            }
            Path scopedRoot = resolveRemoteBase(provider);
            if (systemConfigService.isMultiUserEnabled() && user != null) {
                scopedRoot = scopedRoot.resolve(String.valueOf(user.getId())).normalize();
            }
            return ProviderCapability.partialSupportedWithPreview(
                resolveRemoteBase(provider),
                scopedRoot,
                true,
                true,
                true,
                "COS 浏览/管理/上传/扫描/预览已接通",
                "COS 浏览/管理/上传/扫描/预览已接通"
            );
        }

        if (isS3CompatibleType(provider.getType())) {
            Map<String, Object> config = parseConfig(provider.getConfigJson());
            String endpoint = trimToNull(provider.getEndpoint());
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
            String region = firstNonBlank(
                trimToNull(asString(config.get("region"))),
                defaultRegionFor(provider.getType())
            );
            if (endpoint == null) {
                return ProviderCapability.uploadOnlyUnsupported(storageTypeLabel(provider.getType()) + " 存储缺少 endpoint 配置");
            }
            if (trimToNull(provider.getBucketName()) == null) {
                return ProviderCapability.uploadOnlyUnsupported(storageTypeLabel(provider.getType()) + " 存储缺少 bucketName 配置");
            }
            if (region == null) {
                return ProviderCapability.uploadOnlyUnsupported(storageTypeLabel(provider.getType()) + " 存储缺少 region 配置");
            }
            if (accessKeyId == null || accessKeySecret == null) {
                return ProviderCapability.uploadOnlyUnsupported(storageTypeLabel(provider.getType()) + " 存储缺少密钥配置");
            }
            Path scopedRoot = resolveRemoteBase(provider);
            if (systemConfigService.isMultiUserEnabled() && user != null) {
                scopedRoot = scopedRoot.resolve(String.valueOf(user.getId())).normalize();
            }
            String label = storageTypeLabel(provider.getType());
            return ProviderCapability.partialSupportedWithPreview(
                resolveRemoteBase(provider),
                scopedRoot,
                true,
                true,
                true,
                label + " 浏览/管理/上传/扫描/预览已接通",
                label + " 浏览/管理/上传/扫描/预览已接通"
            );
        }

        if (provider.getType() == StorageType.AZURE_BLOB) {
            Map<String, Object> config = parseConfig(provider.getConfigJson());
            String endpoint = trimToNull(provider.getEndpoint());
            String accountName = trimToNull(asString(config.get("accountName")));
            String accountKey = trimToNull(asString(config.get("accountKey")));
            String sasToken = firstNonBlank(
                trimToNull(asString(config.get("sasToken"))),
                trimToNull(asString(config.get("sharedAccessSignature")))
            );
            if (endpoint == null) {
                return ProviderCapability.uploadOnlyUnsupported("Azure Blob 存储缺少 endpoint 配置");
            }
            if (trimToNull(provider.getBucketName()) == null) {
                return ProviderCapability.uploadOnlyUnsupported("Azure Blob 存储缺少容器名配置");
            }
            if ((accountName == null || accountKey == null) && sasToken == null) {
                return ProviderCapability.uploadOnlyUnsupported("Azure Blob 存储缺少 accountName/accountKey 或 SAS Token 配置");
            }
            Path scopedRoot = resolveRemoteBase(provider);
            if (systemConfigService.isMultiUserEnabled() && user != null) {
                scopedRoot = scopedRoot.resolve(String.valueOf(user.getId())).normalize();
            }
            return ProviderCapability.partialSupportedWithPreview(
                resolveRemoteBase(provider),
                scopedRoot,
                true,
                true,
                true,
                "Azure Blob 浏览/管理/上传/扫描/预览已接通",
                "Azure Blob 浏览/管理/上传/扫描/预览已接通"
            );
        }

        if (provider.getType() == StorageType.UPYUN) {
            String serviceName = trimToNull(provider.getBucketName());
            String operator = firstNonBlank(
                trimToNull(asString(parseConfig(provider.getConfigJson()).get("operator"))),
                trimToNull(asString(parseConfig(provider.getConfigJson()).get("username"))),
                trimToNull(asString(parseConfig(provider.getConfigJson()).get("user")))
            );
            String password = trimToNull(asString(parseConfig(provider.getConfigJson()).get("password")));
            if (serviceName == null) {
                return ProviderCapability.uploadOnlyUnsupported("又拍云存储缺少服务名配置");
            }
            if (operator == null || password == null) {
                return ProviderCapability.uploadOnlyUnsupported("又拍云存储缺少 operator/password 配置");
            }
            Path scopedRoot = resolveRemoteBase(provider);
            if (systemConfigService.isMultiUserEnabled() && user != null) {
                scopedRoot = scopedRoot.resolve(String.valueOf(user.getId())).normalize();
            }
            boolean previewReady = firstNonBlank(
                trimToNull(asString(parseConfig(provider.getConfigJson()).get("publicBaseUrl"))),
                trimToNull(asString(parseConfig(provider.getConfigJson()).get("cdnDomain")))
            ) != null;
            String supportMessage = previewReady
                ? "又拍云浏览/管理/上传/扫描/预览已接通"
                : "又拍云浏览/管理/上传/扫描已接通，预览需额外配置 publicBaseUrl 或 cdnDomain";
            return ProviderCapability.partialSupportedWithPreview(
                resolveRemoteBase(provider),
                scopedRoot,
                true,
                true,
                previewReady,
                supportMessage,
                supportMessage
            );
        }

        if (provider.getType() == StorageType.DROPBOX) {
            String accessToken = firstNonBlank(
                trimToNull(asString(parseConfig(provider.getConfigJson()).get("accessToken"))),
                trimToNull(asString(parseConfig(provider.getConfigJson()).get("token")))
            );
            if (accessToken == null) {
                return ProviderCapability.uploadOnlyUnsupported("Dropbox 存储缺少 accessToken 配置");
            }
            Path scopedRoot = resolveRemoteBase(provider);
            if (systemConfigService.isMultiUserEnabled() && user != null) {
                scopedRoot = scopedRoot.resolve(String.valueOf(user.getId())).normalize();
            }
            return ProviderCapability.partialSupportedWithPreview(
                resolveRemoteBase(provider),
                scopedRoot,
                true,
                true,
                true,
                "Dropbox 浏览/管理/上传/扫描/预览已接通",
                "Dropbox 浏览/管理/上传/扫描/预览已接通"
            );
        }

        if (provider.getType() == StorageType.ONEDRIVE) {
            Map<String, Object> config = parseConfig(provider.getConfigJson());
            String accessToken = firstNonBlank(
                trimToNull(asString(config.get("accessToken"))),
                trimToNull(asString(config.get("token")))
            );
            String tenantId = trimToNull(asString(config.get("tenantId")));
            String clientId = trimToNull(asString(config.get("clientId")));
            String clientSecret = trimToNull(asString(config.get("clientSecret")));
            if (accessToken == null && (tenantId == null || clientId == null || clientSecret == null)) {
                return ProviderCapability.uploadOnlyUnsupported("OneDrive 存储缺少 accessToken 或 tenantId/clientId/clientSecret 配置");
            }
            Path scopedRoot = resolveRemoteBase(provider);
            if (systemConfigService.isMultiUserEnabled() && user != null) {
                scopedRoot = scopedRoot.resolve(String.valueOf(user.getId())).normalize();
            }
            return ProviderCapability.partialSupportedWithPreview(
                resolveRemoteBase(provider),
                scopedRoot,
                true,
                true,
                true,
                "OneDrive 浏览/管理/上传/扫描/预览已接通",
                "OneDrive 浏览/管理/上传/扫描/预览已接通"
            );
        }

        return ProviderCapability.unsupported("TODO: 暂不支持的存储类型");
    }

    private Path resolveLocalBrowserScopedRoot(Path providerBase, UserAccount user) {
        Path normalizedBase = providerBase == null ? null : providerBase.toAbsolutePath().normalize();
        if (normalizedBase == null || user == null) {
            return normalizedBase;
        }
        if (systemConfigService.isMultiUserEnabled()) {
            return normalizedBase.resolve(String.valueOf(user.getId())).normalize();
        }

        Path ownedRoot = normalizedBase.resolve(String.valueOf(user.getId())).normalize();
        if (Files.isDirectory(ownedRoot)) {
            return ownedRoot;
        }
        return normalizedBase;
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
        return Paths.get(base).normalize();
    }

    private Map<String, Object> parseConfig(String configJson) {
        String raw = trimToNull(configJson);
        if (raw == null) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析存储提供者配置失败，将视为未配置: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private boolean isS3CompatibleType(StorageType type) {
        return type == StorageType.S3_COMPATIBLE
            || type == StorageType.MINIO
            || type == StorageType.OSS
            || type == StorageType.R2
            || type == StorageType.GCS
            || type == StorageType.OBS
            || type == StorageType.TOS
            || type == StorageType.BOS
            || type == StorageType.UCLOUD_US3
            || type == StorageType.JD_JSS
            || type == StorageType.WASABI
            || type == StorageType.QINIU_KODO
            || type == StorageType.B2;
    }

    private String storageTypeLabel(StorageType type) {
        if (type == null) {
            return "对象存储";
        }
        switch (type) {
            case MINIO:
                return "MinIO";
            case OSS:
                return "阿里云 OSS";
            case R2:
                return "Cloudflare R2";
            case GCS:
                return "Google Cloud Storage";
            case UCLOUD_US3:
                return "UCloud US3";
            case JD_JSS:
                return "京东云 JSS";
            case OBS:
                return "华为云 OBS";
            case TOS:
                return "火山引擎 TOS";
            case BOS:
                return "百度云 BOS";
            case WASABI:
                return "Wasabi";
            case QINIU_KODO:
                return "七牛云 Kodo";
            case B2:
                return "Backblaze B2";
            case UPYUN:
                return "又拍云";
            case DROPBOX:
                return "Dropbox";
            case ONEDRIVE:
                return "OneDrive";
            case S3_COMPATIBLE:
            default:
                return "S3 兼容对象存储";
        }
    }

    private String defaultRegionFor(StorageType type) {
        if (type == StorageType.R2) {
            return "auto";
        }
        if (type == StorageType.OSS) {
            return "cn-hangzhou";
        }
        if (type == StorageType.GCS) {
            return "auto";
        }
        return "us-east-1";
    }

    @Getter
    public static class BrowserStorageContext {
        private final StorageProvider provider;
        private final Path providerBasePath;
        private final Path scopedRoot;
        private final List<BrowserProviderOption> availableProviders;

        public BrowserStorageContext(StorageProvider provider,
                                     Path providerBasePath,
                                     Path scopedRoot,
                                     List<BrowserProviderOption> availableProviders) {
            this.provider = provider;
            this.providerBasePath = providerBasePath;
            this.scopedRoot = scopedRoot;
            this.availableProviders = availableProviders;
        }

        public LinkedHashMap<String, Object> toResponse() {
            LinkedHashMap<String, Object> resp = new LinkedHashMap<>();
            resp.put("basePath", scopedRoot == null ? null : "/");
            resp.put("storageProviderId", provider.getId());
            resp.put("storageProviderName", provider.getName());
            resp.put("storageProviderType", provider.getType().name());
            resp.put("storageProviderBaseDirectory", provider.getBaseDirectory());
            resp.put("availableStorageProviders", availableProviders);
            return resp;
        }
    }

    @Getter
    public static class BrowserProviderOption {
        private final Long id;
        private final String name;
        private final StorageType type;
        private final boolean enabled;
        private final String baseDirectory;
        private final boolean browserSupported;
        private final boolean uploadSupported;
        private final boolean scanSupported;
        private final boolean previewSupported;
        private final String supportMessage;
        private final String scopedBasePath;

        public BrowserProviderOption(Long id,
                                     String name,
                                     StorageType type,
                                     boolean enabled,
                                     String baseDirectory,
                                     boolean browserSupported,
                                     boolean uploadSupported,
                                     boolean scanSupported,
                                     boolean previewSupported,
                                     String supportMessage,
                                     String scopedBasePath) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.enabled = enabled;
            this.baseDirectory = baseDirectory;
            this.browserSupported = browserSupported;
            this.uploadSupported = uploadSupported;
            this.scanSupported = scanSupported;
            this.previewSupported = previewSupported;
            this.supportMessage = supportMessage;
            this.scopedBasePath = scopedBasePath;
        }
    }

    private static class ProviderCapability {
        private final boolean browserSupported;
        private final String browserSupportMessage;
        private final boolean uploadSupported;
        private final String uploadSupportMessage;
        private final boolean scanSupported;
        private final boolean previewSupported;
        private final Path providerBasePath;
        private final Path scopedRoot;

        private ProviderCapability(boolean browserSupported,
                                   String browserSupportMessage,
                                   boolean uploadSupported,
                                   String uploadSupportMessage,
                                   boolean scanSupported,
                                   boolean previewSupported,
                                   Path providerBasePath,
                                   Path scopedRoot) {
            this.browserSupported = browserSupported;
            this.browserSupportMessage = browserSupportMessage;
            this.uploadSupported = uploadSupported;
            this.uploadSupportMessage = uploadSupportMessage;
            this.scanSupported = scanSupported;
            this.previewSupported = previewSupported;
            this.providerBasePath = providerBasePath;
            this.scopedRoot = scopedRoot;
        }

        public static ProviderCapability localSupported(Path providerBasePath, Path scopedRoot) {
            return new ProviderCapability(true, null, true, null, true, true, providerBasePath, scopedRoot);
        }

        public static ProviderCapability unsupported(String message) {
            return new ProviderCapability(false, message, false, message, false, false, null, null);
        }

        public static ProviderCapability uploadOnly(Path providerBasePath, String browserMessage, String uploadMessage) {
            return new ProviderCapability(false, browserMessage, true, uploadMessage, false, false, providerBasePath, null);
        }

        public static ProviderCapability partialSupported(Path providerBasePath,
                                                          Path scopedRoot,
                                                          boolean browserSupported,
                                                          boolean scanSupported,
                                                          String browserMessage,
                                                          String uploadMessage) {
            return new ProviderCapability(browserSupported, browserMessage, true, uploadMessage, scanSupported, false, providerBasePath, scopedRoot);
        }

        public static ProviderCapability partialSupportedWithPreview(Path providerBasePath,
                                                                     Path scopedRoot,
                                                                     boolean browserSupported,
                                                                     boolean scanSupported,
                                                                     boolean previewSupported,
                                                                     String browserMessage,
                                                                     String uploadMessage) {
            return new ProviderCapability(browserSupported, browserMessage, true, uploadMessage, scanSupported, previewSupported, providerBasePath, scopedRoot);
        }

        public static ProviderCapability uploadOnlyUnsupported(String uploadMessage) {
            return new ProviderCapability(false, "TODO: 当前文件浏览器暂不支持该存储类型", false, uploadMessage, false, false, null, null);
        }

        public boolean isBrowserSupported() {
            return browserSupported;
        }

        public String getBrowserSupportMessage() {
            return browserSupportMessage;
        }

        public boolean isUploadSupported() {
            return uploadSupported;
        }

        public String getUploadSupportMessage() {
            return uploadSupportMessage;
        }

        public boolean isScanSupported() {
            return scanSupported;
        }

        public boolean isPreviewSupported() {
            return previewSupported;
        }

        public String getPrimarySupportMessage() {
            if (!browserSupported && uploadSupported && uploadSupportMessage != null) {
                return uploadSupportMessage;
            }
            return browserSupportMessage != null ? browserSupportMessage : uploadSupportMessage;
        }

        public Path getProviderBasePath() {
            return providerBasePath;
        }

        public Path getScopedRoot() {
            return scopedRoot;
        }
    }
}
