-- 摄影作品展示平台数据库表结构

-- 相册表
CREATE TABLE IF NOT EXISTS `album` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(255) NOT NULL COMMENT '相册名称',
  `path` VARCHAR(500) NOT NULL COMMENT '文件夹路径',
  `cover_image_id` BIGINT(20) DEFAULT NULL COMMENT '封面图片ID',
  `description` TEXT COMMENT '相册描述',
  `photo_count` INT(11) DEFAULT 0 COMMENT '照片数量',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_path` (`path`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相册表';

-- 图片表
CREATE TABLE IF NOT EXISTS `photo` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `album_id` BIGINT(20) NOT NULL COMMENT '相册ID',
  `filename` VARCHAR(255) NOT NULL COMMENT '文件名',
  `original_path` VARCHAR(1000) NOT NULL COMMENT '原始文件路径',
  `thumbnail_path` VARCHAR(1000) DEFAULT NULL COMMENT '缩略图路径',
  `webp_path` VARCHAR(1000) DEFAULT NULL COMMENT 'WebP格式路径',
  `file_size` BIGINT(20) DEFAULT NULL COMMENT '文件大小（字节）',
  `width` INT(11) DEFAULT NULL COMMENT '宽度（像素）',
  `height` INT(11) DEFAULT NULL COMMENT '高度（像素）',
  `format` VARCHAR(50) DEFAULT NULL COMMENT '图片格式',
  `dominant_color` VARCHAR(20) DEFAULT NULL COMMENT '主色调（HEX）',
  `color_palette` JSON DEFAULT NULL COMMENT '色彩调色板（JSON数组）',
  `exif_data` JSON DEFAULT NULL COMMENT 'EXIF信息（JSON）',
  `camera_make` VARCHAR(100) DEFAULT NULL COMMENT '相机品牌',
  `camera_model` VARCHAR(100) DEFAULT NULL COMMENT '相机型号',
  `lens_model` VARCHAR(100) DEFAULT NULL COMMENT '镜头型号',
  `focal_length` VARCHAR(50) DEFAULT NULL COMMENT '焦距',
  `aperture` VARCHAR(50) DEFAULT NULL COMMENT '光圈',
  `shutter_speed` VARCHAR(50) DEFAULT NULL COMMENT '快门速度',
  `iso` INT(11) DEFAULT NULL COMMENT 'ISO感光度',
  `taken_at` DATETIME DEFAULT NULL COMMENT '拍摄时间',
  `quality_score` DECIMAL(5,2) DEFAULT NULL COMMENT '质量评分（0-100）',
  `view_count` INT(11) DEFAULT 0 COMMENT '查看次数',
  `is_featured` TINYINT(1) DEFAULT 0 COMMENT '是否精选',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_album_id` (`album_id`),
  KEY `idx_taken_at` (`taken_at`),
  KEY `idx_camera_model` (`camera_model`),
  KEY `idx_is_featured` (`is_featured`),
  KEY `idx_quality_score` (`quality_score`),
  CONSTRAINT `fk_photo_album` FOREIGN KEY (`album_id`) REFERENCES `album` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片表';

-- 标签表
CREATE TABLE IF NOT EXISTS `tag` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(100) NOT NULL COMMENT '标签名称',
  `color` VARCHAR(20) DEFAULT NULL COMMENT '标签颜色',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

-- 相册标签关联表
CREATE TABLE IF NOT EXISTS `album_tag` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `album_id` BIGINT(20) NOT NULL COMMENT '相册ID',
  `tag_id` BIGINT(20) NOT NULL COMMENT '标签ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_album_tag` (`album_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`),
  CONSTRAINT `fk_album_tag_album` FOREIGN KEY (`album_id`) REFERENCES `album` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_album_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相册标签关联表';

-- 图片标签关联表
CREATE TABLE IF NOT EXISTS `photo_tag` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `photo_id` BIGINT(20) NOT NULL COMMENT '图片ID',
  `tag_id` BIGINT(20) NOT NULL COMMENT '标签ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_photo_tag` (`photo_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`),
  CONSTRAINT `fk_photo_tag_photo` FOREIGN KEY (`photo_id`) REFERENCES `photo` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_photo_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片标签关联表';

-- 管理员表
CREATE TABLE IF NOT EXISTS `admin` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（加密）',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS `system_config` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
  `config_value` TEXT COMMENT '配置值',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '配置描述',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 初始化管理员账号（密码：admin123，实际使用时需要加密）
INSERT INTO `admin` (`username`, `password`, `email`) VALUES 
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8p6/Pa', 'admin@photoexhibition.com')
ON DUPLICATE KEY UPDATE `username`=`username`;

