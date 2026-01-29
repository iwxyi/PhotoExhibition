package com.photoexhibition.service;

import com.photoexhibition.entity.FilterOption;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.repository.FilterOptionRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FilterOptionService {

    private final FilterOptionRepository filterOptionRepository;
    private final PhotoRepository photoRepository;
    private final TagRepository tagRepository;

    /**
     * 更新所有筛选选项
     */
    @Transactional
    public void updateAllFilterOptions() {
        log.info("开始更新筛选选项...");

        try {
            // 更新相机型号
            updateCameraModels();

            // 更新镜头型号
            updateLensModels();

            // 更新颜色分类
            updateColorCategories();

            // 更新焦距范围
            updateFocalLengthRange();

            // 更新快门速度范围
            updateShutterSpeedRange();

            // 更新光圈范围
            updateApertureRange();

            // 更新ISO范围
            updateIsoRange();

            log.info("筛选选项更新完成");
        } catch (Exception e) {
            log.error("更新筛选选项失败", e);
            throw new RuntimeException("更新筛选选项失败", e);
        }
    }

    /**
     * 获取筛选选项
     */
    public Map<String, Object> getFilterOptions() {
        Map<String, Object> options = new HashMap<>();

        // 获取相机型号（带数量）
        List<Map<String, Object>> cameraModels = filterOptionRepository.findByOptionTypeOrderByOptionKey("camera_models")
                .stream()
                .map(option -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", option.getOptionKey());
                    item.put("count", option.getPhotoCount());
                    return item;
                })
                .collect(Collectors.toList());
        options.put("cameraModels", cameraModels);

        // 获取镜头型号（带数量）
        List<Map<String, Object>> lensModels = filterOptionRepository.findByOptionTypeOrderByOptionKey("lens_models")
                .stream()
                .map(option -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", option.getOptionKey());
                    item.put("count", option.getPhotoCount());
                    return item;
                })
                .collect(Collectors.toList());
        options.put("lensModels", lensModels);

        // 获取颜色分类（带数量）
        List<Map<String, Object>> colorCategories = filterOptionRepository.findByOptionTypeOrderByOptionKey("color_categories")
                .stream()
                .map(option -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", option.getOptionKey());
                    item.put("count", option.getPhotoCount());
                    return item;
                })
                .collect(Collectors.toList());
        options.put("colorCategories", colorCategories);

        // 获取标签（带数量）
        List<Map<String, Object>> tags = tagRepository.findAllWithCount()
                .stream()
                .filter(tag -> tag.getPhotoCount() > 0) // 只显示有照片的标签
                .map(tag -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", tag.getId());
                    item.put("name", tag.getName());
                    item.put("color", tag.getColor());
                    item.put("count", tag.getPhotoCount());
                    return item;
                })
                .collect(Collectors.toList());
        options.put("tags", tags);

        // 获取范围值
        Double[] focalLengthRange = getRangeValues("focal_length");
        options.put("focalLengthRange", focalLengthRange);

        Double[] shutterSpeedRange = getRangeValues("shutter_speed");
        options.put("shutterSpeedRange", shutterSpeedRange);

        Double[] apertureRange = getRangeValues("aperture");
        options.put("apertureRange", apertureRange);

        Integer[] isoRange = getIsoRangeValues();
        options.put("isoRange", isoRange);

        return options;
    }

    private void updateCameraModels() {
        filterOptionRepository.deleteByOptionType("camera_models");

        // 获取相机型号及其数量
        List<Object[]> cameraStats = photoRepository.findCameraModelsWithCount();
        List<FilterOption> options = cameraStats.stream()
                .map(stat -> {
                    String model = (String) stat[0];
                    Number count = (Number) stat[1];
                    return new FilterOption(null, "camera_models", model, null, null, count.intValue(), null, null);
                })
                .collect(Collectors.toList());

        filterOptionRepository.saveAll(options);
        log.info("更新了 {} 个相机型号", options.size());
    }

    private void updateLensModels() {
        filterOptionRepository.deleteByOptionType("lens_models");

        // 获取镜头型号及其数量
        List<Object[]> lensStats = photoRepository.findLensModelsWithCount();
        List<FilterOption> options = lensStats.stream()
                .map(stat -> {
                    String model = (String) stat[0];
                    Number count = (Number) stat[1];
                    return new FilterOption(null, "lens_models", model, null, null, count.intValue(), null, null);
                })
                .collect(Collectors.toList());

        filterOptionRepository.saveAll(options);
        log.info("更新了 {} 个镜头型号", options.size());
    }

    private void updateColorCategories() {
        filterOptionRepository.deleteByOptionType("color_categories");

        // 获取颜色分类及其数量
        List<Object[]> colorStats = photoRepository.findColorCategoriesWithCount();
        List<FilterOption> options = colorStats.stream()
                .map(stat -> {
                    String category = (String) stat[0];
                    Number count = (Number) stat[1];
                    return new FilterOption(null, "color_categories", category, null, null, count.intValue(), null, null);
                })
                .collect(Collectors.toList());

        filterOptionRepository.saveAll(options);
        log.info("更新了 {} 个颜色分类", options.size());
    }

    private void updateFocalLengthRange() {
        filterOptionRepository.deleteByOptionType("focal_length");

        // 关键优化：用 SQL 聚合取 min/max，避免加载全表 photo
        Object[] range = photoRepository.findFocalLengthRange();
        if (range != null && range.length >= 2 && range[0] != null && range[1] != null) {
            double minVal = ((Number) range[0]).doubleValue();
            double maxVal = ((Number) range[1]).doubleValue();

            FilterOption minOption = new FilterOption(null, "focal_length", "min", null, minVal, null, null, null);
            FilterOption maxOption = new FilterOption(null, "focal_length", "max", null, maxVal, null, null, null);
            List<FilterOption> options = Arrays.asList(minOption, maxOption);
            filterOptionRepository.saveAll(options);
            log.info("更新焦距范围: {} - {} mm", minVal, maxVal);
        } else {
            log.info("没有找到焦距数据，跳过更新");
        }
    }

    private void updateShutterSpeedRange() {
        filterOptionRepository.deleteByOptionType("shutter_speed");

        Object[] range = photoRepository.findShutterSpeedRange();
        if (range != null && range.length >= 2 && range[0] != null && range[1] != null) {
            double minVal = ((Number) range[0]).doubleValue();
            double maxVal = ((Number) range[1]).doubleValue();

            FilterOption minOption = new FilterOption(null, "shutter_speed", "min", null, minVal, null, null, null);
            FilterOption maxOption = new FilterOption(null, "shutter_speed", "max", null, maxVal, null, null, null);
            List<FilterOption> options = Arrays.asList(minOption, maxOption);
            filterOptionRepository.saveAll(options);
            log.info("更新快门速度范围: {} - {} s", minVal, maxVal);
        } else {
            log.info("没有找到快门速度数据，跳过更新");
        }
    }

    private void updateApertureRange() {
        filterOptionRepository.deleteByOptionType("aperture");

        Object[] range = photoRepository.findApertureRange();
        if (range != null && range.length >= 2 && range[0] != null && range[1] != null) {
            double minVal = ((Number) range[0]).doubleValue();
            double maxVal = ((Number) range[1]).doubleValue();

            FilterOption minOption = new FilterOption(null, "aperture", "min", null, minVal, null, null, null);
            FilterOption maxOption = new FilterOption(null, "aperture", "max", null, maxVal, null, null, null);
            List<FilterOption> options = Arrays.asList(minOption, maxOption);
            filterOptionRepository.saveAll(options);
            log.info("更新光圈范围: f/{} - f/{}", minVal, maxVal);
        } else {
            log.info("没有找到光圈数据，跳过更新");
        }
    }

    private void updateIsoRange() {
        filterOptionRepository.deleteByOptionType("iso");

        Object[] range = photoRepository.findIsoRange();
        if (range != null && range.length >= 2 && range[0] != null && range[1] != null) {
            int minVal = ((Number) range[0]).intValue();
            int maxVal = ((Number) range[1]).intValue();

            FilterOption minOption = new FilterOption(null, "iso", "min", minVal + "", null, null, null, null);
            FilterOption maxOption = new FilterOption(null, "iso", "max", maxVal + "", null, null, null, null);
            List<FilterOption> options = Arrays.asList(minOption, maxOption);
            filterOptionRepository.saveAll(options);
            log.info("更新ISO范围: {} - {}", minVal, maxVal);
        } else {
            log.info("没有找到ISO数据，跳过更新");
        }
    }

    private Double[] getRangeValues(String optionType) {
        List<FilterOption> options = filterOptionRepository.findByOptionType(optionType);
        Double min = null, max = null;

        for (FilterOption option : options) {
            if ("min".equals(option.getOptionKey()) && option.getNumericValue() != null) {
                min = option.getNumericValue();
            } else if ("max".equals(option.getOptionKey()) && option.getNumericValue() != null) {
                max = option.getNumericValue();
            }
        }

        return new Double[]{min, max};
    }

    private Integer[] getIsoRangeValues() {
        List<FilterOption> options = filterOptionRepository.findByOptionType("iso");
        Integer min = null, max = null;

        for (FilterOption option : options) {
            if ("min".equals(option.getOptionKey()) && option.getOptionValue() != null) {
                try {
                    min = Integer.parseInt(option.getOptionValue());
                } catch (NumberFormatException e) {
                    log.warn("解析ISO最小值失败: {}", option.getOptionValue());
                }
            } else if ("max".equals(option.getOptionKey()) && option.getOptionValue() != null) {
                try {
                    max = Integer.parseInt(option.getOptionValue());
                } catch (NumberFormatException e) {
                    log.warn("解析ISO最大值失败: {}", option.getOptionValue());
                }
            }
        }

        return new Integer[]{min, max};
    }
}
