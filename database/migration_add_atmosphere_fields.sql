-- 为相册表添加氛围信息字段的迁移脚本
-- 执行前请备份数据库

-- 添加氛围信息字段
ALTER TABLE `album`
ADD COLUMN `background_color` VARCHAR(20) DEFAULT NULL COMMENT '背景颜色（HEX）' AFTER `photo_count`,
ADD COLUMN `foreground_color` VARCHAR(20) DEFAULT NULL COMMENT '前景色（HEX）' AFTER `background_color`,
ADD COLUMN `navbar_color` VARCHAR(20) DEFAULT NULL COMMENT '导航栏颜色（HEX）' AFTER `foreground_color`,
ADD COLUMN `atmosphere_effects` JSON DEFAULT NULL COMMENT '氛围特效配置（JSON数组）' AFTER `navbar_color`,
ADD COLUMN `atmosphere_last_updated` DATETIME DEFAULT NULL COMMENT '氛围信息最后更新时间' AFTER `atmosphere_effects`,
ADD COLUMN `path_hash` VARCHAR(64) DEFAULT NULL COMMENT '路径哈希（用于唯一性检查）' AFTER `path`;

-- 创建新索引
ALTER TABLE `album` ADD UNIQUE KEY `uk_path_hash` (`path_hash`);

-- 为现有相册生成path_hash（如果需要）
UPDATE `album` SET `path_hash` = SHA2(`path`, 256) WHERE `path_hash` IS NULL;

-- 修改唯一键约束（如果原有的uk_path还在）
-- ALTER TABLE `album` DROP INDEX `uk_path`;
-- 注意：如果上面的DROP失败，需要手动检查并删除旧的uk_path索引


