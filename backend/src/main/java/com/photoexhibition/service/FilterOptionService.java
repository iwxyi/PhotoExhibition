package com.photoexhibition.service;

import com.photoexhibition.entity.FilterOption;
import com.photoexhibition.repository.FilterOptionRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.TagRepository;
import com.photoexhibition.repository.UserAccountRepository;
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
    private final SystemConfigService systemConfigService;
    private final UserAccountRepository userAccountRepository;

    /**
     * 更新所有筛选选项
     */
    @Transactional
    public void updateAllFilterOptions() {
        log.info("开始更新筛选选项...");

        try {
            if (systemConfigService.isMultiUserEnabled()) {
                userAccountRepository.findAllIds().forEach(this::updateAllFilterOptions);
            } else {
                updateAllFilterOptions(null);
            }

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
        return getFilterOptions(null);
    }

    public Map<String, Object> getFilterOptions(Long userId) {
        Map<String, Object> options = new HashMap<>();
        options.put("cameraModels", buildOptionItems("camera_models", userId));
        options.put("lensModels", buildOptionItems("lens_models", userId));
        options.put("colorCategories", buildOptionItems("color_categories", userId));
        options.put("tags", buildTagItems(userId));
        options.put("focalLengthRange", getRangeValues("focal_length", userId));
        options.put("shutterSpeedRange", getRangeValues("shutter_speed", userId));
        options.put("apertureRange", getRangeValues("aperture", userId));
        options.put("isoRange", getIsoRangeValues(userId));
        return options;
    }

    @Transactional
    public void updateAllFilterOptions(Long userId) {
        updateCameraModels(userId);
        updateLensModels(userId);
        updateColorCategories(userId);
        updateFocalLengthRange(userId);
        updateShutterSpeedRange(userId);
        updateApertureRange(userId);
        updateIsoRange(userId);
    }

    private List<Map<String, Object>> buildOptionItems(String optionType, Long userId) {
        return filterOptionRepository.findByOptionTypeAndUserIdOrderByOptionKey(optionType, userId)
            .stream()
            .map(option -> {
                Map<String, Object> item = new HashMap<>();
                item.put("name", option.getOptionKey());
                item.put("count", option.getPhotoCount());
                return item;
            })
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildTagItems(Long userId) {
        Set<String> ignoredTags = systemConfigService.getTagIgnoreListSet();
        return (userId == null ? tagRepository.findAllWithCount() : tagRepository.findAllWithCountByUserId(userId))
            .stream()
            .filter(tag -> tag.getPhotoCount() > 0)
            .filter(tag -> !ignoredTags.contains(tag.getName()))
            .map(tag -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", tag.getId());
                item.put("name", tag.getName());
                item.put("color", tag.getColor());
                item.put("count", tag.getPhotoCount());
                return item;
            })
            .collect(Collectors.toList());
    }

    private Double[] getDoubleRange(Object[] values) {
        if (values == null || values.length < 2 || values[0] == null || values[1] == null) {
            return new Double[]{null, null};
        }
        return new Double[]{((Number) values[0]).doubleValue(), ((Number) values[1]).doubleValue()};
    }

    private Integer[] getIntegerRange(Object[] values) {
        if (values == null || values.length < 2 || values[0] == null || values[1] == null) {
            return new Integer[]{null, null};
        }
        return new Integer[]{((Number) values[0]).intValue(), ((Number) values[1]).intValue()};
    }

    private void updateCameraModels(Long userId) {
        filterOptionRepository.deleteByOptionTypeAndUserId("camera_models", userId);

        List<FilterOption> options = photoRepository.findCameraModelCountsByScopedUserId(userId).stream()
            .map(entry -> buildFilterOption(userId, "camera_models", entry.getValue(), null, null, entry.getPhotoCount().intValue()))
            .collect(Collectors.toList());

        filterOptionRepository.saveAll(options);
        log.info("更新了 {} 个相机型号", options.size());
    }

    private void updateLensModels(Long userId) {
        filterOptionRepository.deleteByOptionTypeAndUserId("lens_models", userId);

        List<FilterOption> options = photoRepository.findLensModelCountsByScopedUserId(userId).stream()
            .map(entry -> buildFilterOption(userId, "lens_models", entry.getValue(), null, null, entry.getPhotoCount().intValue()))
            .collect(Collectors.toList());

        filterOptionRepository.saveAll(options);
        log.info("更新了 {} 个镜头型号", options.size());
    }

    private void updateColorCategories(Long userId) {
        filterOptionRepository.deleteByOptionTypeAndUserId("color_categories", userId);

        List<FilterOption> options = photoRepository.findColorCategoryCountsByScopedUserId(userId).stream()
            .map(entry -> buildFilterOption(userId, "color_categories", entry.getValue(), null, null, entry.getPhotoCount().intValue()))
            .collect(Collectors.toList());

        filterOptionRepository.saveAll(options);
        log.info("更新了 {} 个颜色分类", options.size());
    }

    private void updateFocalLengthRange(Long userId) {
        filterOptionRepository.deleteByOptionTypeAndUserId("focal_length", userId);

        Double[] range = getDoubleRange(photoRepository.findFocalLengthRangeByScopedUserId(userId));
        if (range[0] != null && range[1] != null) {
            List<FilterOption> options = Arrays.asList(
                buildFilterOption(userId, "focal_length", "min", null, range[0], null),
                buildFilterOption(userId, "focal_length", "max", null, range[1], null)
            );
            filterOptionRepository.saveAll(options);
            log.info("更新焦距范围: {} - {} mm", range[0], range[1]);
        } else {
            log.info("没有找到焦距数据，跳过更新");
        }
    }

    private void updateShutterSpeedRange(Long userId) {
        filterOptionRepository.deleteByOptionTypeAndUserId("shutter_speed", userId);

        Double[] range = getDoubleRange(photoRepository.findShutterSpeedRangeByScopedUserId(userId));
        if (range[0] != null && range[1] != null) {
            List<FilterOption> options = Arrays.asList(
                buildFilterOption(userId, "shutter_speed", "min", null, range[0], null),
                buildFilterOption(userId, "shutter_speed", "max", null, range[1], null)
            );
            filterOptionRepository.saveAll(options);
            log.info("更新快门速度范围: {} - {} s", range[0], range[1]);
        } else {
            log.info("没有找到快门速度数据，跳过更新");
        }
    }

    private void updateApertureRange(Long userId) {
        filterOptionRepository.deleteByOptionTypeAndUserId("aperture", userId);

        Double[] range = getDoubleRange(photoRepository.findApertureRangeByScopedUserId(userId));
        if (range[0] != null && range[1] != null) {
            List<FilterOption> options = Arrays.asList(
                buildFilterOption(userId, "aperture", "min", null, range[0], null),
                buildFilterOption(userId, "aperture", "max", null, range[1], null)
            );
            filterOptionRepository.saveAll(options);
            log.info("更新光圈范围: f/{} - f/{}", range[0], range[1]);
        } else {
            log.info("没有找到光圈数据，跳过更新");
        }
    }

    private void updateIsoRange(Long userId) {
        filterOptionRepository.deleteByOptionTypeAndUserId("iso", userId);

        Integer[] range = getIntegerRange(photoRepository.findIsoRangeByScopedUserId(userId));
        if (range[0] != null && range[1] != null) {
            List<FilterOption> options = Arrays.asList(
                buildFilterOption(userId, "iso", "min", String.valueOf(range[0]), null, null),
                buildFilterOption(userId, "iso", "max", String.valueOf(range[1]), null, null)
            );
            filterOptionRepository.saveAll(options);
            log.info("更新ISO范围: {} - {}", range[0], range[1]);
        } else {
            log.info("没有找到ISO数据，跳过更新");
        }
    }

    private Double[] getRangeValues(String optionType, Long userId) {
        List<FilterOption> options = filterOptionRepository.findByOptionTypeAndUserId(optionType, userId);
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

    private Integer[] getIsoRangeValues(Long userId) {
        List<FilterOption> options = filterOptionRepository.findByOptionTypeAndUserId("iso", userId);
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

    private FilterOption buildFilterOption(Long userId, String optionType, String optionKey, String optionValue, Double numericValue, Integer photoCount) {
        FilterOption option = new FilterOption();
        option.setUserId(userId);
        option.setOptionType(optionType);
        option.setOptionKey(optionKey);
        option.setOptionValue(optionValue);
        option.setNumericValue(numericValue);
        option.setPhotoCount(photoCount);
        return option;
    }
}
