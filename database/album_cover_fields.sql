-- 添加相册自定义封面字段
-- 执行此SQL前请备份数据库

ALTER TABLE album ADD COLUMN cover_image_ids TEXT;

-- 为现有相册创建初始的封面ID列表（从已有的cover_image_id迁移）
-- 注意：这里只迁移单个封面到cover1，cover2/3/4为空
UPDATE album
SET cover_image_ids = '[' || cover_image_id || ']'
WHERE cover_image_id IS NOT NULL;

-- 可选：如果想清除旧的单个封面字段，可以取消下面的注释（建议保留以备回滚）
-- ALTER TABLE album DROP COLUMN cover_image_id;

-- 创建索引以提高查询性能（如果需要）
-- CREATE INDEX IF NOT EXISTS idx_album_cover_image_ids ON album(cover_image_ids);

