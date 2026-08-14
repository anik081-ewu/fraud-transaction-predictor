package com.ftd.fraud_transaction_detector.aml.training.application;

import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlTrainingRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TrainingPipelineJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(TrainingPipelineJobLauncher.class);

    private final TrainingDatasetExportService exportService;
    private final ProductionCandidateTrainingService candidateTrainingService;
    private final AmlTrainingRunRepository runRepository;

    public TrainingPipelineJobLauncher(
            TrainingDatasetExportService exportService,
            ProductionCandidateTrainingService candidateTrainingService,
            AmlTrainingRunRepository runRepository
    ) {
        this.exportService = exportService;
        this.candidateTrainingService = candidateTrainingService;
        this.runRepository = runRepository;
    }

    // Runs entirely on an async thread: export → train every model → mark complete.
    // Progress is reported in models trained, and COMPLETED is only set once every
    // model has landed, so a partially trained pipeline never reads as finished.
    @Async
    public void exportThenTrain(UUID snapshotRunId, String requestedBy, List<String> selectedModels) {
        try {
            exportService.export(snapshotRunId);
        } catch (Exception e) {
            log.error("Dataset export failed for run {}: {}", snapshotRunId, e.getMessage());
            return; // run is already marked FAILED by exportService
        }

        int trained = 0;
        int totalModels = 0;
        try {
            runRepository.updateProgress(snapshotRunId, "TRAINING", 0, 0);
            var response = candidateTrainingService.start(snapshotRunId, selectedModels, requestedBy);
            totalModels = response.trainedModels().size();
            trained = totalModels;
            runRepository.updateProgress(snapshotRunId, "TRAINING", trained, totalModels);
        } catch (Exception e) {
            log.error("Training failed for run {}: {}", snapshotRunId, e.getMessage(), e);
        }

        // Three distinct outcomes: everything trained, some models missing, or training never
        // produced anything at all. The last is a failure, not a partial result, and must not
        // be reported as one.
        if (totalModels == 0) {
            runRepository.updateProgress(snapshotRunId, "TRAINING_FAILED", 0, 0);
            log.error("Training produced no models for run {} — see the error above", snapshotRunId);
        } else if (trained == totalModels) {
            runRepository.updateProgress(snapshotRunId, "COMPLETED", trained, totalModels);
            log.info("Pipeline complete for run {}: {} models trained", snapshotRunId, trained);
        } else {
            runRepository.updateProgress(snapshotRunId, "PARTIAL", trained, totalModels);
            log.warn("Pipeline incomplete for run {}: {}/{} models trained", snapshotRunId, trained, totalModels);
        }
    }
}
