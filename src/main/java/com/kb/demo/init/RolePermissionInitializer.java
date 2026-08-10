package com.kb.demo.init;

import com.kb.demo.entity.Permission;
import com.kb.demo.entity.Role;
import com.kb.demo.entity.User;
import com.kb.demo.repository.PermissionRepository;
import com.kb.demo.repository.RoleRepository;
import com.kb.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * 初始化角色和权限
 */
@Component
public class RolePermissionInitializer implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(RolePermissionInitializer.class);
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private PermissionRepository permissionRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // 初始化权限
        Permission documentRead = createPermissionIfNotExists("document:read", "读取文档");
        Permission documentWrite = createPermissionIfNotExists("document:write", "编辑文档");
        Permission documentDelete = createPermissionIfNotExists("document:delete", "删除文档");
        Permission qaAsk = createPermissionIfNotExists("qa:ask", "提问");
        Permission dashboardView = createPermissionIfNotExists("dashboard:view", "查看数据面板");
        Permission adminManage = createPermissionIfNotExists("admin:manage", "管理员功能");
        
        // 初始化角色
        // ADMIN 角色（所有权限）
        Set<Permission> adminPermissions = new HashSet<>();
        adminPermissions.add(documentRead);
        adminPermissions.add(documentWrite);
        adminPermissions.add(documentDelete);
        adminPermissions.add(qaAsk);
        adminPermissions.add(dashboardView);
        adminPermissions.add(adminManage);
        createRoleIfNotExists("ADMIN", "管理员", adminPermissions);
        
        // USER 角色（常规权限）
        Set<Permission> userPermissions = new HashSet<>();
        userPermissions.add(documentRead);
        userPermissions.add(documentWrite);
        userPermissions.add(qaAsk);
        userPermissions.add(dashboardView);
        createRoleIfNotExists("USER", "普通用户", userPermissions);
        
        // GUEST 角色（默认无业务权限）
        Set<Permission> guestPermissions = new HashSet<>();
        createRoleIfNotExists("GUEST", "访客", guestPermissions);
        
        // 创建默认管理员账户
        createDefaultAdminIfNotExists();
        
        logger.info("角色和权限初始化完成");
    }
    
    private Permission createPermissionIfNotExists(String name, String description) {
        return permissionRepository.findByName(name)
            .orElseGet(() -> {
                Permission permission = new Permission();
                permission.setName(name);
                permission.setDescription(description);
                Permission saved = permissionRepository.save(permission);
                logger.info("创建权限: {}", name);
                return saved;
            });
    }
    
    private Role createRoleIfNotExists(String name, String description, Set<Permission> permissions) {
        Role role = roleRepository.findByName(name).orElseGet(() -> {
            Role newRole = new Role();
            newRole.setName(name);
            logger.info("创建角色: {}", name);
            return newRole;
        });

        role.setDescription(description);
        role.setPermissions(new HashSet<>(permissions));
        return roleRepository.save(role);
    }
    
    private void createDefaultAdminIfNotExists() {
        String adminUsername = "admin";
        if (!userRepository.existsByUsername(adminUsername)) {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEmail("admin@example.com");
            admin.setNickname("系统管理员");
            
            Set<Role> roles = new HashSet<>();
            roleRepository.findByName("ADMIN").ifPresent(roles::add);
            admin.setRoles(roles);
            
            userRepository.save(admin);
            logger.info("创建默认管理员账户: {}", adminUsername);
        }
    }
}
