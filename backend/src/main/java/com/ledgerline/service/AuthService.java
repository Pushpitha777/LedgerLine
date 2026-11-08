package com.ledgerline.service;

import com.ledgerline.dto.AuthResponse;
import com.ledgerline.dto.LoginRequest;
import com.ledgerline.dto.RegisterRequest;
import com.ledgerline.dto.UserProfileResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AuditStatus;
import com.ledgerline.entity.Role;
import com.ledgerline.entity.RoleName;
import com.ledgerline.entity.User;
import com.ledgerline.exception.DuplicateResourceException;
import com.ledgerline.exception.ResourceNotFoundException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.RoleRepository;
import com.ledgerline.repository.UserRepository;
import com.ledgerline.security.CustomUserDetails;
import com.ledgerline.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            AuthenticationManager authenticationManager,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public UserProfileResponse register(RegisterRequest request, String ipAddress) {
        if (userRepository.existsByUsername(request.username())) {
            auditLogService.recordSecurityEvent("REGISTER_ATTEMPT_DUPLICATE_USERNAME", request.username(), ipAddress,
                    "Registration rejected: Username already taken", AuditStatus.FAILURE);
            throw new DuplicateResourceException("Username '" + request.username() + "' is already in use.");
        }

        if (userRepository.existsByEmail(request.email())) {
            auditLogService.recordSecurityEvent("REGISTER_ATTEMPT_DUPLICATE_EMAIL", request.email(), ipAddress,
                    "Registration rejected: Email already registered", AuditStatus.FAILURE);
            throw new DuplicateResourceException("Email '" + request.email() + "' is already registered.");
        }

        Role accountHolderRole = roleRepository.findByName(RoleName.ROLE_ACCOUNT_HOLDER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_ACCOUNT_HOLDER)));

        User user = new User(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName()
        );
        user.setRoles(Set.of(accountHolderRole));
        User savedUser = userRepository.save(user);

        // Auto-provision initial savings account with zero balance
        String generatedAccountNumber = generateUniqueAccountNumber();
        Account initialAccount = new Account(generatedAccountNumber, savedUser, "SAVINGS", 0L);
        accountRepository.save(initialAccount);

        auditLogService.recordSecurityEvent("USER_REGISTERED", savedUser.getUsername(), ipAddress,
                "New account holder registered with account number: " + generatedAccountNumber, AuditStatus.SUCCESS);

        return mapToUserProfileResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            String token = jwtUtil.generateToken(userDetails, user.getId());

            auditLogService.recordSecurityEvent("LOGIN_SUCCESS", user.getUsername(), ipAddress,
                    "User authenticated successfully", AuditStatus.SUCCESS);

            Set<String> roles = user.getRoles().stream()
                    .map(role -> role.getName().name())
                    .collect(Collectors.toSet());

            return AuthResponse.of(
                    token,
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFullName(),
                    roles
            );
        } catch (BadCredentialsException ex) {
            auditLogService.recordSecurityEvent("LOGIN_FAILED", request.username(), ipAddress,
                    "Authentication failed: Invalid credentials", AuditStatus.FAILURE);
            throw new BadCredentialsException("Invalid username or password");
        }
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return mapToUserProfileResponse(user);
    }

    private UserProfileResponse mapToUserProfileResponse(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toSet());

        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roles,
                user.getCreatedAt()
        );
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            long randomNum = 1000000000L + (long) (secureRandom.nextDouble() * 9000000000L);
            accountNumber = "LL" + randomNum;
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}
