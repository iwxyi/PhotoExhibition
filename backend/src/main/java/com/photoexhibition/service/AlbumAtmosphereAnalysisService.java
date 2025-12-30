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

        // 更新相册氛围信息
        album.setBackgroundColor(colors.getBackgroundColor());
        album.setForegroundColor(colors.getForegroundColor());
        album.setNavbarColor(colors.getNavbarColor());
        album.setAtmosphereLastUpdated(LocalDateTime.now());

        albumRepository.save(album);

        log.info("完成相册 {} 的氛围分析，背景色: {}, 前景色: {}, 导航栏色: {}",
            album.getName(), colors.getBackgroundColor(),
            colors.getForegroundColor(), colors.getNavbarColor());
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
     * 分析图片颜色并计算氛围颜色
     */
    private AtmosphereColors analyzeColors(List<Photo> photos) {
        List<ColorInfo> allColors = new ArrayList<>();

        // 收集所有图片的主色调和调色板
        for (Photo photo : photos) {
            if (photo.getDominantColor() != null) {
                try {
                    Color dominantColor = hexToColor(photo.getDominantColor());
                    allColors.add(new ColorInfo(dominantColor, 3.0)); // 主色调权重更高
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }

            // 添加调色板颜色
            if (photo.getColorPalette() != null) {
                try {
                    List<String> palette = objectMapper.readValue(photo.getColorPalette(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

                    for (int i = 0; i < palette.size(); i++) {
                        Color color = hexToColor(palette.get(i));
                        double weight = 2.0 - (i * 0.3); // 调色板中靠前的颜色权重更高
                        allColors.add(new ColorInfo(color, weight));
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }
        }

        if (allColors.isEmpty()) {
            // 默认颜色方案
            return new AtmosphereColors("#1a1a1a", "#ffffff", "#2d3748");
        }

        // 计算平均颜色
        Color averageColor = calculateAverageColor(allColors);

        // 根据平均颜色确定背景色
        String backgroundColor = determineBackgroundColor(averageColor);

        // 根据背景色确定前景色（确保对比度）
        String foregroundColor = determineForegroundColor(backgroundColor);

        // 导航栏颜色稍微深于背景色
        String navbarColor = determineNavbarColor(backgroundColor);

        return new AtmosphereColors(backgroundColor, foregroundColor, navbarColor);
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
     * 根据平均颜色确定背景色
     */
    private String determineBackgroundColor(Color averageColor) {
        // 计算亮度
        double brightness = (averageColor.getRed() * 0.299 +
                           averageColor.getGreen() * 0.587 +
                           averageColor.getBlue() * 0.114) / 255;

        // 根据图片色调调整背景色
        if (brightness > 0.7) {
            // 明亮图片：使用深色背景
            return "#1a1a1a";
        } else if (brightness > 0.4) {
            // 中等亮度：稍微调整饱和度和亮度
            Color adjusted = adjustColorForBackground(averageColor, 0.3, 0.2);
            return colorToHex(adjusted);
        } else {
            // 暗色图片：使用浅色背景或稍微调亮
            Color adjusted = adjustColorForBackground(averageColor, 0.6, 0.4);
            return colorToHex(adjusted);
        }
    }

    /**
     * 根据背景色确定前景色（确保对比度）
     */
    private String determineForegroundColor(String backgroundColorHex) {
        Color bgColor = hexToColor(backgroundColorHex);
        double bgBrightness = getBrightness(bgColor);

        // 如果背景较暗，使用浅色前景；反之使用深色前景
        if (bgBrightness < 0.5) {
            return "#ffffff"; // 白色文字
        } else {
            return "#1a1a1a"; // 深色文字
        }
    }

    /**
     * 根据背景色确定导航栏颜色
     */
    private String determineNavbarColor(String backgroundColorHex) {
        Color bgColor = hexToColor(backgroundColorHex);
        // 导航栏稍微深一点
        Color navbarColor = adjustColorForNavbar(bgColor);
        return colorToHex(navbarColor);
    }

    /**
     * 调整颜色用于背景
     */
    private Color adjustColorForBackground(Color color, double saturationFactor, double brightnessFactor) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

        // 降低饱和度，调整亮度
        float newSaturation = (float) Math.max(0, Math.min(1, hsb[1] * saturationFactor));
        float newBrightness = (float) Math.max(0, Math.min(1, hsb[2] * brightnessFactor));

        return Color.getHSBColor(hsb[0], newSaturation, newBrightness);
    }

    /**
     * 调整颜色用于导航栏
     */
    private Color adjustColorForNavbar(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

        // 稍微降低亮度
        float newBrightness = (float) Math.max(0, Math.min(1, hsb[2] * 0.8));

        return Color.getHSBColor(hsb[0], hsb[1], newBrightness);
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
     * 氛围颜色结果类
     */
    private static class AtmosphereColors {
        private final String backgroundColor;
        private final String foregroundColor;
        private final String navbarColor;

        public AtmosphereColors(String backgroundColor, String foregroundColor, String navbarColor) {
            this.backgroundColor = backgroundColor;
            this.foregroundColor = foregroundColor;
            this.navbarColor = navbarColor;
        }

        public String getBackgroundColor() { return backgroundColor; }
        public String getForegroundColor() { return foregroundColor; }
        public String getNavbarColor() { return navbarColor; }
    }
}
