package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * 相册氛围分析服务
 * 根据相册中所有图片的色调，智能计算背景色、前景色、导航栏颜色
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumAtmosphereAnalysisService {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final ObjectMapper objectMapper;

    /**
     * 分析相册氛围并更新
     */
    @Transactional
    public void analyzeAlbumAtmosphere(Long albumId) {
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在"));

        // 获取前50张照片用于氛围分析（避免处理过多照片）
        List<Photo> photos = photoRepository.findByAlbumId(albumId, PageRequest.of(0, 50)).getContent();

        if (photos.isEmpty()) {
            log.debug("相册 {} 没有照片，跳过氛围分析", album.getName());
            return;
        }

        // 分析颜色
        AtmosphereColors colors = analyzeColors(photos);

        // 更新相册氛围颜色
        album.setDarkBgColor(colors.getDarkBgColor());
        album.setLightBgColor(colors.getLightBgColor());
        album.setDarkAccentColor(colors.getDarkAccentColor());
        album.setLightAccentColor(colors.getLightAccentColor());
        album.setAtmosphereLastUpdated(LocalDateTime.now());

        albumRepository.save(album);

        log.info("完成相册 {} 的氛围分析，深色背景: {}, 浅色背景: {}, 深色点缀: {}, 浅色点缀: {}",
            album.getName(), colors.getDarkBgColor(), colors.getLightBgColor(),
            colors.getDarkAccentColor(), colors.getLightAccentColor());
    }

    /**
     * 批量分析所有相册的氛围（用于初始化或重建）
     */
    @Transactional
    public void analyzeAllAlbumsAtmosphere() {
        List<Album> albums = albumRepository.findAll();
        log.info("开始批量分析 {} 个相册的氛围", albums.size());

        int processed = 0;
        for (Album album : albums) {
            try {
                if (album.getPhotoCount() != null && album.getPhotoCount() > 0) {
                    analyzeAlbumAtmosphere(album.getId());
                    processed++;
                }
            } catch (Exception e) {
                log.warn("分析相册 {} 氛围失败: {}", album.getName(), e.getMessage());
            }
        }

        log.info("批量氛围分析完成，处理了 {} 个相册", processed);
    }

    /**
     * 检查相册是否需要重新分析氛围
     * 当添加/删除图片或图片颜色数据更新时需要重新分析
     */
    public boolean needsAtmosphereUpdate(Long albumId) {
        Album album = albumRepository.findById(albumId).orElse(null);
        if (album == null) {
            return false;
        }

        // 如果从未分析过，需要分析
        if (album.getAtmosphereLastUpdated() == null) {
            return true;
        }

        // 检查是否有图片在氛围分析后被修改
        LocalDateTime lastAtmosphereUpdate = album.getAtmosphereLastUpdated();
        long photosUpdatedAfter = photoRepository.countPhotosUpdatedAfter(albumId, lastAtmosphereUpdate);
        long totalPhotos = photoRepository.countByAlbumId(albumId);

        // 如果有图片更新或照片数量变化，需要重新分析
        return photosUpdatedAfter > 0 || (album.getPhotoCount() != null && album.getPhotoCount().intValue() != totalPhotos);
    }

    /**
     * 分析图片颜色并计算氛围颜色（4种：深色/浅色背景 + 深色/浅色点缀色）
     */
    private AtmosphereColors analyzeColors(List<Photo> photos) {
        List<ColorInfo> allColors = new ArrayList<>();

        // 收集所有图片的主色调和调色板
        for (Photo photo : photos) {
            if (photo.getDominantColor() != null) {
                try {
                    Color dominantColor = hexToColor(photo.getDominantColor());
                    allColors.add(new ColorInfo(dominantColor, 3.0));
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }

            if (photo.getColorPalette() != null) {
                try {
                    List<String> palette = objectMapper.readValue(photo.getColorPalette(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

                    for (int i = 0; i < palette.size(); i++) {
                        Color color = hexToColor(palette.get(i));
                        double weight = 2.0 - (i * 0.3);
                        allColors.add(new ColorInfo(color, weight));
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }
        }

        if (allColors.isEmpty()) {
            return new AtmosphereColors("#1a1a2e", "#f0ebe3", "#7c9cb5", "#4a6b82");
        }

        // 计算加权平均颜色
        Color averageColor = calculateAverageColor(allColors);
        float[] hsb = Color.RGBtoHSB(averageColor.getRed(), averageColor.getGreen(), averageColor.getBlue(), null);
        float hue = hsb[0];
        float sat = hsb[1];

        // === 深色背景 ===
        // 保留色调，适中饱和度，低亮度，营造沉浸感
        float darkBgSat = Math.min(0.5f, sat * 0.8f);
        float darkBgBri = 0.12f + Math.min(0.08f, sat * 0.1f); // 0.12~0.20
        String darkBgColor = colorToHex(Color.getHSBColor(hue, darkBgSat, darkBgBri));

        // === 浅色背景 ===
        // 保留色调，低饱和度，高亮度，柔和不刺眼
        float lightBgSat = Math.min(0.15f, sat * 0.3f);
        float lightBgBri = 0.94f + Math.min(0.04f, (1 - sat) * 0.05f); // 0.94~0.98
        String lightBgColor = colorToHex(Color.getHSBColor(hue, lightBgSat, lightBgBri));

        // === 点缀色（用于标题等） ===
        // 从图片中找到饱和度较高的代表色作为点缀色基础
        Color accentBase = findAccentBase(allColors, hue);
        float[] accentHsb = Color.RGBtoHSB(accentBase.getRed(), accentBase.getGreen(), accentBase.getBlue(), null);
        float accentHue = accentHsb[0];
        float accentSat = accentHsb[1];

        // 深色模式点缀色：明亮、饱和，在深色背景上醒目
        float darkAccSat = Math.max(0.4f, Math.min(0.8f, accentSat * 1.1f));
        float darkAccBri = Math.max(0.65f, Math.min(0.85f, 0.75f));
        String darkAccentColor = colorToHex(Color.getHSBColor(accentHue, darkAccSat, darkAccBri));

        // 浅色模式点缀色：深沉、饱和，在浅色背景上醒目
        float lightAccSat = Math.max(0.45f, Math.min(0.85f, accentSat * 1.2f));
        float lightAccBri = Math.max(0.3f, Math.min(0.5f, 0.4f));
        String lightAccentColor = colorToHex(Color.getHSBColor(accentHue, lightAccSat, lightAccBri));

        return new AtmosphereColors(darkBgColor, lightBgColor, darkAccentColor, lightAccentColor);
    }

    /**
     * 从图片颜色中找到适合做点缀色的基础色
     * 优先选择饱和度较高且与主色调有一定区分的颜色
     */
    private Color findAccentBase(List<ColorInfo> allColors, float mainHue) {
        // 按饱和度排序，取饱和度最高的几个颜色
        List<float[]> candidates = allColors.stream()
            .map(ci -> Color.RGBtoHSB(ci.getColor().getRed(), ci.getColor().getGreen(), ci.getColor().getBlue(), null))
            .filter(hsb -> hsb[1] > 0.2f && hsb[2] > 0.2f) // 过滤掉太灰暗的
            .sorted((a, b) -> Float.compare(b[1] * b[2], a[1] * a[2])) // 按饱和度×亮度排序
            .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            // 没有饱和色，用主色调偏移30度
            return Color.getHSBColor((mainHue + 0.083f) % 1.0f, 0.5f, 0.6f);
        }

        // 优先选择色相与主背景有所区分的（至少15度差异）
        for (float[] c : candidates) {
            float hueDiff = Math.abs(c[0] - mainHue);
            if (hueDiff > 0.5f) hueDiff = 1.0f - hueDiff;
            if (hueDiff > 0.04f) { // ~15度
                return Color.getHSBColor(c[0], c[1], c[2]);
            }
        }

        // 如果都很接近，用饱和度最高的
        float[] best = candidates.get(0);
        return Color.getHSBColor(best[0], best[1], best[2]);
    }

    /**
     * 计算加权平均颜色
     */
    private Color calculateAverageColor(List<ColorInfo> colors) {
        double totalWeight = colors.stream().mapToDouble(ColorInfo::getWeight).sum();
        if (totalWeight == 0) {
            return Color.GRAY;
        }

        double r = 0, g = 0, b = 0;
        for (ColorInfo colorInfo : colors) {
            double weight = colorInfo.getWeight() / totalWeight;
            r += colorInfo.getColor().getRed() * weight;
            g += colorInfo.getColor().getGreen() * weight;
            b += colorInfo.getColor().getBlue() * weight;
        }

        return new Color(
            Math.max(0, Math.min(255, (int) r)),
            Math.max(0, Math.min(255, (int) g)),
            Math.max(0, Math.min(255, (int) b))
        );
    }

    /**
     * 获取颜色的亮度（0-1）
     */
    private double getBrightness(Color color) {
        return (color.getRed() * 0.299 + color.getGreen() * 0.587 + color.getBlue() * 0.114) / 255;
    }

    /**
     * 十六进制颜色转Color对象
     */
    private Color hexToColor(String hex) {
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        return new Color(
            Integer.valueOf(hex.substring(0, 2), 16),
            Integer.valueOf(hex.substring(2, 4), 16),
            Integer.valueOf(hex.substring(4, 6), 16)
        );
    }

    /**
     * Color对象转十六进制字符串
     */
    private String colorToHex(Color color) {
        return String.format("#%02x%02x%02x",
            color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * 颜色信息类
     */
    private static class ColorInfo {
        private final Color color;
        private final double weight;

        public ColorInfo(Color color, double weight) {
            this.color = color;
            this.weight = weight;
        }

        public Color getColor() { return color; }
        public double getWeight() { return weight; }
    }

    /**
     * 氛围颜色结果类（4种颜色）
     */
    private static class AtmosphereColors {
        private final String darkBgColor;
        private final String lightBgColor;
        private final String darkAccentColor;
        private final String lightAccentColor;

        public AtmosphereColors(String darkBgColor, String lightBgColor, String darkAccentColor, String lightAccentColor) {
            this.darkBgColor = darkBgColor;
            this.lightBgColor = lightBgColor;
            this.darkAccentColor = darkAccentColor;
            this.lightAccentColor = lightAccentColor;
        }

        public String getDarkBgColor() { return darkBgColor; }
        public String getLightBgColor() { return lightBgColor; }
        public String getDarkAccentColor() { return darkAccentColor; }
        public String getLightAccentColor() { return lightAccentColor; }
    }
}
