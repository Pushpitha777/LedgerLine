package com.ledgerline.security;

import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String secret = "super_secure_jwt_secret_key_minimum_256_bits_length_for_hmac_sha256";

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        props.setExpirationMs(3600000L); // 1 hour
        jwtUtil = new JwtUtil(props);
    }

    @Test
    @DisplayName("Should generate a valid token and successfully extract claims")
    void testGenerateAndExtractClaims() {
        UUID userId = UUID.randomUUID();
        UserDetails userDetails = new User(
                "alice",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"))
        );

        String token = jwtUtil.generateToken(userDetails, userId);
        assertNotNull(token);
        assertTrue(token.startsWith("ey"));

        assertEquals("alice", jwtUtil.extractUsername(token));
        assertEquals(userId, jwtUtil.extractUserId(token));
        assertEquals(List.of("ROLE_ACCOUNT_HOLDER"), jwtUtil.extractRoles(token));
        assertTrue(jwtUtil.isTokenValid(token, userDetails));
    }

    @Test
    @DisplayName("Should reject token when username does not match UserDetails")
    void testTokenInvalidForDifferentUser() {
        UUID userId = UUID.randomUUID();
        UserDetails alice = new User("alice", "pass", List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")));
        UserDetails bob = new User("bob", "pass", List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")));

        String token = jwtUtil.generateToken(alice, userId);
        assertFalse(jwtUtil.isTokenValid(token, bob));
    }

    @Test
    @DisplayName("Should reject tampered token signature")
    void testTamperedTokenRejected() {
        UUID userId = UUID.randomUUID();
        UserDetails alice = new User("alice", "pass", List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER")));
        String token = jwtUtil.generateToken(alice, userId);

        // Tamper with the payload part of the JWT
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + "tampered." + parts[2];

        assertFalse(jwtUtil.isTokenValid(tamperedToken, alice));
    }
}
