package com.ftd.fraud_transaction_detector.comparison.web;

import com.ftd.fraud_transaction_detector.comparison.dto.RiskPolicyConfigResponse;
import com.ftd.fraud_transaction_detector.comparison.dto.RiskPolicyConfigUpdateRequest;
import com.ftd.fraud_transaction_detector.comparison.service.RiskPolicyConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/anomaly-model-comparisons/risk-policy")
public class RiskPolicyConfigController {

    private final RiskPolicyConfigService riskPolicyConfigService;

    public RiskPolicyConfigController(RiskPolicyConfigService riskPolicyConfigService) {
        this.riskPolicyConfigService = riskPolicyConfigService;
    }

    @GetMapping
    public RiskPolicyConfigResponse get() {
        return riskPolicyConfigService.get();
    }

    @PutMapping
    public RiskPolicyConfigResponse update(@RequestBody RiskPolicyConfigUpdateRequest request) {
        return riskPolicyConfigService.update(request);
    }
}
