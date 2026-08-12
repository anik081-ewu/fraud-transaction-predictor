package com.ftd.fraud_transaction_detector.aml.training.api;

import com.ftd.fraud_transaction_detector.aml.training.application.TrainingDatasetExportService;
import com.ftd.fraud_transaction_detector.aml.training.application.TrainingDatasetJobLauncher;
import com.ftd.fraud_transaction_detector.aml.training.application.TrainingPipelineJobLauncher;
import com.ftd.fraud_transaction_detector.aml.training.application.AmlModelRegistryService;
import com.ftd.fraud_transaction_detector.aml.training.application.IncrementalTrainingJobLauncher;
import com.ftd.fraud_transaction_detector.aml.training.application.IncrementalTrainingService;
import com.ftd.fraud_transaction_detector.aml.training.application.ProductionCandidateTrainingService;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/aml/training-runs")
public class TrainingDatasetController {

    private final TrainingDatasetExportService exportService;
    private final TrainingDatasetJobLauncher jobLauncher;
    private final TrainingPipelineJobLauncher pipelineJobLauncher;
    private final AmlModelRegistryService registryService;
    private final IncrementalTrainingService incrementalTrainingService;
    private final IncrementalTrainingJobLauncher incrementalTrainingJobLauncher;
    private final ProductionCandidateTrainingService productionCandidateTrainingService;

    public TrainingDatasetController(
            TrainingDatasetExportService exportService,
            TrainingDatasetJobLauncher jobLauncher,
            TrainingPipelineJobLauncher pipelineJobLauncher,
            AmlModelRegistryService registryService,
            IncrementalTrainingService incrementalTrainingService,
            IncrementalTrainingJobLauncher incrementalTrainingJobLauncher,
            ProductionCandidateTrainingService productionCandidateTrainingService
    ) {
        this.exportService = exportService;
        this.jobLauncher = jobLauncher;
        this.pipelineJobLauncher = pipelineJobLauncher;
        this.registryService = registryService;
        this.incrementalTrainingService = incrementalTrainingService;
        this.incrementalTrainingJobLauncher = incrementalTrainingJobLauncher;
        this.productionCandidateTrainingService = productionCandidateTrainingService;
    }

    @PostMapping
    public ResponseEntity<AmlTrainingRun> create(@Valid @RequestBody CreateTrainingRunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exportService.createRun(request));
    }

    // Combined endpoint: create run → queue export → auto-train all models
    @PostMapping("/pipeline/start")
    public ResponseEntity<AmlTrainingRun> startPipeline(
            @Valid @RequestBody RunTrainingPipelineRequest request
    ) {
        AmlTrainingRun run = exportService.createRun(request.toCreateRequest());
        exportService.queue(run.trainingRunId());
        pipelineJobLauncher.exportThenTrain(run.trainingRunId(), request.requestedBy());
        return ResponseEntity.accepted().body(run);
    }

    @GetMapping("/{trainingRunId}")
    public AmlTrainingRun get(@PathVariable UUID trainingRunId) {
        return exportService.getRun(trainingRunId);
    }

    @GetMapping
    public List<AmlTrainingRun> list() {
        return exportService.listRuns();
    }

    @PostMapping("/{trainingRunId}/dataset")
    public ResponseEntity<AmlTrainingRun> generateDataset(@PathVariable UUID trainingRunId) {
        AmlTrainingRun queued = exportService.queue(trainingRunId);
        jobLauncher.export(trainingRunId);
        return ResponseEntity.accepted().body(queued);
    }

    @PostMapping("/{trainingRunId}/training/start")
    public AmlTrainingRun startTraining(
            @PathVariable UUID trainingRunId,
            @Valid @RequestBody(required = false) StartModelTrainingRequest request
    ) {
        return registryService.startTraining(trainingRunId, request == null ? null : request.baseModelVersion());
    }

    @PostMapping("/{trainingRunId}/candidate")
    public ResponseEntity<AmlModelRegistryEntry> registerCandidate(
            @PathVariable UUID trainingRunId,
            @Valid @RequestBody RegisterCandidateModelRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registryService.registerCandidate(trainingRunId, request));
    }

    @PostMapping("/{trainingRunId}/training/fail")
    public AmlTrainingRun failTraining(
            @PathVariable UUID trainingRunId,
            @Valid @RequestBody FailModelTrainingRequest request
    ) {
        return registryService.failTraining(trainingRunId, request.reason());
    }

    @PostMapping("/{trainingRunId}/incremental-training")
    public ResponseEntity<AmlTrainingRun> incrementalTraining(
            @PathVariable UUID trainingRunId,
            @Valid @RequestBody(required = false) RunIncrementalTrainingRequest request
    ) {
        String baseModelVersion = request == null ? null : request.baseModelVersion();
        String requestedBy = request == null ? null : request.requestedBy();
        AmlTrainingRun training = incrementalTrainingService.start(trainingRunId, baseModelVersion);
        incrementalTrainingJobLauncher.train(trainingRunId, requestedBy);
        return ResponseEntity.accepted().body(training);
    }

    @PostMapping("/{trainingRunId}/production-candidates")
    public ResponseEntity<ProductionCandidateTrainingResponse> trainProductionCandidates(
            @PathVariable UUID trainingRunId,
            @Valid @RequestBody(required = false) RunProductionCandidateTrainingRequest request
    ) {
        String requestedBy = request == null ? null : request.requestedBy();
        ProductionCandidateTrainingResponse response = productionCandidateTrainingService.start(
                trainingRunId,
                request == null ? null : request.selectedModels(),
                requestedBy
        );
        List<AmlTrainingRun> trainingRuns = response.trainingRuns();
        incrementalTrainingJobLauncher.trainSequentially(
                trainingRuns.stream().map(AmlTrainingRun::trainingRunId).toList(), requestedBy);
        return ResponseEntity.accepted().body(response);
    }
}
