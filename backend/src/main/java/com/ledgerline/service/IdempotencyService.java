package com.ledgerline.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.dto.CachedResponse;
import com.ledgerline.entity.IdempotencyRecord;
import com.ledgerline.entity.User;
import com.ledgerline.exception.IdempotencyException;
import com.ledgerline.repository.IdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    public String computePayloadHash(Object payload) {
        if (payload == null) {
            return "EMPTY_PAYLOAD";
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            log.error("Failed to compute SHA-256 payload hash: {}", e.getMessage());
            return String.valueOf(payload.hashCode());
        }
    }

    @Transactional(readOnly = true)
    public Optional<CachedResponse> findExistingResponse(String key, UUID userId, String requestHash) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        Optional<IdempotencyRecord> recordOpt = idempotencyRepository.findByKey(key);
        if (recordOpt.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyRecord record = recordOpt.get();

        // Security check: Key must belong to the caller
        if (!record.getUser().getId().equals(userId)) {
            throw new IdempotencyException("Idempotency key '" + key + "' was used by a different account.");
        }

        // Integrity check: Payload must match the initial request
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyException("Idempotency key '" + key + "' was already used with different request parameters.");
        }

        log.info("[IDEMPOTENCY] Replaying cached response for key: {}", key);
        return Optional.of(new CachedResponse(record.getStatusCode(), record.getResponseBody()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordResponse(String key, User user, String requestPath, String requestHash, int statusCode, Object response) {
        if (key == null || key.isBlank()) {
            return;
        }

        try {
            String responseBody = objectMapper.writeValueAsString(response);
            OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(1);

            IdempotencyRecord record = new IdempotencyRecord(
                    key,
                    user,
                    requestPath,
                    requestHash,
                    statusCode,
                    responseBody,
                    expiresAt
            );
            idempotencyRepository.save(record);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize idempotency response body: {}", e.getMessage());
        }
    }
}
