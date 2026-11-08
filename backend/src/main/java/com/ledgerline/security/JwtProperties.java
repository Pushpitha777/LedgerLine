package com.ledgerline.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ledgerline.security.jwt")
public class JwtProperties {

    private String secret = "c3VwZXJfc2VjdXJlX2Jhbmtpbmdfand0X3NlY3JldF9rZXlfbGVkZ2VybGluZV8yMDI2X2FwaV92YWxpZGF0aW9u";
    private long expirationMs = 86400000L; // 24 hours

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
