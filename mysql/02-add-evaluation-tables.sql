-- 上传进度表
CREATE TABLE IF NOT EXISTS upload_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    upload_id VARCHAR(36) NOT NULL UNIQUE COMMENT '唯一上传ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名称',
    file_size BIGINT NOT NULL COMMENT '文件总大小（字节）',
    uploaded_size BIGINT DEFAULT 0 COMMENT '已上传大小',
    percentage INT DEFAULT 0 COMMENT '上传进度百分比',
    status VARCHAR(20) NOT NULL COMMENT '上传状态: UPLOADING,PARSING,CHUNKING,EMBEDDING,COMPLETED,FAILED',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_upload_id (upload_id),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传进度表';

-- 问答评估表
CREATE TABLE IF NOT EXISTS answer_evaluations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL COMMENT '会话ID',
    question TEXT NOT NULL COMMENT '用户问题',
    answer LONGTEXT NOT NULL COMMENT 'AI回答',
    relevance_score DOUBLE DEFAULT 0.0 COMMENT '相关性评分(0-1)',
    completeness_score DOUBLE DEFAULT 0.0 COMMENT '完整性评分(0-1)',
    hallucination DOUBLE DEFAULT 0.0 COMMENT '幻觉程度(0-1)',
    overall_score DOUBLE DEFAULT 0.0 COMMENT '综合评分(0-1)',
    evaluation_level VARCHAR(20) COMMENT '评分级别: EXCELLENT,GOOD,FAIR,POOR',
    user_feedback INT DEFAULT 0 COMMENT '用户反馈: -1(差), 0(中立), 1(好)',
    model VARCHAR(50) NOT NULL COMMENT '使用的模型名称',
    response_time BIGINT DEFAULT 0 COMMENT '响应时间（毫秒）',
    retrieved_doc_count INT DEFAULT 0 COMMENT '检索到的文档数',
    evaluation_notes TEXT COMMENT '评估备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_session_id (session_id),
    KEY idx_evaluation_level (evaluation_level),
    KEY idx_model (model),
    KEY idx_created_at (created_at),
    KEY idx_user_feedback (user_feedback)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答评估表，用于评估和记录每条回答的质量';
