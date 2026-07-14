package com.kb.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA配置类
 * 启用JPA审计功能，自动处理创建时间和更新时间
 * @author LiJingLin
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}