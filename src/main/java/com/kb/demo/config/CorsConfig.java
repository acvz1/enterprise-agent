package com.kb.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS配置类
 * 解决跨域请求问题
 * @author LiJingLin
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 应用到所有路径
                .allowedOriginPatterns("*") // 允许所有源（使用 allowedOriginPatterns 而不是 allowedOrigins）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的HTTP方法
                .allowedHeaders("*") // 允许所有头部
                .allowCredentials(true) // 允许发送凭证信息（如cookies）
                .maxAge(3600); // 预检请求的有效期，单位为秒
    }
}