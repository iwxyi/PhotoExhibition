-- 创建筛选选项表
CREATE TABLE filter_option (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    option_type VARCHAR(50) NOT NULL COMMENT '选项类型，如 camera_models, lens_models, focal_length等',
    option_key VARCHAR(255) NOT NULL COMMENT '具体的值，如相机型号名称，或 range_min/range_max',
    option_value VARCHAR(255) COMMENT '对应的值（用于字符串类型）',
    numeric_value DECIMAL(10,3) COMMENT '数值类型的值（用于范围查询）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_option_type (option_type),
    INDEX idx_option_key (option_key),
    UNIQUE KEY uk_option_type_key (option_type, option_key)
) COMMENT '筛选选项表，用于存储相机型号、镜头型号等筛选选项，避免每次查询都从照片表聚合';

-- 插入一些初始数据（可选，实际数据会在扫描完成后自动填充）
INSERT INTO filter_option (option_type, option_key, option_value) VALUES
('camera_models', '初始化标记', '此表会在扫描完成后自动填充数据'),
('lens_models', '初始化标记', '此表会在扫描完成后自动填充数据'),
('focal_length', '初始化标记', '此表会在扫描完成后自动填充数据'),
('shutter_speed', '初始化标记', '此表会在扫描完成后自动填充数据'),
('aperture', '初始化标记', '此表会在扫描完成后自动填充数据'),
('iso', '初始化标记', '此表会在扫描完成后自动填充数据');
