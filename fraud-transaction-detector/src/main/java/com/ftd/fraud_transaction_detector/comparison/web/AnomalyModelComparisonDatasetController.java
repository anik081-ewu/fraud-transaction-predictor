package com.ftd.fraud_transaction_detector.comparison.web;

import com.ftd.fraud_transaction_detector.comparison.dto.DatabaseSnapshotRequest;
import com.ftd.fraud_transaction_detector.comparison.dto.DatasetPartitionResponse;
import com.ftd.fraud_transaction_detector.comparison.dto.UploadedDatasetResponse;
import com.ftd.fraud_transaction_detector.comparison.service.UploadedDatasetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/anomaly-model-comparisons/datasets")
public class AnomalyModelComparisonDatasetController {

    private final UploadedDatasetService uploadedDatasetService;

    public AnomalyModelComparisonDatasetController(UploadedDatasetService uploadedDatasetService) {
        this.uploadedDatasetService = uploadedDatasetService;
    }

    @GetMapping
    public List<UploadedDatasetResponse> listDatasets() {
        return uploadedDatasetService.listDatasets();
    }

    @PostMapping("/database-snapshot")
    public UploadedDatasetResponse createDatabaseSnapshot(@RequestBody(required = false) DatabaseSnapshotRequest request) {
        return uploadedDatasetService.createDatabaseSnapshot(request == null ? null : request.requestedBy());
    }

    @GetMapping("/{datasetId}")
    public UploadedDatasetResponse getDataset(@PathVariable Long datasetId) {
        return uploadedDatasetService.getDataset(datasetId);
    }

    @GetMapping("/{datasetId}/partitions")
    public List<DatasetPartitionResponse> listPartitions(@PathVariable Long datasetId) {
        return uploadedDatasetService.getDataset(datasetId).partitions();
    }
}
