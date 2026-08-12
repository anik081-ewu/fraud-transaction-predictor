package com.ftd.fraud_transaction_detector.aml.training.api;

import com.ftd.fraud_transaction_detector.aml.deployment.api.ModelDeploymentRequest;
import com.ftd.fraud_transaction_detector.aml.deployment.application.ModelDeploymentService;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.ModelDeploymentEvent;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.ActiveModelPointer;
import com.ftd.fraud_transaction_detector.aml.training.application.AmlModelRegistryService;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.validation.api.ValidateCandidateRequest;
import com.ftd.fraud_transaction_detector.aml.validation.application.ModelValidationService;
import com.ftd.fraud_transaction_detector.aml.validation.domain.ModelValidationReport;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/aml/models")
public class AmlModelRegistryController {

    private final AmlModelRegistryService registryService;
    private final ModelValidationService validationService;
    private final ModelDeploymentService deploymentService;

    public AmlModelRegistryController(
            AmlModelRegistryService registryService,
            ModelValidationService validationService,
            ModelDeploymentService deploymentService
    ) {
        this.registryService = registryService;
        this.validationService = validationService;
        this.deploymentService = deploymentService;
    }

    @GetMapping("/{modelVersion}")
    public AmlModelRegistryEntry get(@PathVariable String modelVersion) {
        return registryService.get(modelVersion);
    }

    @GetMapping
    public List<AmlModelRegistryEntry> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String modelSegment
    ) {
        return registryService.search(status, modelType, modelSegment);
    }

    @PostMapping("/{modelVersion}/validate")
    public ModelValidationReport validate(
            @PathVariable String modelVersion,
            @Valid @RequestBody(required = false) ValidateCandidateRequest request
    ) {
        return validationService.validate(modelVersion, request);
    }

    @GetMapping("/{modelVersion}/validations")
    public List<ModelValidationReport> validations(@PathVariable String modelVersion) {
        return validationService.reports(modelVersion);
    }

    @PostMapping("/{modelVersion}/promote")
    public ModelDeploymentEvent promote(
            @PathVariable String modelVersion,
            @Valid @RequestBody ModelDeploymentRequest request,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        return deploymentService.promote(modelVersion, request, authorizationHeader);
    }

    @PostMapping("/{modelVersion}/rollback")
    public ModelDeploymentEvent rollback(
            @PathVariable String modelVersion,
            @Valid @RequestBody ModelDeploymentRequest request,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        return deploymentService.rollback(modelVersion, request, authorizationHeader);
    }

    @GetMapping("/deployments")
    public List<ModelDeploymentEvent> deployments(
            @RequestParam String modelType,
            @RequestParam(required = false) String modelSegment
    ) {
        return deploymentService.history(modelType, modelSegment);
    }

    @GetMapping("/active")
    public List<ActiveModelPointer> activeModels() {
        return deploymentService.activeModels();
    }
}
