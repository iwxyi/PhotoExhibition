package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.dto.AtmosphereEffectDTO;
import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.AlbumRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 氛围特效服务
 * 根据相册标签生成相应的氛围特效配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtmosphereEffectsService {

    private final AlbumRepository albumRepository;
    private final ObjectMapper objectMapper;

    // 标签到特效的映射配置
    private static final Map<String, AtmosphereEffectConfig> TAG_EFFECTS = new HashMap<>();

    static {
        // 雪景相关
        TAG_EFFECTS.put("雪天", new AtmosphereEffectConfig("snow", "medium"));
        TAG_EFFECTS.put("雪景", new AtmosphereEffectConfig("snow", "medium"));
        TAG_EFFECTS.put("下雪", new AtmosphereEffectConfig("snow", "high"));
        TAG_EFFECTS.put("雪花", new AtmosphereEffectConfig("snow", "medium"));
        TAG_EFFECTS.put("冬雪", new AtmosphereEffectConfig("snow", "medium"));
        TAG_EFFECTS.put("雪地", new AtmosphereEffectConfig("snow", "low"));

        // 雨天相关 - 雨滴落在图片容器上并流淌
        TAG_EFFECTS.put("雨天", new AtmosphereEffectConfig("rain_on_containers", "medium"));
        TAG_EFFECTS.put("下雨", new AtmosphereEffectConfig("rain_on_containers", "high"));
        TAG_EFFECTS.put("雨", new AtmosphereEffectConfig("rain_on_containers", "medium"));
        TAG_EFFECTS.put("暴雨", new AtmosphereEffectConfig("rain_on_containers", "high"));
        TAG_EFFECTS.put("阵雨", new AtmosphereEffectConfig("rain_on_containers", "low"));
        TAG_EFFECTS.put("rainy", new AtmosphereEffectConfig("rain_on_containers", "medium"));
        TAG_EFFECTS.put("rain", new AtmosphereEffectConfig("rain_on_containers", "medium"));

        // 樱花相关
        TAG_EFFECTS.put("樱花", new AtmosphereEffectConfig("cherry_blossom", "high"));
        TAG_EFFECTS.put("樱花树", new AtmosphereEffectConfig("cherry_blossom", "high"));
        TAG_EFFECTS.put("樱花雨", new AtmosphereEffectConfig("cherry_blossom", "high"));
        TAG_EFFECTS.put("赏樱", new AtmosphereEffectConfig("cherry_blossom", "medium"));
        TAG_EFFECTS.put("樱花道", new AtmosphereEffectConfig("cherry_blossom", "medium"));

        // 生日相关
        TAG_EFFECTS.put("生日", new AtmosphereEffectConfig("birthday", "high"));
        TAG_EFFECTS.put("生日派对", new AtmosphereEffectConfig("birthday", "high"));
        TAG_EFFECTS.put("庆生", new AtmosphereEffectConfig("birthday", "medium"));
        TAG_EFFECTS.put("生日快乐", new AtmosphereEffectConfig("birthday", "high"));

        // 流星相关
        TAG_EFFECTS.put("流星", new AtmosphereEffectConfig("meteor", "high"));
        TAG_EFFECTS.put("流星雨", new AtmosphereEffectConfig("meteor", "high"));
        TAG_EFFECTS.put("星空", new AtmosphereEffectConfig("starry_sky", "medium"));
        TAG_EFFECTS.put("银河", new AtmosphereEffectConfig("starry_sky", "high"));
        TAG_EFFECTS.put("星轨", new AtmosphereEffectConfig("starry_sky", "medium"));
        TAG_EFFECTS.put("夜空", new AtmosphereEffectConfig("starry_sky", "low"));

        // 其他场景
        TAG_EFFECTS.put("烟花", new AtmosphereEffectConfig("fireworks", "high"));
        TAG_EFFECTS.put("烟花秀", new AtmosphereEffectConfig("fireworks", "high"));
        TAG_EFFECTS.put("节日", new AtmosphereEffectConfig("festival", "medium"));
        TAG_EFFECTS.put("婚礼", new AtmosphereEffectConfig("wedding", "medium"));
        TAG_EFFECTS.put("毕业", new AtmosphereEffectConfig("graduation", "medium"));
        TAG_EFFECTS.put("旅行", new AtmosphereEffectConfig("travel", "low"));
        TAG_EFFECTS.put("海边", new AtmosphereEffectConfig("beach", "low"));
        TAG_EFFECTS.put("森林", new AtmosphereEffectConfig("forest", "low"));
        TAG_EFFECTS.put("秋叶", new AtmosphereEffectConfig("autumn_leaves", "medium"));
        TAG_EFFECTS.put("枫叶", new AtmosphereEffectConfig("autumn_leaves", "medium"));
    }

    /**
     * 分析相册特效并更新
     * 注意：如果相册已有手动设置的特效，不会自动覆盖
     */
    @Transactional
    public void analyzeAlbumEffects(Long albumId) {
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在"));

        // 如果相册已有特效配置，检查是否为手动设置的（非自动生成的）
        if (album.getAtmosphereEffects() != null && !album.getAtmosphereEffects().isEmpty()) {
            try {
                List<AtmosphereEffectDTO> existingEffects = objectMapper.readValue(
                    album.getAtmosphereEffects(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AtmosphereEffectDTO.class)
                );

                // 检查是否有手动设置的特效（自动生成的特效config中有source=auto标记）
                boolean hasManualEffects = existingEffects.stream()
                    .anyMatch(effect -> {
                        if (effect.getConfig() == null || !(effect.getConfig() instanceof Map)) {
                            return true; // 无config视为手动
                        }
                        Object source = ((Map<?, ?>) effect.getConfig()).get("source");
                        return !"auto".equals(source);
                    });

                if (hasManualEffects) {
                    log.info("相册 {} 已有手动设置的特效，跳过自动分析", album.getName());
                    return;
                }
            } catch (Exception e) {
                log.warn("解析现有特效配置失败，将重新生成: {}", e.getMessage());
            }
        }

        List<AtmosphereEffectDTO> effects = generateEffectsForAlbum(album);

        try {
            String effectsJson = effects.isEmpty() ? null : objectMapper.writeValueAsString(effects);
            album.setAtmosphereEffects(effectsJson);
            album.setAtmosphereLastUpdated(LocalDateTime.now());
            albumRepository.save(album);

            log.info("完成相册 {} 的特效分析，生成了 {} 个特效", album.getName(), effects.size());
        } catch (Exception e) {
            log.warn("序列化特效配置失败: {}", e.getMessage());
        }
    }

    /**
     * 批量分析所有相册的特效
     */
    @Transactional
    public void analyzeAllAlbumsEffects() {
        List<Album> albums = albumRepository.findAll();
        log.info("开始批量分析 {} 个相册的特效", albums.size());

        int processed = 0;
        for (Album album : albums) {
            try {
                analyzeAlbumEffects(album.getId());
                processed++;
            } catch (Exception e) {
                log.warn("分析相册 {} 特效失败: {}", album.getName(), e.getMessage());
            }
        }

        log.info("批量特效分析完成，处理了 {} 个相册", processed);
    }

    /**
     * 检查相册是否需要重新分析特效
     * 当标签发生变化时需要重新分析
     */
    public boolean needsEffectsUpdate(Long albumId) {
        Album album = albumRepository.findById(albumId).orElse(null);
        if (album == null) {
            return false;
        }

        // 如果从未分析过，需要分析
        if (album.getAtmosphereLastUpdated() == null) {
            return true;
        }

        // 如果已有手动设置的特效，不需要自动更新
        if (album.getAtmosphereEffects() != null && !album.getAtmosphereEffects().isEmpty()) {
            try {
                List<AtmosphereEffectDTO> existingEffects = objectMapper.readValue(
                    album.getAtmosphereEffects(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AtmosphereEffectDTO.class)
                );
                boolean hasManualEffects = existingEffects.stream()
                    .anyMatch(effect -> {
                        if (effect.getConfig() == null || !(effect.getConfig() instanceof Map)) {
                            return true;
                        }
                        Object source = ((Map<?, ?>) effect.getConfig()).get("source");
                        return !"auto".equals(source);
                    });
                if (hasManualEffects) {
                    return false;
                }
            } catch (Exception e) {
                // 解析失败则允许重新分析
            }
        }

        // 如果已有自动生成的特效且分析时间不超过7天，不需要重新分析
        if (album.getAtmosphereLastUpdated() != null) {
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            if (album.getAtmosphereLastUpdated().isAfter(sevenDaysAgo)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 根据相册标签生成特效配置
     */
    private List<AtmosphereEffectDTO> generateEffectsForAlbum(Album album) {
        List<AtmosphereEffectDTO> effects = new ArrayList<>();

        if (album.getTags() == null || album.getTags().isEmpty()) {
            return effects;
        }

        // 收集所有匹配的特效
        Set<String> processedTypes = new HashSet<>();
        Map<String, Integer> effectPriority = new HashMap<>();

        for (Tag tag : album.getTags()) {
            String tagName = tag.getName();
            AtmosphereEffectConfig config = TAG_EFFECTS.get(tagName);

            if (config != null && !processedTypes.contains(config.type)) {
                AtmosphereEffectDTO effect = new AtmosphereEffectDTO(
                    config.type,
                    config.intensity,
                    generateEffectConfig(config.type, config.intensity, null)
                );

                effects.add(effect);
                processedTypes.add(config.type);

                // 限制特效数量，避免过度
                if (effects.size() >= 3) {
                    break;
                }
            }
        }

        // 根据相册名称中的关键词补充特效
        String albumName = album.getName().toLowerCase();
        for (Map.Entry<String, AtmosphereEffectConfig> entry : TAG_EFFECTS.entrySet()) {
            String keyword = entry.getKey().toLowerCase();
            AtmosphereEffectConfig config = entry.getValue();

            if (albumName.contains(keyword) && !processedTypes.contains(config.type)) {
                AtmosphereEffectDTO effect = new AtmosphereEffectDTO(
                    config.type,
                    config.intensity,
                    generateEffectConfig(config.type, config.intensity, null)
                );

                effects.add(effect);
                processedTypes.add(config.type);

                if (effects.size() >= 3) {
                    break;
                }
            }
        }

        return effects;
    }

    /**
     * 生成特效的具体配置参数
     */
    public Object generateEffectConfig(String type, String intensity, String layer) {
        Map<String, Object> config = new HashMap<>();
        boolean isManual = layer != null && !layer.isEmpty();

        switch (type) {
            case "snow":
                config.put("particleCount", intensity.equals("high") ? 150 : intensity.equals("medium") ? 100 : 50);
                config.put("speed", intensity.equals("high") ? 2.0 : intensity.equals("medium") ? 1.5 : 1.0);
                config.put("size", intensity.equals("high") ? 4 : intensity.equals("medium") ? 3 : 2);
                config.put("layer", layer != null ? layer : "above");
                if (!isManual) config.put("source", "auto");
                break;

            case "cherry_blossom":
                config.put("particleCount", intensity.equals("high") ? 80 : intensity.equals("medium") ? 50 : 30);
                config.put("speed", intensity.equals("high") ? 1.2 : intensity.equals("medium") ? 0.8 : 0.5);
                config.put("size", intensity.equals("high") ? 6 : intensity.equals("medium") ? 4 : 3);
                config.put("sway", true);
                config.put("layer", layer != null ? layer : "above");
                if (!isManual) config.put("source", "auto");
                break;

            case "birthday":
                config.put("balloonCount", intensity.equals("high") ? 20 : intensity.equals("medium") ? 15 : 10);
                config.put("confettiCount", intensity.equals("high") ? 100 : intensity.equals("medium") ? 70 : 50);
                config.put("animationDuration", 3000);
                config.put("layer", layer != null ? layer : "above");
                if (!isManual) config.put("source", "auto");
                break;

            case "meteor":
                config.put("meteorCount", intensity.equals("high") ? 8 : intensity.equals("medium") ? 5 : 3);
                config.put("trailLength", intensity.equals("high") ? 200 : intensity.equals("medium") ? 150 : 100);
                config.put("speed", intensity.equals("high") ? 3.0 : intensity.equals("medium") ? 2.0 : 1.5);
                config.put("layer", layer != null ? layer : "above");
                if (!isManual) config.put("source", "auto");
                break;

            case "starry_sky":
                config.put("starCount", intensity.equals("high") ? 200 : intensity.equals("medium") ? 150 : 100);
                config.put("twinkleSpeed", intensity.equals("high") ? 2.0 : intensity.equals("medium") ? 1.5 : 1.0);
                config.put("brightness", intensity.equals("high") ? 0.9 : intensity.equals("medium") ? 0.7 : 0.5);
                config.put("layer", layer != null ? layer : "background");
                if (!isManual) config.put("source", "auto");
                break;

            case "fireworks":
                config.put("fireworkCount", intensity.equals("high") ? 15 : intensity.equals("medium") ? 10 : 5);
                config.put("burstSize", intensity.equals("high") ? 50 : intensity.equals("medium") ? 35 : 20);
                config.put("colors", Arrays.asList("#ff6b6b", "#4ecdc4", "#45b7d1", "#f9ca24", "#f0932b"));
                config.put("layer", layer != null ? layer : "above");
                if (!isManual) config.put("source", "auto");
                break;

            case "autumn_leaves":
                config.put("leafCount", intensity.equals("high") ? 60 : intensity.equals("medium") ? 40 : 25);
                config.put("fallSpeed", intensity.equals("high") ? 1.5 : intensity.equals("medium") ? 1.0 : 0.7);
                config.put("sway", true);
                config.put("colors", Arrays.asList("#d2691e", "#daa520", "#cd853f", "#deb887"));
                config.put("layer", layer != null ? layer : "above");
                if (!isManual) config.put("source", "auto");
                break;

            case "rain_on_containers":
                // 雨滴落在图片容器上并流淌的效果
                config.put("count", intensity.equals("high") ? 8 : intensity.equals("medium") ? 5 : 3);
                config.put("speed", intensity.equals("high") ? 2.0 : intensity.equals("medium") ? 1.5 : 1.0);
                config.put("size", intensity.equals("high") ? 6 : intensity.equals("medium") ? 4 : 3);
                config.put("opacity", intensity.equals("high") ? 9 : intensity.equals("medium") ? 7 : 5);
                config.put("layer", layer != null ? layer : "above");
                if (!isManual) config.put("source", "auto");
                break;

            default:
                config.put("intensity", intensity);
                config.put("layer", layer != null ? layer : "above");
                if (!isManual) config.put("source", "auto");
                break;
        }

        return config;
    }

    /**
     * 添加新的标签特效映射（运行时动态配置）
     */
    public void addTagEffect(String tagName, String effectType, String intensity) {
        TAG_EFFECTS.put(tagName, new AtmosphereEffectConfig(effectType, intensity));
        log.info("添加标签特效映射: {} -> {} ({})", tagName, effectType, intensity);
    }

    /**
     * 获取所有支持的特效类型
     */
    public Set<String> getSupportedEffectTypes() {
        return TAG_EFFECTS.values().stream()
            .map(config -> config.type)
            .collect(Collectors.toSet());
    }

    /**
     * 获取所有标签到特效的映射
     */
    public Map<String, AtmosphereEffectConfig> getTagEffects() {
        return new HashMap<>(TAG_EFFECTS);
    }

    /**
     * 特效配置内部类
     */
    public static class AtmosphereEffectConfig {
        public final String type;
        public final String intensity;

        public AtmosphereEffectConfig(String type, String intensity) {
            this.type = type;
            this.intensity = intensity;
        }
    }
}
