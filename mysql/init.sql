-- 设置默认字符集
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS ai_knowledge_base DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ai_knowledge_base;

-- 设置连接字符集
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- 创建文档表
CREATE TABLE IF NOT EXISTS documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '文档标题',
    content TEXT NOT NULL COMMENT '文档内容',
    current_version INT DEFAULT 1 COMMENT '当前版本号',
    file_type VARCHAR(50) COMMENT '文件类型',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_title (title),
    KEY idx_created_at (created_at),
    KEY idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档表';

-- 创建文档版本表
CREATE TABLE IF NOT EXISTS document_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT '文档ID',
    version_number INT NOT NULL COMMENT '版本号',
    title VARCHAR(200) NOT NULL COMMENT '文档标题',
    content TEXT NOT NULL COMMENT '文档内容',
    change_summary VARCHAR(500) COMMENT '变更摘要',
    created_by VARCHAR(100) COMMENT '创建者',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_version (document_id, version_number),
    KEY idx_document_id (document_id),
    CONSTRAINT fk_document_version_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档版本表';

-- 创建文档块表
CREATE TABLE IF NOT EXISTS document_chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT '文档ID',
    chunk_index INT NOT NULL COMMENT '块索引',
    content TEXT NOT NULL COMMENT '块内容',
    embedding_vector BLOB COMMENT '向量数据',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_chunk (document_id, chunk_index),
    KEY idx_document_id (document_id),
    CONSTRAINT fk_document_chunk_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档块表';

-- 创建文档分类表
CREATE TABLE IF NOT EXISTS document_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '分类名称',
    description VARCHAR(500) COMMENT '分类描述',
    color VARCHAR(7) DEFAULT '#1890ff' COMMENT '分类颜色',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分类表';

-- 创建文档标签表
CREATE TABLE IF NOT EXISTS document_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '标签名称',
    description VARCHAR(500) COMMENT '标签描述',
    color VARCHAR(7) DEFAULT '#52c41a' COMMENT '标签颜色',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档标签表';

-- 创建文档与分类关联表
CREATE TABLE IF NOT EXISTS document_category_relations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT '文档ID',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_category (document_id, category_id),
    KEY idx_document_id (document_id),
    KEY idx_category_id (category_id),
    CONSTRAINT fk_document_category_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_category_category FOREIGN KEY (category_id) REFERENCES document_categories (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档与分类关联表';

-- 创建文档与标签关联表
CREATE TABLE IF NOT EXISTS document_tag_relations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT '文档ID',
    tag_id BIGINT NOT NULL COMMENT '标签ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_tag (document_id, tag_id),
    KEY idx_document_id (document_id),
    KEY idx_tag_id (tag_id),
    CONSTRAINT fk_document_tag_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_tag_tag FOREIGN KEY (tag_id) REFERENCES document_tags (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档与标签关联表';

-- 插入默认分类数据
INSERT INTO document_categories (name, description, color) VALUES
('技术文档', '技术相关的文档', '#1890ff'),
('产品文档', '产品相关的文档', '#52c41a'),
('用户手册', '用户使用手册', '#fa8c16'),
('API文档', '接口文档', '#722ed1'),
('其他', '其他类型的文档', '#8c8c8c');

-- 插入默认标签数据
INSERT INTO document_tags (name, description, color) VALUES
('重要', '重要文档', '#f5222d'),
('待审核', '待审核的文档', '#fa8c16'),
('已发布', '已发布的文档', '#52c41a'),
('草稿', '草稿文档', '#8c8c8c'),
('归档', '已归档的文档', '#595959'),
('Java', 'Java相关文档', '#1890ff'),
('前端', '前端相关文档', '#722ed1'),
('后端', '后端相关文档', '#13c2c2'),
('数据库', '数据库相关文档', '#fa541c'),
('算法', '算法相关文档', '#eb2f96');

SET FOREIGN_KEY_CHECKS = 1;