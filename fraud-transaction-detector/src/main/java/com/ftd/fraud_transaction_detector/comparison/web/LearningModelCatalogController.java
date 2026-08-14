package com.ftd.fraud_transaction_detector.comparison.web;

import com.ftd.fraud_transaction_detector.comparison.dto.LearningModelCatalogResponse;
import com.ftd.fraud_transaction_detector.comparison.service.LearningModelCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/anomaly-model-comparisons/model-catalog")
public class LearningModelCatalogController {

    private final LearningModelCatalogService service;

    public LearningModelCatalogController(LearningModelCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<LearningModelCatalogResponse> catalog() {
        return service.catalog();
    }
}
