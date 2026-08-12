package com.ftd.fraud_transaction_detector.aml.validation.api;

import com.ftd.fraud_transaction_detector.aml.validation.application.LayeredShadowValidationService;
import com.ftd.fraud_transaction_detector.aml.validation.domain.LayeredShadowValidationReport;
import com.ftd.fraud_transaction_detector.aml.validation.domain.SyntheticScenarioLabel;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/aml/layered-shadow")
public class LayeredShadowValidationController {

    private final LayeredShadowValidationService service;

    public LayeredShadowValidationController(LayeredShadowValidationService service) {
        this.service = service;
    }

    @PostMapping("/validate")
    public LayeredShadowValidationReport validate(
            @RequestBody(required = false) ValidateLayeredShadowRequest request
    ) {
        return service.validate(request);
    }

    @GetMapping("/validations")
    public List<LayeredShadowValidationReport> reports() {
        return service.reports();
    }

    @PostMapping("/scenario-labels")
    public SyntheticScenarioLabel label(@Valid @RequestBody SyntheticScenarioLabelRequest request) {
        return service.label(request);
    }
}
