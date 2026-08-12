package com.ftd.fraud_transaction_detector.aml.research.api;

import com.ftd.fraud_transaction_detector.aml.research.application.GrowthAnalysisService;
import com.ftd.fraud_transaction_detector.aml.research.application.LayerAblationService;
import com.ftd.fraud_transaction_detector.aml.research.client.GrowthAnalysisResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/aml/growth-analysis")
public class GrowthAnalysisController {

    private final GrowthAnalysisService service;
    private final LayerAblationService ablationService;

    public GrowthAnalysisController(GrowthAnalysisService service, LayerAblationService ablationService) {
        this.service = service;
        this.ablationService = ablationService;
    }

    @PostMapping("/training-runs/{trainingRunId}")
    public GrowthAnalysisResponse analyze(
            @PathVariable UUID trainingRunId,
            @Valid @RequestBody(required = false) RunGrowthAnalysisRequest request
    ) {
        return service.analyze(trainingRunId, request);
    }

    @PostMapping("/layer-ablation")
    public LayerAblationReport layerAblation() {
        return ablationService.analyze();
    }
}
