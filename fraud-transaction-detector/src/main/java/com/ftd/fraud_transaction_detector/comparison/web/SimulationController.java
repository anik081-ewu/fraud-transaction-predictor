package com.ftd.fraud_transaction_detector.comparison.web;

import com.ftd.fraud_transaction_detector.comparison.dto.SimulationRequest;
import com.ftd.fraud_transaction_detector.comparison.dto.SimulationResponse;
import com.ftd.fraud_transaction_detector.comparison.service.SimulationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/anomaly-model-comparisons/simulations")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping
    public SimulationResponse simulate(@RequestBody SimulationRequest request) {
        return simulationService.simulate(request);
    }
}
