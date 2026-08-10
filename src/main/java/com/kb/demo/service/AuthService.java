package com.kb.demo.service;

import com.kb.demo.dto.JwtResponse;
import com.kb.demo.dto.LoginRequest;
import com.kb.demo.dto.RegisterRequest;
import com.kb.demo.entity.Role;
import com.kb.demo.entity.User;
import com.kb.demo.repository.RoleRepository;
import com.kb.demo.repository.UserRepository;
import com.kb.demo.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 认证服务
 */
@Service
public class AuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtTokenProvider tokenProvider;
    
    @Value("${jwt.expiration:86400000}")
    private Long jwtExpiration;
    
    /**
     * 用户登录
     */
    @Transactional
    public JwtResponse login(LoginRequest loginRequest) {
        // 认证用户
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getUsername(),
                loginRequest.getPassword()
            )
        );
        
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        
        // 生成 Token
        String accessToken = tokenProvider.generateToken(userDetails);
        String refreshToken = tokenProvider.generateRefreshToken(userDetails);
        
        // 更新最后登录时间
        User user = userRepository.findByUsername(loginRequest.getUsername())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        logger.info("User logged in: {}", loginRequest.getUsername());
        
        return new JwtResponse(
            accessToken,
            refreshToken,
            jwtExpiration,
            user.getUsername(),
            user.getEmail()
        );
    }
    
    /**
     * 用户注册
     */
    @Transactional
    public User register(RegisterRequest registerRequest) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("邮箱已存在");
        }
        
        // 创建用户
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setEmail(registerRequest.getEmail());
        user.setPhone(registerRequest.getPhone());
        user.setNickname(registerRequest.getNickname());
        
        // 公开注册只能获得无业务权限的 GUEST，业务角色必须由管理员审批授予。
        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByName("GUEST")
            .orElseThrow(() -> new RuntimeException("角色不存在"));
        roles.add(userRole);
        user.setRoles(roles);
        
        User savedUser = userRepository.save(user);
        logger.info("New user registered: {}", savedUser.getUsername());
        
        return savedUser;
    }
    
    /**
     * 刷新 Token
     */
    public JwtResponse refreshToken(String refreshToken) {
        String username = tokenProvider.extractUsername(refreshToken);
        UserDetails userDetails = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        if (!tokenProvider.validateToken(refreshToken, userDetails)) {
            throw new RuntimeException("无效的刷新令牌");
        }
        
        String newAccessToken = tokenProvider.generateToken(userDetails);
        String newRefreshToken = tokenProvider.generateRefreshToken(userDetails);
        
        User user = (User) userDetails;
        return new JwtResponse(
            newAccessToken,
            newRefreshToken,
            jwtExpiration,
            user.getUsername(),
            user.getEmail()
        );
    }
}
