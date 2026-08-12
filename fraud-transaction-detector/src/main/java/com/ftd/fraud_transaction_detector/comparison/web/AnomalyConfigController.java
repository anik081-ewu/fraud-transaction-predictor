package com.ftd.fraud_transaction_detector.comparison.web;

import com.ftd.fraud_transaction_detector.comparison.dto.AnomalyConfigRequest;
import com.ftd.fraud_transaction_detector.comparison.dto.AnomalyConfigResponse;
import com.ftd.fraud_transaction_detector.comparison.service.AnomalyConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/anomaly-model-comparisons/configs")
public class AnomalyConfigController {

    private final AnomalyConfigService anomalyConfigService;

    public AnomalyConfigController(AnomalyConfigService anomalyConfigService) {
        this.anomalyConfigService = anomalyConfigService;
    }

    @GetMapping
    public List<AnomalyConfigResponse> listConfigs() {
        return anomalyConfigService.listConfigs();
    }

    @GetMapping("/active")
    public AnomalyConfigResponse getActiveConfig() {
        return anomalyConfigService.getActiveConfig();
    }

    @PostMapping
    public AnomalyConfigResponse saveConfig(@RequestBody AnomalyConfigRequest request) {
        return anomalyConfigService.saveConfig(request);
    }
}
