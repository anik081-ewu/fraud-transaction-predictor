package com.ftd.fraud_transaction_detector.comparison.web;

import com.ftd.fraud_transaction_detector.comparison.dto.ColdStartConfigItemResponse;
import com.ftd.fraud_transaction_detector.comparison.dto.ColdStartConfigUpdateRequest;
import com.ftd.fraud_transaction_detector.comparison.service.ColdStartConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({
        "/api/v1/anomaly-model-comparisons/settings",
        "/api/v1/anomaly-model-comparisons/cold-start-config"
})
public class ColdStartConfigController {

    private final ColdStartConfigService coldStartConfigService;

    public ColdStartConfigController(ColdStartConfigService coldStartConfigService) {
        this.coldStartConfigService = coldStartConfigService;
    }

    @GetMapping
    public List<ColdStartConfigItemResponse> list() {
        return coldStartConfigService.list();
    }

    @PutMapping
    public List<ColdStartConfigItemResponse> update(@RequestBody ColdStartConfigUpdateRequest request) {
        return coldStartConfigService.update(request);
    }
}
