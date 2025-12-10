-- 修复路径字段长度不足的问题
-- 执行此SQL来更新现有数据库表结构

ALTER TABLE `photo` 
MODIFY COLUMN `original_path` VARCHAR(1000) NOT NULL COMMENT '原始文件路径',
MODIFY COLUMN `thumbnail_path` VARCHAR(1000) DEFAULT NULL COMMENT '缩略图路径',
MODIFY COLUMN `webp_path` VARCHAR(1000) DEFAULT NULL COMMENT 'WebP格式路径';

