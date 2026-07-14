package com.kb.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI模型配置类
 * 配置各种AI模型的参数和属性
 * @author LiJingLin
 */
@Component
@ConfigurationProperties(prefix = "langchain4j")
public class ModelConfig {
    
    private String defaultModel;
    
    private ModelProperties qwen;
    private ModelProperties deepseek;
    private ModelProperties kimi;
    private ModelProperties ollama;
    
    public static class ModelProperties {
        private String apiKey;
        private String baseUrl;
        private String modelName;
        
        // Getters and Setters
        public String getApiKey() {
            return apiKey;
        }
        
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
        
        public String getBaseUrl() {
            return baseUrl;
        }
        
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
        
        public String getModelName() {
            return modelName;
        }
        
        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
    }
    
    // Getters and Setters
    public String getDefaultModel() {
        return defaultModel;
    }
    
    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }
    
    public ModelProperties getQwen() {
        return qwen;
    }
    
    public void setQwen(ModelProperties qwen) {
        this.qwen = qwen;
    }
    
    public ModelProperties getDeepseek() {
        return deepseek;
    }
    
    public void setDeepseek(ModelProperties deepseek) {
        this.deepseek = deepseek;
    }
    
    public ModelProperties getKimi() {
        return kimi;
    }
    
    public void setKimi(ModelProperties kimi) {
        this.kimi = kimi;
    }
    
    public ModelProperties getOllama() {
        return ollama;
    }
    
    public void setOllama(ModelProperties ollama) {
        this.ollama = ollama;
    }
}