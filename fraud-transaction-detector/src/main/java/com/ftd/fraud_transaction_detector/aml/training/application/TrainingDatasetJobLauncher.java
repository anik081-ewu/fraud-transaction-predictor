package com.ftd.fraud_transaction_detector.aml.training.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TrainingDatasetJobLauncher {

    private final TrainingDatasetExportService exportService;

    public TrainingDatasetJobLauncher(TrainingDatasetExportService exportService) {
        this.exportService = exportService;
    }

    @Async
    public void export(UUID trainingRunId) {
        exportService.export(trainingRunId);
    }
}
