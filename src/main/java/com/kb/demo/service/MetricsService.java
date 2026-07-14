package com.kb.demo.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自定义业务指标服务
 * 用于收集和暴露业务相关的监控指标
 */
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;
    
    // 问答次数计数器
    private final Counter qaCounter;
    
    // 文档上传计数器
    private final Counter documentUploadCounter;
    
    // 向量检索计数器
    private final Counter vectorSearchCounter;
    
    // 缓存命中计数器
    private final Counter cacheHitCounter;
    
    // 缓存未命中计数器
    private final Counter cacheMissCounter;
    
    // 问答响应时间计时器
    private final Timer qaResponseTimer;
    
    // 文档处理时间计时器
    private final Timer documentProcessingTimer;
    
    // 向量检索时间计时器
    private final Timer vectorSearchTimer;
    
    // 各模型使用次数
    private final ConcurrentHashMap<String, Counter> modelUsageCounters = new ConcurrentHashMap<>();
    
    // 评分分布统计（1-5分）
    private final ConcurrentHashMap<Integer, AtomicInteger> ratingDistribution = new ConcurrentHashMap<>();
    
    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化计数器
        this.qaCounter = Counter.builder("kb.qa.total")
                .description("Total number of Q&A requests")
                .tag("type", "question_answer")
                .register(meterRegistry);
        
        this.documentUploadCounter = Counter.builder("kb.document.upload.total")
                .description("Total number of document uploads")
                .tag("type", "upload")
                .register(meterRegistry);
        
        this.vectorSearchCounter = Counter.builder("kb.vector.search.total")
                .description("Total number of vector searches")
                .tag("type", "search")
                .register(meterRegistry);
        
        this.cacheHitCounter = Counter.builder("kb.cache.hit.total")
                .description("Total number of cache hits")
                .tag("type", "hit")
                .register(meterRegistry);
        
        this.cacheMissCounter = Counter.builder("kb.cache.miss.total")
                .description("Total number of cache misses")
                .tag("type", "miss")
                .register(meterRegistry);
        
        // 初始化计时器
        this.qaResponseTimer = Timer.builder("kb.qa.response.time")
                .description("Q&A response time")
                .tag("type", "response")
                .register(meterRegistry);
        
        this.documentProcessingTimer = Timer.builder("kb.document.processing.time")
                .description("Document processing time")
                .tag("type", "processing")
                .register(meterRegistry);
        
        this.vectorSearchTimer = Timer.builder("kb.vector.search.time")
                .description("Vector search time")
                .tag("type", "search")
                .register(meterRegistry);
        
        // 初始化评分分布（1-5分）
        for (int i = 1; i <= 5; i++) {
            ratingDistribution.put(i, new AtomicInteger(0));
            final int rating = i;
            meterRegistry.gauge("kb.rating.distribution", 
                    java.util.Collections.singletonList(io.micrometer.core.instrument.Tag.of("rating", String.valueOf(rating))),
                    ratingDistribution.get(rating));
        }
        
        // 注册缓存命中率Gauge
        meterRegistry.gauge("kb.cache.hit.rate", this, MetricsService::calculateCacheHitRate);
    }
    
    /**
     * 记录问答请求
     */
    public void recordQARequest() {
        qaCounter.increment();
    }
    
    /**
     * 记录问答响应时间
     */
    public Timer.Sample startQATimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordQAResponseTime(Timer.Sample sample) {
        sample.stop(qaResponseTimer);
    }
    
    /**
     * 记录文档上传
     */
    public void recordDocumentUpload() {
        documentUploadCounter.increment();
    }
    
    /**
     * 记录文档处理时间
     */
    public Timer.Sample startDocumentProcessingTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordDocumentProcessingTime(Timer.Sample sample) {
        sample.stop(documentProcessingTimer);
    }
    
    /**
     * 记录向量检索
     */
    public void recordVectorSearch() {
        vectorSearchCounter.increment();
    }
    
    /**
     * 记录向量检索时间
     */
    public Timer.Sample startVectorSearchTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordVectorSearchTime(Timer.Sample sample) {
        sample.stop(vectorSearchTimer);
    }
    
    /**
     * 记录缓存命中
     */
    public void recordCacheHit() {
        cacheHitCounter.increment();
    }
    
    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }
    
    /**
     * 记录模型使用
     */
    public void recordModelUsage(String modelName) {
        modelUsageCounters.computeIfAbsent(modelName, name -> 
            Counter.builder("kb.model.usage.total")
                    .description("Model usage count")
                    .tag("model", name)
                    .register(meterRegistry)
        ).increment();
    }
    
    /**
     * 记录评分
     */
    public void recordRating(int rating) {
        if (rating >= 1 && rating <= 5) {
            ratingDistribution.get(rating).incrementAndGet();
        }
    }
    
    /**
     * 计算缓存命中率
     */
    private double calculateCacheHitRate() {
        double hits = cacheHitCounter.count();
        double misses = cacheMissCounter.count();
        double total = hits + misses;
        return total > 0 ? (hits / total) * 100 : 0.0;
    }
    
    /**
     * 获取问答总数
     */
    public double getQATotal() {
        return qaCounter.count();
    }
    
    /**
     * 获取文档上传总数
     */
    public double getDocumentUploadTotal() {
        return documentUploadCounter.count();
    }
    
    /**
     * 获取缓存命中率
     */
    public double getCacheHitRate() {
        return calculateCacheHitRate();
    }
}
