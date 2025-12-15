-- 修复photo_tag表结构
-- JPA的@ManyToMany不需要关联表的id字段，使用复合主键即可
-- 
-- 执行步骤：
-- 1. 先删除外键约束
-- 2. 删除id字段和旧的主键
-- 3. 添加新的复合主键
-- 4. 重新添加外键约束

-- 删除外键约束
ALTER TABLE `photo_tag` DROP FOREIGN KEY `fk_photo_tag_photo`;
ALTER TABLE `photo_tag` DROP FOREIGN KEY `fk_photo_tag_tag`;

-- 删除旧的主键和id字段
ALTER TABLE `photo_tag` DROP PRIMARY KEY;
ALTER TABLE `photo_tag` DROP COLUMN `id`;

-- 添加新的复合主键
ALTER TABLE `photo_tag` ADD PRIMARY KEY (`photo_id`, `tag_id`);

-- 重新添加外键约束
ALTER TABLE `photo_tag` 
  ADD CONSTRAINT `fk_photo_tag_photo` FOREIGN KEY (`photo_id`) REFERENCES `photo` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_photo_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE;

