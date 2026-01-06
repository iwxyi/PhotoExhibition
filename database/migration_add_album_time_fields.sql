-- 添加相册时间字段到 album 表
-- 执行时间: 2026-01-XX

ALTER TABLE `album`
ADD COLUMN IF NOT EXISTS `latest_photo_taken_at` DATETIME DEFAULT NULL COMMENT '相册中最晚的照片拍摄时间',
ADD COLUMN IF NOT EXISTS `album_name_date` DATETIME DEFAULT NULL COMMENT '从相册名称中解析的时间';
