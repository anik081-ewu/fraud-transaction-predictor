package com.ftd.fraud_transaction_detector.aml.training.application;

import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
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
    private final IncrementalTrainingService incrementalTrainingService;
    private final AmlTrainingRunRepository runRepository;

    public TrainingPipelineJobLauncher(
            TrainingDatasetExportService exportService,
            ProductionCandidateTrainingService candidateTrainingService,
            IncrementalTrainingService incrementalTrainingService,
            AmlTrainingRunRepository runRepository
    ) {
        this.exportService = exportService;
        this.candidateTrainingService = candidateTrainingService;
        this.incrementalTrainingService = incrementalTrainingService;
        this.runRepository = runRepository;
    }

    // Runs entirely on an async thread: export → train every model → mark complete.
    // Progress is reported in models trained, and COMPLETED is only set once every
    // model has landed, so a partially trained pipeline never reads as finished.
    @Async
    public void exportThenTrain(UUID snapshotRunId, String requestedBy) {
        try {
            exportService.export(snapshotRunId);
        } catch (Exception e) {
            log.error("Dataset export failed for run {}: {}", snapshotRunId, e.getMessage());
            return; // run is already marked FAILED by exportService
        }

        int trained = 0;
        int totalModels = 0;
        try {
            // Batch models train synchronously inside start(); mark the stage first so the
            // bar does not sit at "dataset ready" while Python is already working.
            runRepository.updateProgress(snapshotRunId, "TRAINING", 0, 0);
            var response = candidateTrainingService.start(snapshotRunId, null, requestedBy);

            List<UUID> incrementalIds = response.trainingRuns().stream()
                    .map(AmlTrainingRun::trainingRunId)
                    .toList();
            totalModels = response.batchModels().size() + incrementalIds.size();

            // start() only returns after batch training succeeded, so those are done
            trained = response.batchModels().size();
            runRepository.updateProgress(snapshotRunId, "TRAINING", trained, totalModels);

            // Train incrementals here rather than firing another async job, so this thread
            // can observe each completion and advance the bar.
            for (UUID incrementalId : incrementalIds) {
                try {
                    incrementalTrainingService.train(incrementalId, requestedBy);
                    trained++;
                } catch (Exception e) {
                    log.error("Incremental training failed for run {}: {}", incrementalId, e.getMessage());
                }
                runRepository.updateProgress(snapshotRunId, "TRAINING", trained, totalModels);
            }
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
