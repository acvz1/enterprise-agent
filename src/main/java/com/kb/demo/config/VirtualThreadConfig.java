package com.kb.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.task.SimpleAsyncTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * 虚拟线程配置
 * 启用 JDK 21+ 的虚拟线程（Project Loom）
 * 
 * 优势：
 * - 极低的内存占用（每个虚拟线程约 1KB vs 传统线程约 1MB）
 * - 支持百万级并发
 * - 简化异步编程模型
 * 
 * @author LiJingLin
 */
@Configuration
public class VirtualThreadConfig {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadConfig.class);

    /**
     * 配置异步任务执行器使用虚拟线程
     * 替换默认的线程池
     */
    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor asyncTaskExecutor(SimpleAsyncTaskExecutorBuilder builder) {
        SimpleAsyncTaskExecutor executor = builder
                .threadNamePrefix("virtual-thread-")
                .build();
        
        // 启用虚拟线程
        executor.setVirtualThreads(true);
        
        log.info("✓ 虚拟线程执行器已启用 - 支持高并发场景");
        log.info("  └─ 线程模式: JDK 21+ Virtual Threads (Project Loom)");
        log.info("  └─ 内存优势: ~1KB/线程 (传统线程 ~1MB/线程)");
        log.info("  └─ 并发能力: 百万级");
        
        return executor;
    }

    /**
     * 打印虚拟线程相关信息（用于调试）
     */
    @Bean
    public VirtualThreadInfo virtualThreadInfo() {
        return new VirtualThreadInfo();
    }

    /**
     * 虚拟线程信息工具类
     */
    public static class VirtualThreadInfo {
        
        /**
         * 检查当前线程是否为虚拟线程
         */
        public boolean isVirtual(Thread thread) {
            return thread.isVirtual();
        }

        /**
         * 打印当前线程信息
         */
        public void logCurrentThread() {
            Thread currentThread = Thread.currentThread();
            log.debug("当前线程: {} | 虚拟线程: {} | ID: {}", 
                currentThread.getName(),
                currentThread.isVirtual() ? "是" : "否",
                currentThread.threadId()
            );
        }

        /**
         * 获取虚拟线程统计信息
         */
        public String getStats() {
            return String.format(
                "虚拟线程状态 - 支持: %s | 当前线程类型: %s",
                "JDK 21+",
                Thread.currentThread().isVirtual() ? "虚拟线程" : "平台线程"
            );
        }
    }
}
