package com.ftd.fraud_transaction_detector.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class SystemController {
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "service", "fraud-transaction-detector",
                "architecture", "modular-monolith",
                "status", "UP",
                "modules", List.of("auth", "transactions", "aml-training", "model-governance", "cases")
        );
    }
}
