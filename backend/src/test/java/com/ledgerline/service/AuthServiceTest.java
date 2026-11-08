package com.ledgerline.service;

import com.ledgerline.dto.AuthResponse;
import com.ledgerline.dto.LoginRequest;
import com.ledgerline.dto.RegisterRequest;
import com.ledgerline.dto.UserProfileResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.Role;
import com.ledgerline.entity.RoleName;
import com.ledgerline.entity.User;
import com.ledgerline.exception.DuplicateResourceException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.RoleRepository;
import com.ledgerline.repository.UserRepository;
import com.ledgerline.security.CustomUserDetails;
import com.ledgerline.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuthService authService;

    private Role accountHolderRole;

    @BeforeEach
    void setUp() {
        accountHolderRole = new Role(RoleName.ROLE_ACCOUNT_HOLDER);
        accountHolderRole.setId(1);
    }

    @Test
    @DisplayName("Should successfully register user and provision an initial savings account")
    void testRegisterSuccess() {
        RegisterRequest request = new RegisterRequest("john_doe", "john@example.com", "SecretPass123", "John Doe");

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.ROLE_ACCOUNT_HOLDER)).thenReturn(Optional.of(accountHolderRole));
        when(passwordEncoder.encode("SecretPass123")).thenReturn("hashed_pass");

        User savedUser = new User("john_doe", "john@example.com", "hashed_pass", "John Doe");
        savedUser.setId(UUID.randomUUID());
        savedUser.setRoles(Set.of(accountHolderRole));

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);

        UserProfileResponse response = authService.register(request, "127.0.0.1");

        assertNotNull(response);
        assertEquals("john_doe", response.username());
        assertEquals("john@example.com", response.email());
        assertTrue(response.roles().contains("ROLE_ACCOUNT_HOLDER"));

        verify(accountRepository, times(1)).save(any(Account.class));
        verify(auditLogService, times(1)).recordSecurityEvent(eq("USER_REGISTERED"), eq("john_doe"), any(), any(), any());
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when username already taken")
    void testRegisterDuplicateUsernameThrowsException() {
        RegisterRequest request = new RegisterRequest("existing_user", "new@example.com", "Secret123", "Name");
        when(userRepository.existsByUsername("existing_user")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request, "127.0.0.1"));
        verify(userRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully login and return JWT token")
    void testLoginSuccess() {
        LoginRequest request = new LoginRequest("john_doe", "SecretPass123");
        User user = new User("john_doe", "john@example.com", "hashed", "John Doe");
        user.setId(UUID.randomUUID());
        user.setRoles(Set.of(accountHolderRole));
        CustomUserDetails userDetails = new CustomUserDetails(user);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        when(jwtUtil.generateToken(any(), any())).thenReturn("mock.jwt.token");

        AuthResponse response = authService.login(request, "127.0.0.1");

        assertNotNull(response);
        assertEquals("mock.jwt.token", response.token());
        assertEquals("john_doe", response.username());
        assertEquals("Bearer", response.tokenType());
        verify(auditLogService).recordSecurityEvent(eq("LOGIN_SUCCESS"), eq("john_doe"), any(), any(), any());
    }

    @Test
    @DisplayName("Should throw BadCredentialsException and record audit failure on wrong password")
    void testLoginBadCredentials() {
        LoginRequest request = new LoginRequest("john_doe", "wrong_pass");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid password"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request, "127.0.0.1"));
        verify(auditLogService).recordSecurityEvent(eq("LOGIN_FAILED"), eq("john_doe"), any(), any(), any());
    }
}
