package com.kb.demo.init;

import com.kb.demo.entity.Permission;
import com.kb.demo.entity.Role;
import com.kb.demo.repository.PermissionRepository;
import com.kb.demo.repository.RoleRepository;
import com.kb.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionInitializerTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private RolePermissionInitializer initializer;

    @Test
    void synchronizesExistingRolesAndClearsGuestBusinessPermissions() throws Exception {
        List<String> permissionNames = List.of(
                "document:read",
                "document:write",
                "document:delete",
                "qa:ask",
                "dashboard:view",
                "admin:manage");
        for (String permissionName : permissionNames) {
            when(permissionRepository.findByName(permissionName))
                    .thenReturn(Optional.of(permission(permissionName)));
        }

        Role admin = role("ADMIN");
        Role user = role("USER");
        Role guest = role("GUEST");
        guest.setPermissions(new HashSet<>(Set.of(permission("document:read"))));

        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(admin));
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(user));
        when(roleRepository.findByName("GUEST")).thenReturn(Optional.of(guest));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.existsByUsername("admin")).thenReturn(true);

        initializer.run();

        ArgumentCaptor<Role> savedRoles = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, org.mockito.Mockito.times(3)).save(savedRoles.capture());
        Role savedGuest = savedRoles.getAllValues().stream()
                .filter(role -> "GUEST".equals(role.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(savedGuest.getPermissions()).isEmpty();
        assertThat(user.getPermissions())
                .extracting(Permission::getName)
                .containsExactlyInAnyOrder(
                        "document:read", "document:write", "qa:ask", "dashboard:view");
        assertThat(admin.getPermissions())
                .extracting(Permission::getName)
                .containsExactlyInAnyOrderElementsOf(permissionNames);
    }

    private Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }

    private Permission permission(String name) {
        Permission permission = new Permission();
        permission.setName(name);
        return permission;
    }
}
