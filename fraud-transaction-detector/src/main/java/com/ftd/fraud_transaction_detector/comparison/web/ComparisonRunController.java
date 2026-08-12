package com.ftd.fraud_transaction_detector.comparison.web;

import com.ftd.fraud_transaction_detector.comparison.dto.ComparisonRunCreateRequest;
import com.ftd.fraud_transaction_detector.comparison.dto.ComparisonRunDetailResponse;
import com.ftd.fraud_transaction_detector.comparison.dto.ComparisonRunResponse;
import com.ftd.fraud_transaction_detector.comparison.service.ComparisonRunService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/anomaly-model-comparisons/runs")
public class ComparisonRunController {

    private final ComparisonRunService comparisonRunService;

    public ComparisonRunController(ComparisonRunService comparisonRunService) {
        this.comparisonRunService = comparisonRunService;
    }

    @PostMapping
    public ComparisonRunResponse createRun(@RequestBody ComparisonRunCreateRequest request) {
        return comparisonRunService.createAndExecute(request);
    }

    @GetMapping
    public List<ComparisonRunResponse> listRuns() {
        return comparisonRunService.listRuns();
    }

    @GetMapping("/{comparisonRunId}")
    public ComparisonRunDetailResponse getRun(@PathVariable Long comparisonRunId) {
        return comparisonRunService.getRunDetail(comparisonRunId);
    }
}
