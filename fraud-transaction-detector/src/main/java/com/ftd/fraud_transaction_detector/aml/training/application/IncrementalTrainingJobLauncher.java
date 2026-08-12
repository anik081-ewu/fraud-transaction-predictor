package com.ftd.fraud_transaction_detector.aml.training.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class IncrementalTrainingJobLauncher {

    private final IncrementalTrainingService trainingService;

    public IncrementalTrainingJobLauncher(IncrementalTrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @Async
    public void train(UUID trainingRunId, String requestedBy) {
        trainingService.train(trainingRunId, requestedBy);
    }

    @Async
    public void trainSequentially(List<UUID> trainingRunIds, String requestedBy) {
        for (UUID trainingRunId : trainingRunIds) {
            try {
                trainingService.train(trainingRunId, requestedBy);
            } catch (Exception ignored) {
            }
        }
    }
}
