-- 照片置顶状态；Spring 的 ddl-auto=update 会自动创建，新环境可手动执行本语句。
ALTER TABLE photo ADD COLUMN is_pinned BOOLEAN NOT NULL DEFAULT FALSE;
