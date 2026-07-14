package com.kb.demo.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

/**
 * 异步处理配置
 * 用于配置线程池，支持大文件异步上传和处理
 * @author LiJingLin
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    
    /**
     * 配置异步任务执行器
     * 用于文件上传、解析、分块、向量化等耗时操作
     * 使用 DelegatingSecurityContextAsyncTaskExecutor 包装，自动传递 SecurityContext
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数（始终存活）
        executor.setCorePoolSize(5);
        
        // 最大线程数（队列满时创建）
        executor.setMaxPoolSize(10);
        
        // 队列容量（等待队列）
        executor.setQueueCapacity(100);
        
        // 线程名称前缀
        executor.setThreadNamePrefix("file-processing-");
        
        // 线程池关闭时的等待时间（秒）
        executor.setAwaitTerminationSeconds(60);
        
        // 应用关闭时，是否等待所有任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 拒绝策略：使用调用者所在线程执行任务
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        
        // 关键：包装为 DelegatingSecurityContextAsyncTaskExecutor，自动传递 SecurityContext
        // 这样异步线程中也能获取到当前用户的认证信息
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }
}
