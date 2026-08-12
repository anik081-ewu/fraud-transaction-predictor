package com.ftd.fraud_transaction_detector.comparison.web;

import com.ftd.fraud_transaction_detector.comparison.dto.ModelVersionResponse;
import com.ftd.fraud_transaction_detector.comparison.dto.PartitionTrainingRequest;
import com.ftd.fraud_transaction_detector.comparison.service.PartitionTrainingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/anomaly-model-comparisons/partitions")
public class PartitionTrainingController {

    private final PartitionTrainingService partitionTrainingService;

    public PartitionTrainingController(PartitionTrainingService partitionTrainingService) {
        this.partitionTrainingService = partitionTrainingService;
    }

    @PostMapping("/{partitionId}/train")
    public List<ModelVersionResponse> trainPartition(
            @PathVariable Long partitionId,
            @RequestBody(required = false) PartitionTrainingRequest request
    ) {
        return partitionTrainingService.trainPartition(partitionId, request);
    }

    @GetMapping("/{partitionId}/model-versions")
    public List<ModelVersionResponse> listModelVersions(@PathVariable Long partitionId) {
        return partitionTrainingService.listModelVersions(partitionId);
    }
}
