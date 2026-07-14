-- 为documents表添加file_type列
ALTER TABLE documents ADD COLUMN file_type VARCHAR(50) COMMENT '文件类型';