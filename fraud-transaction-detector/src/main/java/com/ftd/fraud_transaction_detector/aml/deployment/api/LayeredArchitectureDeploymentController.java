package com.ftd.fraud_transaction_detector.aml.deployment.api;

import com.ftd.fraud_transaction_detector.aml.deployment.application.LayeredArchitectureDeploymentService;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredDeploymentEvent;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredDeploymentPointer;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/aml/layered-deployments")
public class LayeredArchitectureDeploymentController {

    private final LayeredArchitectureDeploymentService service;

    public LayeredArchitectureDeploymentController(LayeredArchitectureDeploymentService service) {
        this.service = service;
    }

    @PostMapping("/promote")
    public LayeredDeploymentEvent promote(
            @Valid @RequestBody PromoteLayeredArchitectureRequest request,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        return service.promote(request, authorizationHeader);
    }

    @PostMapping("/rollback")
    public LayeredDeploymentEvent rollback(
            @Valid @RequestBody RollbackLayeredArchitectureRequest request,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        return service.rollback(request, authorizationHeader);
    }

    @GetMapping("/active")
    public List<LayeredDeploymentPointer> active() {
        return service.pointers();
    }

    @GetMapping("/history")
    public List<LayeredDeploymentEvent> history(
            @RequestParam(required = false) String peerGroupCode
    ) {
        return service.history(peerGroupCode);
    }
}
