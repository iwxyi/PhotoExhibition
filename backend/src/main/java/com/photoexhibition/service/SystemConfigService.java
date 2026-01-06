package com.photoexhibition.service;

import com.photoexhibition.entity.SystemConfig;
import com.photoexhibition.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    // 常量定义
    public static final String MAX_ALBUM_DEPTH_KEY = "max_album_depth";
    public static final String MAX_ALBUM_DEPTH_DEFAULT = "1";
    public static final String MAX_ALBUM_DEPTH_DESCRIPTION = "最大相册层级（从base-path下的第三级目录开始计算）";

    public static final String PHOTO_SORT_ORDER_KEY = "photo_sort_order";
    public static final String PHOTO_SORT_ORDER_DEFAULT = "taken_at_desc";
    public static final String PHOTO_SORT_ORDER_DESCRIPTION = "照片排序方式";

    public static final String ALBUM_SORT_ORDER_KEY = "album_sort_order";
    public static final String ALBUM_SORT_ORDER_DEFAULT = "name_asc";
    public static final String ALBUM_SORT_ORDER_DESCRIPTION = "相册排序方式";

    public static final String WALL_SORT_ORDER_KEY = "wall_sort_order";
    public static final String WALL_SORT_ORDER_DEFAULT = "taken_at_desc";
    public static final String WALL_SORT_ORDER_DESCRIPTION = "图墙排序方式";

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
        } else {
            config = new SystemConfig();
            config.setConfigKey(key);
        }

        config.setConfigValue(value);
        config.setDescription(description);
        systemConfigRepository.save(config);

        log.info("配置已更新: {} = {}", key, value);
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
