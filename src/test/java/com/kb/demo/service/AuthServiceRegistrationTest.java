package com.kb.demo.service;

import com.kb.demo.dto.RegisterRequest;
import com.kb.demo.entity.Role;
import com.kb.demo.entity.User;
import com.kb.demo.repository.RoleRepository;
import com.kb.demo.repository.UserRepository;
import com.kb.demo.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRegistrationTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService();
        ReflectionTestUtils.setField(authService, "authenticationManager", authenticationManager);
        ReflectionTestUtils.setField(authService, "userRepository", userRepository);
        ReflectionTestUtils.setField(authService, "roleRepository", roleRepository);
        ReflectionTestUtils.setField(authService, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(authService, "tokenProvider", tokenProvider);
    }

    @Test
    void publicRegistrationAssignsGuestWithoutBusinessPermissions() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("new-user");
        request.setPassword("password123");
        request.setEmail("new-user@example.com");

        Role guest = new Role();
        guest.setName("GUEST");
        when(userRepository.existsByUsername("new-user")).thenReturn(false);
        when(userRepository.existsByEmail("new-user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(roleRepository.findByName("GUEST")).thenReturn(Optional.of(guest));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User registered = authService.register(request);

        assertThat(registered.getRoles()).containsExactly(guest);
        assertThat(registered.getAuthorities())
                .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                .containsExactly("ROLE_GUEST");
    }
}
