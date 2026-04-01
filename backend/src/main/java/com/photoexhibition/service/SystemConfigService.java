package com.photoexhibition.service;

import com.photoexhibition.entity.SystemConfig;
import com.photoexhibition.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // 常量定义
    public static final String MAX_ALBUM_DEPTH_KEY = "max_album_depth";
    public static final String MAX_ALBUM_DEPTH_DEFAULT = "1";
    public static final String MAX_ALBUM_DEPTH_DESCRIPTION = "最大相册层级（从当前用户图片根目录 / 分类 / 顶级相册 之后开始计算）";

    public static final String PHOTO_SORT_ORDER_KEY = "photo_sort_order";
    public static final String PHOTO_SORT_ORDER_DEFAULT = "taken_at_asc";
    public static final String PHOTO_SORT_ORDER_DESCRIPTION = "照片排序方式";

    public static final String ALBUM_SORT_ORDER_KEY = "album_sort_order";
    public static final String ALBUM_SORT_ORDER_DEFAULT = "latest_photo_taken_desc";
    public static final String ALBUM_SORT_ORDER_DESCRIPTION = "相册排序方式";

    public static final String WALL_SORT_ORDER_KEY = "wall_sort_order";
    public static final String WALL_SORT_ORDER_DEFAULT = "taken_at_desc";
    public static final String WALL_SORT_ORDER_DESCRIPTION = "图墙排序方式";

    public static final String MIN_CLUSTER_FACE_COUNT_KEY = "min_cluster_face_count";
    public static final String MIN_CLUSTER_FACE_COUNT_DEFAULT = "2";
    public static final String MIN_CLUSTER_FACE_COUNT_DESCRIPTION = "聚类显示最小人脸数量（少于此数量的聚类不显示）";

    public static final String GLOBAL_DOWNLOAD_ALLOWED_KEY = "global_download_allowed";
    public static final String GLOBAL_DOWNLOAD_ALLOWED_DEFAULT = "false";
    public static final String GLOBAL_DOWNLOAD_ALLOWED_DESCRIPTION = "全局是否允许下载图片";

    public static final String ALBUM_CATEGORY_SORT_ORDER_KEY = "album_category_sort_order";
    public static final String ALBUM_CATEGORY_SORT_ORDER_DEFAULT = "";
    public static final String ALBUM_CATEGORY_SORT_ORDER_DESCRIPTION = "相册分类排序方式（用逗号分隔，未排序的分类排在后面）";

    public static final String TAG_IGNORE_LIST_KEY = "tag_ignore_list";
    public static final String TAG_IGNORE_LIST_DEFAULT = "";
    public static final String TAG_IGNORE_LIST_DESCRIPTION = "标签忽略列表（用空格或逗号分隔，这些标签在筛选功能中不显示）";

    public static final String ATMOSPHERE_ENABLED_KEY = "atmosphere_enabled";
    public static final String ATMOSPHERE_ENABLED_DEFAULT = "false";
    public static final String ATMOSPHERE_ENABLED_DESCRIPTION = "全局氛围特效开关";

    public static final String FACE_CLUSTER_THRESHOLD_KEY = "face_cluster_threshold";
    public static final String FACE_CLUSTER_THRESHOLD_DEFAULT = "0.7";
    public static final String FACE_CLUSTER_THRESHOLD_DESCRIPTION = "人脸聚类阈值（用于人脸识别和分组）";

    public static final String AI_SEARCH_ENABLED_KEY = "ai_search_enabled";
    public static final String AI_SEARCH_ENABLED_DEFAULT = "false";
    public static final String AI_SEARCH_ENABLED_DESCRIPTION = "AI智能搜索开关";

    public static final String AI_SEARCH_API_URL_KEY = "ai_search_api_url";
    public static final String AI_SEARCH_API_URL_DEFAULT = "";
    public static final String AI_SEARCH_API_URL_DESCRIPTION = "AI搜索API地址(OpenAI兼容格式)";

    public static final String AI_SEARCH_API_KEY_KEY = "ai_search_api_key";
    public static final String AI_SEARCH_API_KEY_DEFAULT = "";
    public static final String AI_SEARCH_API_KEY_DESCRIPTION = "AI搜索API密钥";

    public static final String AI_SEARCH_MODEL_KEY = "ai_search_model";
    public static final String AI_SEARCH_MODEL_DEFAULT = "gpt-4o";
    public static final String AI_SEARCH_MODEL_DESCRIPTION = "AI搜索使用的模型名称";

    public static final String MULTI_USER_ENABLED_KEY = "multi_user_enabled";
    public static final String MULTI_USER_ENABLED_DEFAULT = "false";
    public static final String MULTI_USER_ENABLED_DESCRIPTION = "是否开启多用户模式";

    public static final String SCAN_SCHEDULER_ENABLED_KEY = "scan_scheduler_enabled";
    public static final String SCAN_SCHEDULER_ENABLED_DEFAULT = "false";
    public static final String SCAN_SCHEDULER_ENABLED_DESCRIPTION = "是否开启定时扫描";

    public static final String SCAN_WORKER_COUNT_KEY = "scan_worker_count";
    public static final String SCAN_WORKER_COUNT_DEFAULT = "1";
    public static final String SCAN_WORKER_COUNT_DESCRIPTION = "扫描任务工作线程数（建议 1~2）";

    public static final String DEFAULT_USER_QUOTA_BYTES_KEY = "default_user_quota_bytes";
    public static final String DEFAULT_USER_QUOTA_BYTES_DEFAULT = String.valueOf(3L * 1024 * 1024 * 1024);
    public static final String DEFAULT_USER_QUOTA_BYTES_DESCRIPTION = "新用户默认原图空间限额（建议按 GB 输入与展示，底层按字节存储）";

    public static final String DEFAULT_VIP_EXTRA_QUOTA_BYTES_KEY = "default_vip_extra_quota_bytes";
    public static final String DEFAULT_VIP_EXTRA_QUOTA_BYTES_DEFAULT = "0";
    public static final String DEFAULT_VIP_EXTRA_QUOTA_BYTES_DESCRIPTION = "默认 VIP 额外空间限额（建议按 GB 输入与展示，底层按字节存储）";

    public static final String LOCAL_STORAGE_ROOT_KEY = "local_storage_root";
    public static final String LOCAL_STORAGE_ROOT_DEFAULT = "./data/photos";
    public static final String LOCAL_STORAGE_ROOT_DESCRIPTION = "本地图片存储总根目录（多用户模式下实际为该目录/{userId}/...）";

    public static final String USER_DATA_ROOT_KEY = "user_data_root";
    public static final String USER_DATA_ROOT_DEFAULT = "./data/users";
    public static final String USER_DATA_ROOT_DESCRIPTION = "用户资料存储根目录";

    public static final String FORCE_BIND_PHONE_KEY = "force_bind_phone";
    public static final String FORCE_BIND_PHONE_DEFAULT = "false";
    public static final String FORCE_BIND_PHONE_DESCRIPTION = "是否强制用户注册时绑定手机号";

    public static final String AUTO_RENEW_SCHEDULER_ENABLED_KEY = "auto_renew_scheduler_enabled";
    public static final String AUTO_RENEW_SCHEDULER_ENABLED_DEFAULT = "false";
    public static final String AUTO_RENEW_SCHEDULER_ENABLED_DESCRIPTION = "是否开启自动续费建单定时任务";

    public static final String SUPER_ADMIN_TABLE_PREFERENCES_KEY = "super_admin_table_preferences";
    public static final String SUPER_ADMIN_TABLE_PREFERENCES_DEFAULT = "{}";
    public static final String SUPER_ADMIN_TABLE_PREFERENCES_DESCRIPTION = "超级管理员表格偏好配置(JSON)";

    public static final String LEGACY_MIGRATION_COMPLETED_KEY = "legacy_migration_completed";
    public static final String LEGACY_MIGRATION_COMPLETED_DEFAULT = "false";
    public static final String LEGACY_MIGRATION_COMPLETED_DESCRIPTION = "旧数据迁移是否已在启动阶段完成，用于避免每次重启重复执行全量迁移";

    // 排序方式常量
    public static final String SORT_BY_TAKEN_AT_DESC = "taken_at_desc";
    public static final String SORT_BY_TAKEN_AT_ASC = "taken_at_asc";
    public static final String SORT_BY_FILENAME_DESC = "filename_desc";
    public static final String SORT_BY_FILENAME_ASC = "filename_asc";
    public static final String SORT_BY_CREATED_AT_DESC = "created_at_desc";
    public static final String SORT_BY_CREATED_AT_ASC = "created_at_asc";
    public static final String SORT_BY_NAME_DESC = "name_desc";
    public static final String SORT_BY_NAME_ASC = "name_asc";
    public static final String SORT_BY_LATEST_PHOTO_TAKEN_DESC = "latest_photo_taken_desc";
    public static final String SORT_BY_LATEST_PHOTO_TAKEN_ASC = "latest_photo_taken_asc";
    public static final String SORT_BY_ALBUM_NAME_DATE_DESC = "album_name_date_desc";
    public static final String SORT_BY_ALBUM_NAME_DATE_ASC = "album_name_date_asc";

    /**
     * 获取配置值，如果不存在则返回默认值
     */
    public String getConfigValue(String key, String defaultValue) {
        // 清除一级缓存，确保读取最新数据
        entityManager.clear();
        Optional<SystemConfig> config = systemConfigRepository.findByConfigKey(key);
        return config.map(SystemConfig::getConfigValue).orElse(defaultValue);
    }

    /**
     * 获取配置值，如果不存在则创建默认配置并返回默认值
     */
    @Transactional
    public String getConfigValueWithDefault(String key, String defaultValue, String description) {
        Optional<SystemConfig> config = systemConfigRepository.findByConfigKey(key);
        if (config.isPresent()) {
            return config.get().getConfigValue();
        }

        // 创建默认配置
        SystemConfig newConfig = new SystemConfig();
        newConfig.setConfigKey(key);
        newConfig.setConfigValue(defaultValue);
        newConfig.setDescription(description);
        systemConfigRepository.save(newConfig);

        return defaultValue;
    }

    /**
     * 设置配置值
     */
    @Transactional
    public void setConfigValue(String key, String value, String description) {
        Optional<SystemConfig> existing = systemConfigRepository.findByConfigKey(key);
        SystemConfig config;

        if (existing.isPresent()) {
            config = existing.get();
            log.info("找到现有配置: key={}, currentValue={}", key, config.getConfigValue());
        } else {
            config = new SystemConfig();
            config.setConfigKey(key);
            log.info("创建新配置: key={}", key);
        }

        config.setConfigValue(value);
        config.setDescription(description);
        systemConfigRepository.save(config);
        log.info("配置已保存: key={}, value={}", key, value);
    }

    public String getSuperAdminTablePreferences() {
        return getConfigValueWithDefault(
            SUPER_ADMIN_TABLE_PREFERENCES_KEY,
            SUPER_ADMIN_TABLE_PREFERENCES_DEFAULT,
            SUPER_ADMIN_TABLE_PREFERENCES_DESCRIPTION
        );
    }

    @Transactional
    public void setSuperAdminTablePreferences(String json) {
        setConfigValue(
            SUPER_ADMIN_TABLE_PREFERENCES_KEY,
            json == null || json.isBlank() ? SUPER_ADMIN_TABLE_PREFERENCES_DEFAULT : json,
            SUPER_ADMIN_TABLE_PREFERENCES_DESCRIPTION
        );
    }

    /**
     * 获取最大相册层级
     */
    public int getMaxAlbumDepth() {
        String value = getConfigValueWithDefault(
            MAX_ALBUM_DEPTH_KEY,
            MAX_ALBUM_DEPTH_DEFAULT,
            MAX_ALBUM_DEPTH_DESCRIPTION
        );
        try {
            int depth = Integer.parseInt(value);
            return Math.max(0, depth); // 确保非负数
        } catch (NumberFormatException e) {
            log.warn("无效的最大相册层级配置: {}, 使用默认值 {}", value, MAX_ALBUM_DEPTH_DEFAULT);
            return Integer.parseInt(MAX_ALBUM_DEPTH_DEFAULT);
        }
    }

    /**
     * 设置最大相册层级
     */
    @Transactional
    public void setMaxAlbumDepth(int depth) {
        if (depth < 0) {
            throw new IllegalArgumentException("最大相册层级不能为负数");
        }
        setConfigValue(MAX_ALBUM_DEPTH_KEY, String.valueOf(depth), MAX_ALBUM_DEPTH_DESCRIPTION);
    }

    /**
     * 获取照片排序方式
     */
    public String getPhotoSortOrder() {
        return getConfigValueWithDefault(
            PHOTO_SORT_ORDER_KEY,
            PHOTO_SORT_ORDER_DEFAULT,
            PHOTO_SORT_ORDER_DESCRIPTION
        );
    }

    /**
     * 设置照片排序方式
     */
    @Transactional
    public void setPhotoSortOrder(String sortOrder) {
        // 验证排序方式
        if (!isValidSortOrder(sortOrder)) {
            throw new IllegalArgumentException("无效的排序方式: " + sortOrder);
        }
        setConfigValue(PHOTO_SORT_ORDER_KEY, sortOrder, PHOTO_SORT_ORDER_DESCRIPTION);
    }

    /**
     * 获取相册排序方式
     */
    public String getAlbumSortOrder() {
        return getConfigValueWithDefault(
            ALBUM_SORT_ORDER_KEY,
            ALBUM_SORT_ORDER_DEFAULT,
            ALBUM_SORT_ORDER_DESCRIPTION
        );
    }

    /**
     * 设置相册排序方式
     */
    @Transactional
    public void setAlbumSortOrder(String sortOrder) {
        // 验证排序方式
        if (!isValidSortOrder(sortOrder)) {
            throw new IllegalArgumentException("无效的排序方式: " + sortOrder);
        }
        setConfigValue(ALBUM_SORT_ORDER_KEY, sortOrder, ALBUM_SORT_ORDER_DESCRIPTION);
    }

    /**
     * 获取图墙排序方式
     */
    public String getWallSortOrder() {
        return getConfigValueWithDefault(
            WALL_SORT_ORDER_KEY,
            WALL_SORT_ORDER_DEFAULT,
            WALL_SORT_ORDER_DESCRIPTION
        );
    }

    /**
     * 获取聚类显示最小人脸数量
     */
    public int getMinClusterFaceCount() {
        String value = getConfigValueWithDefault(
            MIN_CLUSTER_FACE_COUNT_KEY,
            MIN_CLUSTER_FACE_COUNT_DEFAULT,
            MIN_CLUSTER_FACE_COUNT_DESCRIPTION
        );
        try {
            int count = Integer.parseInt(value);
            return Math.max(1, count); // 最小为1
        } catch (NumberFormatException e) {
            log.warn("聚类最小人脸数量配置无效: {}, 使用默认值 {}", value, MIN_CLUSTER_FACE_COUNT_DEFAULT);
            return Integer.parseInt(MIN_CLUSTER_FACE_COUNT_DEFAULT);
        }
    }

    /**
     * 设置聚类显示最小人脸数量
     */
    @Transactional
    public void setMinClusterFaceCount(int minCount) {
        if (minCount < 1) {
            throw new IllegalArgumentException("聚类最小人脸数量不能小于1: " + minCount);
        }
        setConfigValue(MIN_CLUSTER_FACE_COUNT_KEY, String.valueOf(minCount), MIN_CLUSTER_FACE_COUNT_DESCRIPTION);
    }

    /**
     * 获取全局下载权限
     */
    public boolean isGlobalDownloadAllowed() {
        String value = getConfigValueWithDefault(
            GLOBAL_DOWNLOAD_ALLOWED_KEY,
            GLOBAL_DOWNLOAD_ALLOWED_DEFAULT,
            GLOBAL_DOWNLOAD_ALLOWED_DESCRIPTION
        );
        return Boolean.parseBoolean(value);
    }

    /**
     * 设置全局下载权限
     */
    @Transactional
    public void setGlobalDownloadAllowed(boolean allowed) {
        setConfigValue(GLOBAL_DOWNLOAD_ALLOWED_KEY, String.valueOf(allowed), GLOBAL_DOWNLOAD_ALLOWED_DESCRIPTION);
    }

    public boolean isAtmosphereEnabled() {
        String value = getConfigValueWithDefault(
            ATMOSPHERE_ENABLED_KEY,
            ATMOSPHERE_ENABLED_DEFAULT,
            ATMOSPHERE_ENABLED_DESCRIPTION
        );
        return "true".equalsIgnoreCase(value);
    }

    public void setAtmosphereEnabled(boolean enabled) {
        setConfigValue(ATMOSPHERE_ENABLED_KEY, String.valueOf(enabled), ATMOSPHERE_ENABLED_DESCRIPTION);
    }

    public boolean isMultiUserEnabled() {
        String value = getConfigValueWithDefault(
            MULTI_USER_ENABLED_KEY,
            MULTI_USER_ENABLED_DEFAULT,
            MULTI_USER_ENABLED_DESCRIPTION
        );
        return "true".equalsIgnoreCase(value);
    }

    @Transactional
    public void setMultiUserEnabled(boolean enabled) {
        setConfigValue(MULTI_USER_ENABLED_KEY, String.valueOf(enabled), MULTI_USER_ENABLED_DESCRIPTION);
    }

    public boolean isScanSchedulerEnabled() {
        String value = getConfigValueWithDefault(
            SCAN_SCHEDULER_ENABLED_KEY,
            SCAN_SCHEDULER_ENABLED_DEFAULT,
            SCAN_SCHEDULER_ENABLED_DESCRIPTION
        );
        return "true".equalsIgnoreCase(value);
    }

    @Transactional
    public void setScanSchedulerEnabled(boolean enabled) {
        setConfigValue(SCAN_SCHEDULER_ENABLED_KEY, String.valueOf(enabled), SCAN_SCHEDULER_ENABLED_DESCRIPTION);
    }

    public long getDefaultUserQuotaBytes() {
        String value = getConfigValueWithDefault(
            DEFAULT_USER_QUOTA_BYTES_KEY,
            DEFAULT_USER_QUOTA_BYTES_DEFAULT,
            DEFAULT_USER_QUOTA_BYTES_DESCRIPTION
        );
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException e) {
            log.warn("无效的默认用户空间限额配置: {}, 使用默认值 {}", value, DEFAULT_USER_QUOTA_BYTES_DEFAULT);
            return Long.parseLong(DEFAULT_USER_QUOTA_BYTES_DEFAULT);
        }
    }

    public int getScanWorkerCount() {
        String value = getConfigValueWithDefault(
            SCAN_WORKER_COUNT_KEY,
            SCAN_WORKER_COUNT_DEFAULT,
            SCAN_WORKER_COUNT_DESCRIPTION
        );
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(1, Math.min(2, parsed));
        } catch (NumberFormatException e) {
            log.warn("无效的扫描工作线程数配置: {}, 使用默认值 {}", value, SCAN_WORKER_COUNT_DEFAULT);
            return Integer.parseInt(SCAN_WORKER_COUNT_DEFAULT);
        }
    }

    @Transactional
    public void setScanWorkerCount(int count) {
        if (count < 1 || count > 2) {
            throw new IllegalArgumentException("扫描工作线程数仅支持 1~2");
        }
        setConfigValue(SCAN_WORKER_COUNT_KEY, String.valueOf(count), SCAN_WORKER_COUNT_DESCRIPTION);
    }

    @Transactional
    public void setDefaultUserQuotaBytes(long quotaBytes) {
        if (quotaBytes < 0) {
            throw new IllegalArgumentException("默认用户空间限额不能为负数");
        }
        setConfigValue(DEFAULT_USER_QUOTA_BYTES_KEY, String.valueOf(quotaBytes), DEFAULT_USER_QUOTA_BYTES_DESCRIPTION);
    }

    public boolean isLegacyMigrationCompleted() {
        String value = getConfigValueWithDefault(
            LEGACY_MIGRATION_COMPLETED_KEY,
            LEGACY_MIGRATION_COMPLETED_DEFAULT,
            LEGACY_MIGRATION_COMPLETED_DESCRIPTION
        );
        return "true".equalsIgnoreCase(value);
    }

    @Transactional
    public void setLegacyMigrationCompleted(boolean completed) {
        setConfigValue(LEGACY_MIGRATION_COMPLETED_KEY, String.valueOf(completed), LEGACY_MIGRATION_COMPLETED_DESCRIPTION);
    }

    public long getDefaultVipExtraQuotaBytes() {
        String value = getConfigValueWithDefault(
            DEFAULT_VIP_EXTRA_QUOTA_BYTES_KEY,
            DEFAULT_VIP_EXTRA_QUOTA_BYTES_DEFAULT,
            DEFAULT_VIP_EXTRA_QUOTA_BYTES_DESCRIPTION
        );
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException e) {
            log.warn("无效的默认 VIP 额外空间限额配置: {}, 使用默认值 {}", value, DEFAULT_VIP_EXTRA_QUOTA_BYTES_DEFAULT);
            return Long.parseLong(DEFAULT_VIP_EXTRA_QUOTA_BYTES_DEFAULT);
        }
    }

    @Transactional
    public void setDefaultVipExtraQuotaBytes(long quotaBytes) {
        if (quotaBytes < 0) {
            throw new IllegalArgumentException("默认 VIP 额外空间限额不能为负数");
        }
        setConfigValue(DEFAULT_VIP_EXTRA_QUOTA_BYTES_KEY, String.valueOf(quotaBytes), DEFAULT_VIP_EXTRA_QUOTA_BYTES_DESCRIPTION);
    }

    public String getLocalStorageRoot() {
        return getConfigValueWithDefault(
            LOCAL_STORAGE_ROOT_KEY,
            LOCAL_STORAGE_ROOT_DEFAULT,
            LOCAL_STORAGE_ROOT_DESCRIPTION
        );
    }

    @Transactional
    public void setLocalStorageRoot(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("本地图片存储根目录不能为空");
        }
        setConfigValue(LOCAL_STORAGE_ROOT_KEY, path.trim(), LOCAL_STORAGE_ROOT_DESCRIPTION);
    }

    public String getUserDataRoot() {
        return getConfigValueWithDefault(
            USER_DATA_ROOT_KEY,
            USER_DATA_ROOT_DEFAULT,
            USER_DATA_ROOT_DESCRIPTION
        );
    }

    @Transactional
    public void setUserDataRoot(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("用户资料存储根目录不能为空");
        }
        setConfigValue(USER_DATA_ROOT_KEY, path.trim(), USER_DATA_ROOT_DESCRIPTION);
    }

    public boolean isForceBindPhone() {
        String value = getConfigValueWithDefault(
            FORCE_BIND_PHONE_KEY,
            FORCE_BIND_PHONE_DEFAULT,
            FORCE_BIND_PHONE_DESCRIPTION
        );
        return "true".equalsIgnoreCase(value);
    }

    @Transactional
    public void setForceBindPhone(boolean enabled) {
        setConfigValue(FORCE_BIND_PHONE_KEY, String.valueOf(enabled), FORCE_BIND_PHONE_DESCRIPTION);
    }

    public boolean isAutoRenewSchedulerEnabled() {
        String value = getConfigValueWithDefault(
            AUTO_RENEW_SCHEDULER_ENABLED_KEY,
            AUTO_RENEW_SCHEDULER_ENABLED_DEFAULT,
            AUTO_RENEW_SCHEDULER_ENABLED_DESCRIPTION
        );
        return "true".equalsIgnoreCase(value);
    }

    @Transactional
    public void setAutoRenewSchedulerEnabled(boolean enabled) {
        setConfigValue(AUTO_RENEW_SCHEDULER_ENABLED_KEY, String.valueOf(enabled), AUTO_RENEW_SCHEDULER_ENABLED_DESCRIPTION);
    }

    /**
     * 获取人脸聚类阈值
     */
    public double getFaceClusterThreshold() {
        String value = getConfigValueWithDefault(
            FACE_CLUSTER_THRESHOLD_KEY,
            FACE_CLUSTER_THRESHOLD_DEFAULT,
            FACE_CLUSTER_THRESHOLD_DESCRIPTION
        );
        try {
            double threshold = Double.parseDouble(value);
            return Math.max(0.1, Math.min(0.9, threshold)); // 限制在 0.1-0.9 范围
        } catch (NumberFormatException e) {
            log.warn("无效的人脸聚类阈值配置: {}, 使用默认值 {}", value, FACE_CLUSTER_THRESHOLD_DEFAULT);
            return Double.parseDouble(FACE_CLUSTER_THRESHOLD_DEFAULT);
        }
    }

    /**
     * 设置人脸聚类阈值
     */
    @Transactional
    public void setFaceClusterThreshold(double threshold) {
        if (threshold < 0.1 || threshold > 0.9) {
            throw new IllegalArgumentException("人脸聚类阈值必须在 0.1-0.9 之间: " + threshold);
        }
        setConfigValue(FACE_CLUSTER_THRESHOLD_KEY, String.valueOf(threshold), FACE_CLUSTER_THRESHOLD_DESCRIPTION);
    }

    /**
     * 获取相册分类排序方式
     */
    public String getAlbumCategorySortOrder() {
        return getConfigValueWithDefault(
            ALBUM_CATEGORY_SORT_ORDER_KEY,
            ALBUM_CATEGORY_SORT_ORDER_DEFAULT,
            ALBUM_CATEGORY_SORT_ORDER_DESCRIPTION
        );
    }

    /**
     * 设置相册分类排序方式
     */
    @Transactional
    public void setAlbumCategorySortOrder(String sortOrder) {
        if (sortOrder == null) {
            sortOrder = "";
        }
        setConfigValue(ALBUM_CATEGORY_SORT_ORDER_KEY, sortOrder.trim(), ALBUM_CATEGORY_SORT_ORDER_DESCRIPTION);
    }

    /**
     * 获取标签忽略列表
     */
    public String getTagIgnoreList() {
        return getConfigValueWithDefault(
            TAG_IGNORE_LIST_KEY,
            TAG_IGNORE_LIST_DEFAULT,
            TAG_IGNORE_LIST_DESCRIPTION
        );
    }

    /**
     * 获取解析后的标签忽略列表（ Set）
     */
    public Set<String> getTagIgnoreListSet() {
        String ignoreList = getTagIgnoreList();
        if (ignoreList == null || ignoreList.trim().isEmpty()) {
            return new HashSet<>();
        }
        // 用空格或逗号分隔
        return Arrays.stream(ignoreList.split("[\\s,]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * 设置标签忽略列表
     */
    @Transactional
    public void setTagIgnoreList(String ignoreList) {
        if (ignoreList == null) {
            ignoreList = "";
        }
        setConfigValue(TAG_IGNORE_LIST_KEY, ignoreList.trim(), TAG_IGNORE_LIST_DESCRIPTION);
    }

    /**
     * 设置图墙排序方式
     */
    @Transactional
    public void setWallSortOrder(String sortOrder) {
        // 验证排序方式
        if (!isValidSortOrder(sortOrder)) {
            throw new IllegalArgumentException("无效的排序方式: " + sortOrder);
        }
        setConfigValue(WALL_SORT_ORDER_KEY, sortOrder, WALL_SORT_ORDER_DESCRIPTION);
    }

    /**
     * 验证排序方式是否有效
     */
    private boolean isValidSortOrder(String sortOrder) {
        return SORT_BY_TAKEN_AT_DESC.equals(sortOrder) ||
               SORT_BY_TAKEN_AT_ASC.equals(sortOrder) ||
               SORT_BY_FILENAME_DESC.equals(sortOrder) ||
               SORT_BY_FILENAME_ASC.equals(sortOrder) ||
               SORT_BY_CREATED_AT_DESC.equals(sortOrder) ||
               SORT_BY_CREATED_AT_ASC.equals(sortOrder) ||
               SORT_BY_NAME_DESC.equals(sortOrder) ||
               SORT_BY_NAME_ASC.equals(sortOrder) ||
               SORT_BY_LATEST_PHOTO_TAKEN_DESC.equals(sortOrder) ||
               SORT_BY_LATEST_PHOTO_TAKEN_ASC.equals(sortOrder) ||
               SORT_BY_ALBUM_NAME_DATE_DESC.equals(sortOrder) ||
               SORT_BY_ALBUM_NAME_DATE_ASC.equals(sortOrder);
    }

    // ===== AI 搜索配置 =====

    public boolean isAiSearchEnabled() {
        String value = getConfigValueWithDefault(
            AI_SEARCH_ENABLED_KEY, AI_SEARCH_ENABLED_DEFAULT, AI_SEARCH_ENABLED_DESCRIPTION
        );
        return "true".equalsIgnoreCase(value);
    }

    @Transactional
    public void setAiSearchEnabled(boolean enabled) {
        setConfigValue(AI_SEARCH_ENABLED_KEY, String.valueOf(enabled), AI_SEARCH_ENABLED_DESCRIPTION);
    }

    public String getAiSearchApiUrl() {
        return getConfigValueWithDefault(
            AI_SEARCH_API_URL_KEY, AI_SEARCH_API_URL_DEFAULT, AI_SEARCH_API_URL_DESCRIPTION
        );
    }

    @Transactional
    public void setAiSearchApiUrl(String url) {
        setConfigValue(AI_SEARCH_API_URL_KEY, url != null ? url.trim() : "", AI_SEARCH_API_URL_DESCRIPTION);
    }

    public String getAiSearchApiKey() {
        return getConfigValueWithDefault(
            AI_SEARCH_API_KEY_KEY, AI_SEARCH_API_KEY_DEFAULT, AI_SEARCH_API_KEY_DESCRIPTION
        );
    }

    @Transactional
    public void setAiSearchApiKey(String key) {
        setConfigValue(AI_SEARCH_API_KEY_KEY, key != null ? key.trim() : "", AI_SEARCH_API_KEY_DESCRIPTION);
    }

    public String getAiSearchModel() {
        return getConfigValueWithDefault(
            AI_SEARCH_MODEL_KEY, AI_SEARCH_MODEL_DEFAULT, AI_SEARCH_MODEL_DESCRIPTION
        );
    }

    @Transactional
    public void setAiSearchModel(String model) {
        setConfigValue(AI_SEARCH_MODEL_KEY, model != null ? model.trim() : AI_SEARCH_MODEL_DEFAULT, AI_SEARCH_MODEL_DESCRIPTION);
    }

    /**
     * 获取所有配置
     */
    public Map<String, String> getAllConfigs() {
        Iterable<SystemConfig> configs = systemConfigRepository.findAll();
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (SystemConfig config : configs) {
            result.put(config.getConfigKey(), config.getConfigValue());
        }
        return result;
    }
}
