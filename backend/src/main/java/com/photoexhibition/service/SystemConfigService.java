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
    public static final String MAX_ALBUM_DEPTH_DESCRIPTION = "最大相册层级（从base-path下的第三级目录开始计算）";

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
