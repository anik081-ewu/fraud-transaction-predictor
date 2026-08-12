package com.ftd.fraud_transaction_detector.comparison.web;

import com.ftd.fraud_transaction_detector.comparison.dto.ModelVersionResponse;
import com.ftd.fraud_transaction_detector.comparison.service.ModelVersionLifecycleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/anomaly-model-comparisons/model-versions")
public class ModelVersionLifecycleController {

    private final ModelVersionLifecycleService modelVersionLifecycleService;

    public ModelVersionLifecycleController(ModelVersionLifecycleService modelVersionLifecycleService) {
        this.modelVersionLifecycleService = modelVersionLifecycleService;
    }

    @PostMapping("/training-runs/{trainingRunId}/promote")
    public List<ModelVersionResponse> promoteTrainingRun(
            @PathVariable Long trainingRunId,
            @RequestParam(required = false) String promotedBy
    ) {
        return modelVersionLifecycleService.promoteTrainingRun(trainingRunId, promotedBy);
    }
}
