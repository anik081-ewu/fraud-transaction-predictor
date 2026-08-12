package com.ftd.fraud_transaction_detector.comparison.web;

import com.ftd.fraud_transaction_detector.comparison.dto.*;
import com.ftd.fraud_transaction_detector.comparison.service.ScenarioLibraryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/anomaly-model-comparisons/scenario-sets")
public class ScenarioLibraryController {

    private final ScenarioLibraryService scenarioLibraryService;

    public ScenarioLibraryController(ScenarioLibraryService scenarioLibraryService) {
        this.scenarioLibraryService = scenarioLibraryService;
    }

    @PostMapping
    public ScenarioSetResponse createScenarioSet(@RequestBody ScenarioSetCreateRequest request) {
        return scenarioLibraryService.createScenarioSet(request);
    }

    @GetMapping
    public List<ScenarioSetResponse> listScenarioSets() {
        return scenarioLibraryService.listScenarioSets();
    }

    @PostMapping("/{scenarioSetId}/scenarios")
    public ComparisonScenarioResponse createScenario(
            @PathVariable Long scenarioSetId,
            @RequestBody ComparisonScenarioCreateRequest request
    ) {
        return scenarioLibraryService.createScenario(scenarioSetId, request);
    }

    @GetMapping("/{scenarioSetId}/scenarios")
    public List<ComparisonScenarioResponse> listScenarios(@PathVariable Long scenarioSetId) {
        return scenarioLibraryService.listScenarios(scenarioSetId);
    }
}
