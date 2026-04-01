package com.photoexhibition.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.StorageProvider;
import com.photoexhibition.entity.StorageType;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.repository.StorageProviderRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        String raw = provider != null && provider.getBaseDirectory() != null && !provider.getBaseDirectory().isBlank()
            ? provider.getBaseDirectory()
            : systemConfigService.getLocalStorageRoot();
        return resolveConfiguredPath(raw);
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
        if (user != null && user.getPreferredStorageProviderId() != null) {
            Optional<StorageProvider> preferred = providers.stream()
                .filter(provider -> Objects.equals(provider.getId(), user.getPreferredStorageProviderId()))
                .findFirst();
            if (preferred.isPresent() && evaluateProvider(preferred.get(), user).isBrowserSupported()) {
                return preferred.get();
            }
        }

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
        if (user != null && user.getPreferredStorageProviderId() != null) {
            Optional<StorageProvider> preferred = providers.stream()
                .filter(provider -> Objects.equals(provider.getId(), user.getPreferredStorageProviderId()))
                .findFirst();
            if (preferred.isPresent() && evaluateProvider(preferred.get(), user).isUploadSupported()) {
                return preferred.get();
            }
        }

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
        if (provider.getType() == StorageType.LOCAL
            || provider.getType() == StorageType.SFTP
            || provider.getType() == StorageType.SMB
            || provider.getType() == StorageType.NFS) {
            Path providerBase = resolveAbsoluteBaseDirectory(provider);
            Path scanBase = userPathService.resolvePhotoBasePath();
            Path scopedRoot = systemConfigService.isMultiUserEnabled() && user != null
                ? providerBase.resolve(String.valueOf(user.getId())).normalize()
                : providerBase.normalize();
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
                return ProviderCapability.uploadOnlyUnsupported("COS 存储缺少 bucketName 配置");
            }
            if (region == null) {
                return ProviderCapability.uploadOnlyUnsupported("COS 存储缺少 region 配置");
            }
            if (accessKeyId == null || accessKeySecret == null) {
                return ProviderCapability.uploadOnlyUnsupported("COS 存储缺少密钥配置");
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

        return ProviderCapability.unsupported("暂不支持的存储类型");
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
            return new ProviderCapability(false, "当前文件浏览器暂不支持该存储类型", false, uploadMessage, false, false, null, null);
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
