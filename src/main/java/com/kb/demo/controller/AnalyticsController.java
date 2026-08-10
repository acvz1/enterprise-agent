package com.kb.demo.controller;

import com.kb.demo.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Dashboard 统计数据控制器
 */
@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")  // 使用 originPatterns 而不是 origins
public class AnalyticsController {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);
    
    @Autowired
    private AnalyticsService analyticsService;
    
    /**
     * 获取Dashboard统计数据
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        try {
            logger.info("接收Dashboard统计数据请求");
            Map<String, Object> stats = analyticsService.getDashboardStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("获取Dashboard统计数据失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 重置缓存统计
     */
    @PostMapping("/cache/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resetCacheStats() {
        try {
            logger.info("接收重置缓存统计请求");
            analyticsService.resetCacheStats();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("重置缓存统计失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
