package com.kb.demo.controller;

import com.kb.demo.dto.JwtResponse;
import com.kb.demo.dto.LoginRequest;
import com.kb.demo.dto.RegisterRequest;
import com.kb.demo.entity.User;
import com.kb.demo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private AuthService authService;
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        JwtResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        User user = authService.register(registerRequest);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "注册成功");
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 刷新 Token
     */
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        JwtResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 测试接口（公开）
     */
    @GetMapping("/test/public")
    public ResponseEntity<Map<String, String>> publicTest() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Public endpoint accessible without authentication");
        return ResponseEntity.ok(response);
    }
}
