package com.ledgerline.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.dto.CachedResponse;
import com.ledgerline.dto.DepositRequest;
import com.ledgerline.entity.IdempotencyRecord;
import com.ledgerline.entity.User;
import com.ledgerline.exception.IdempotencyException;
import com.ledgerline.repository.IdempotencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRepository idempotencyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private IdempotencyService idempotencyService;

    private User user;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(idempotencyRepository, objectMapper);
        user = new User("alice", "alice@example.com", "hash", "Alice");
        user.setId(userId);
    }

    @Test
    @DisplayName("Should return cached response when replayed with identical payload and key")
    void testReplayCachedResponse() {
        DepositRequest req = new DepositRequest(UUID.randomUUID(), new BigDecimal("100.00"), "Test");
        String hash = idempotencyService.computePayloadHash(req);

        IdempotencyRecord record = new IdempotencyRecord(
                "key-123", user, "/api/transactions/deposit", hash, 200, "{\"success\":true}", OffsetDateTime.now().plusDays(1)
        );

        when(idempotencyRepository.findByKey("key-123")).thenReturn(Optional.of(record));

        Optional<CachedResponse> cached = idempotencyService.findExistingResponse("key-123", userId, hash);
        assertTrue(cached.isPresent());
        assertEquals(200, cached.get().statusCode());
        assertEquals("{\"success\":true}", cached.get().responseBody());
    }

    @Test
    @DisplayName("Should throw IdempotencyException when key is re-used with differing payload parameters")
    void testDifferingPayloadThrowsException() {
        DepositRequest req1 = new DepositRequest(UUID.randomUUID(), new BigDecimal("100.00"), "First");
        String hash1 = idempotencyService.computePayloadHash(req1);

        DepositRequest req2 = new DepositRequest(UUID.randomUUID(), new BigDecimal("500.00"), "Tampered");
        String hash2 = idempotencyService.computePayloadHash(req2);

        IdempotencyRecord record = new IdempotencyRecord(
                "key-123", user, "/api/transactions/deposit", hash1, 200, "{\"success\":true}", OffsetDateTime.now().plusDays(1)
        );

        when(idempotencyRepository.findByKey("key-123")).thenReturn(Optional.of(record));

        assertThrows(IdempotencyException.class, () ->
                idempotencyService.findExistingResponse("key-123", userId, hash2)
        );
    }
}
