package com.ledgerline.controller;

import com.ledgerline.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> checkHealth() {
        return ResponseEntity.ok(ApiResponse.ok(
                "System is operational",
                Map.of(
                        "status", "UP",
                        "service", "LedgerLine Core Banking Engine",
                        "engine", "Double-Entry ACID Bookkeeper"
                )
        ));
    }
}
