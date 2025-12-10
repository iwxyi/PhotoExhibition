-- 添加管理员用户表
CREATE TABLE IF NOT EXISTS `admin_user` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
  `role` VARCHAR(20) DEFAULT 'ADMIN' COMMENT '角色',
  `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员用户表';

-- 插入默认管理员账户（用户名：admin，密码：admin123）
-- 密码使用BCrypt加密，对应明文：admin123
INSERT INTO `admin_user` (`username`, `password`, `role`, `enabled`) 
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJ5C', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE `username`=`username`;

