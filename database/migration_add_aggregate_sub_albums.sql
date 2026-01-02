-- 添加相册聚合下级相册功能
-- 为album表添加aggregate_sub_albums字段

ALTER TABLE album ADD COLUMN aggregate_sub_albums TINYINT(1) DEFAULT 0 COMMENT '是否聚合下级相册';

-- 为现有相册设置默认值
UPDATE album SET aggregate_sub_albums = 0 WHERE aggregate_sub_albums IS NULL;
