package com.ftd.fraud_transaction_detector.comparison.web;

import com.ftd.fraud_transaction_detector.comparison.dto.ModelTuningItemResponse;
import com.ftd.fraud_transaction_detector.comparison.dto.ModelTuningUpdateRequest;
import com.ftd.fraud_transaction_detector.comparison.service.ModelTuningConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/anomaly-model-comparisons/model-tuning")
public class ModelTuningConfigController {

    private final ModelTuningConfigService modelTuningConfigService;

    public ModelTuningConfigController(ModelTuningConfigService modelTuningConfigService) {
        this.modelTuningConfigService = modelTuningConfigService;
    }

    @GetMapping
    public List<ModelTuningItemResponse> list() {
        return modelTuningConfigService.list();
    }

    @PutMapping
    public List<ModelTuningItemResponse> update(@RequestBody ModelTuningUpdateRequest request) {
        return modelTuningConfigService.update(request);
    }
}
