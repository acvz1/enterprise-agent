package com.kb.demo.service;

import com.kb.demo.config.ModelConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * AI模型工厂类
 * 负责创建和管理各种AI模型实例
 * @author LiJingLin
 */
@Service
public class ModelFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(ModelFactory.class);
    
    @Autowired
    private ModelConfig modelConfig;
    
    public ChatLanguageModel createModel(String modelName) {
        logger.info("开始创建模型: {}", modelName);
        switch (modelName.toLowerCase()) {
            case "qwen":
                logger.debug("创建Qwen模型");
                return createQwenModel();
            case "deepseek":
                logger.debug("创建DeepSeek模型");
                return createDeepseekModel();
            case "kimi":
                logger.debug("创建Kimi模型");
                return createKimiModel();
            case "ollama":
                logger.debug("创建Ollama模型");
                return createOllamaModel();
            default:
                logger.warn("未知模型: {}, 使用默认Qwen模型", modelName);
                // 默认使用通义千问
                return createQwenModel();
        }
    }
    
    public StreamingChatLanguageModel createStreamingModel(String modelName) {
        logger.info("开始创建流式模型: {}", modelName);
        switch (modelName.toLowerCase()) {
            case "qwen":
                logger.debug("创建Qwen流式模型");
                return createQwenStreamingModel();
            case "deepseek":
                logger.debug("创建DeepSeek流式模型");
                return createDeepseekStreamingModel();
            case "kimi":
                logger.debug("创建Kimi流式模型");
                return createKimiStreamingModel();
            case "ollama":
                logger.debug("创建Ollama流式模型");
                return createOllamaStreamingModel();
            default:
                logger.warn("未知流式模型: {}, 使用默认Qwen流式模型", modelName);
                // 默认使用通义千问
                return createQwenStreamingModel();
        }
    }
    
    private ChatLanguageModel createQwenModel() {
        logger.info("创建Qwen模型实例");
        ModelConfig.ModelProperties config = modelConfig.getQwen();
        logger.debug("Qwen配置 - API密钥: {}, 模型名称: {}", 
            config.getApiKey() != null ? config.getApiKey().substring(0, Math.min(8, config.getApiKey().length())) + "..." : "null", 
            config.getModelName());
        return QwenChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .build();
    }
    
    private ChatLanguageModel createDeepseekModel() {
        logger.info("创建DeepSeek模型实例");
        ModelConfig.ModelProperties config = modelConfig.getDeepseek();
        logger.debug("DeepSeek配置 - API密钥: {}, 基础URL: {}, 模型名称: {}", 
            config.getApiKey() != null ? config.getApiKey().substring(0, Math.min(8, config.getApiKey().length())) + "..." : "null", 
            config.getBaseUrl(), 
            config.getModelName());
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .build();
    }
    
    private ChatLanguageModel createKimiModel() {
        logger.info("创建Kimi模型实例");
        ModelConfig.ModelProperties config = modelConfig.getKimi();
        logger.debug("Kimi配置 - API密钥: {}, 基础URL: {}, 模型名称: {}", 
            config.getApiKey() != null ? config.getApiKey().substring(0, Math.min(8, config.getApiKey().length())) + "..." : "null", 
            config.getBaseUrl(), 
            config.getModelName());
        return OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .build();
    }
    
    private ChatLanguageModel createOllamaModel() {
        logger.info("创建Ollama模型实例");
        ModelConfig.ModelProperties config = modelConfig.getOllama();
        logger.debug("Ollama配置 - 基础URL: {}, 模型名称: {}", 
            config.getBaseUrl(), 
            config.getModelName());
        return OllamaChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .timeout(Duration.ofMinutes(5))  // 设置5分钟超时
                .build();
    }
    
    private StreamingChatLanguageModel createQwenStreamingModel() {
        logger.info("创建Qwen流式模型实例");
        ModelConfig.ModelProperties config = modelConfig.getQwen();
        logger.debug("Qwen流式配置 - API密钥: {}, 模型名称: {}", 
            config.getApiKey() != null ? config.getApiKey().substring(0, Math.min(8, config.getApiKey().length())) + "..." : "null", 
            config.getModelName());
        return QwenStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .build();
    }
    
    private StreamingChatLanguageModel createDeepseekStreamingModel() {
        logger.info("创建DeepSeek流式模型实例");
        ModelConfig.ModelProperties config = modelConfig.getDeepseek();
        logger.debug("DeepSeek流式配置 - API密钥: {}, 基础URL: {}, 模型名称: {}", 
            config.getApiKey() != null ? config.getApiKey().substring(0, Math.min(8, config.getApiKey().length())) + "..." : "null", 
            config.getBaseUrl(), 
            config.getModelName());
        return OpenAiStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .build();
    }
    
    private StreamingChatLanguageModel createKimiStreamingModel() {
        logger.info("创建Kimi流式模型实例");
        ModelConfig.ModelProperties config = modelConfig.getKimi();
        logger.debug("Kimi流式配置 - API密钥: {}, 基础URL: {}, 模型名称: {}", 
            config.getApiKey() != null ? config.getApiKey().substring(0, Math.min(8, config.getApiKey().length())) + "..." : "null", 
            config.getBaseUrl(), 
            config.getModelName());
        return OpenAiStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .build();
    }
    
    private StreamingChatLanguageModel createOllamaStreamingModel() {
        logger.info("创建Ollama流式模型实例");
        ModelConfig.ModelProperties config = modelConfig.getOllama();
        logger.debug("Ollama流式配置 - 基础URL: {}, 模型名称: {}", 
            config.getBaseUrl(), 
            config.getModelName());
        return OllamaStreamingChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .timeout(Duration.ofMinutes(5))  // 设置5分钟超时
                .build();
    }
}