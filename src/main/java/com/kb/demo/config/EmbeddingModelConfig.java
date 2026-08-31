package com.kb.demo.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集中管理 Embedding 模型与向量维度。
 * BGE-small-zh-v1.5 面向中文/中英混合语义检索，本地 ONNX 运行，输出 512 维向量。
 * Redis Vector 索引与所有维度硬编码统一从此处读取，避免维度混用。
 */
@Configuration
public class EmbeddingModelConfig {

    /** BGE-small-zh-v1.5 官方向量维度。 */
    public static final int EMBEDDING_DIMENSION = 512;

    @Bean
    public EmbeddingModel embeddingModel() {
        return new BgeSmallZhV15EmbeddingModel();
    }
}
