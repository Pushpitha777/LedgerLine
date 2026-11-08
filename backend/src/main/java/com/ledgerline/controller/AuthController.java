package com.ledgerline.controller;

import com.ledgerline.dto.ApiResponse;
import com.ledgerline.dto.AuthResponse;
import com.ledgerline.dto.LoginRequest;
import com.ledgerline.dto.RegisterRequest;
import com.ledgerline.dto.UserProfileResponse;
import com.ledgerline.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {

        String clientIp = extractClientIp(servletRequest);
        UserProfileResponse profile = authService.register(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Account registered successfully. Default savings account opened.", profile));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {

        String clientIp = extractClientIp(servletRequest);
        AuthResponse response = authService.login(request, clientIp);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            throw new org.springframework.security.authentication.BadCredentialsException("Authentication required");
        }
        UserProfileResponse profile = authService.getProfile(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Profile retrieved successfully", profile));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
