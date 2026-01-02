-- 为相册表添加照片排序方式字段
-- 执行时间：需要在系统运行后添加此字段

ALTER TABLE `album` ADD COLUMN `photo_sort_order` VARCHAR(50) DEFAULT NULL COMMENT '相册照片排序方式' AFTER `aggregate_sub_albums`;
