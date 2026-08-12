package com.ftd.fraud_transaction_detector.aml.research.application;

import com.ftd.fraud_transaction_detector.aml.research.api.RunGrowthAnalysisRequest;
import com.ftd.fraud_transaction_detector.aml.research.client.GrowthAnalysisClient;
import com.ftd.fraud_transaction_detector.aml.research.client.GrowthAnalysisRequest;
import com.ftd.fraud_transaction_detector.aml.research.client.GrowthAnalysisResponse;
import com.ftd.fraud_transaction_detector.aml.training.application.TrainingDatasetExportService;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GrowthAnalysisService {

    private static final List<Integer> DEFAULT_PERCENTAGES = List.of(10, 25, 50, 100);

    private final TrainingDatasetExportService datasetService;
    private final GrowthAnalysisClient client;
    private final AppConfigService appConfigService;

    public GrowthAnalysisService(
            TrainingDatasetExportService datasetService,
            GrowthAnalysisClient client,
            AppConfigService appConfigService
    ) {
        this.datasetService = datasetService;
        this.client = client;
        this.appConfigService = appConfigService;
    }

    public GrowthAnalysisResponse analyze(UUID trainingRunId, RunGrowthAnalysisRequest options) {
        AmlTrainingRun run = datasetService.getRun(trainingRunId);
        // The real precondition is a verified dataset, not a particular status: a run that has
        // since trained its models sits at CANDIDATE_READY but its exported snapshot is just as
        // valid to study. Requiring DATASET_READY would reject every successful pipeline.
        if (run.datasetPath() == null || run.datasetChecksum() == null) {
            throw new IllegalStateException("Training run has no verified persisted-feature dataset");
        }
        List<Integer> percentages = options == null || options.percentages() == null || options.percentages().isEmpty()
                ? DEFAULT_PERCENTAGES : options.percentages();
        return client.analyze(new GrowthAnalysisRequest(
                run.datasetPath(), run.datasetChecksum(), percentages,
                options == null || options.minimumRows() == null
                        ? appConfigService.getResearchMinimumRows() : options.minimumRows(),
                appConfigService.getResearchHoldoutFraction(),
                options == null || options.maximumEvaluationRows() == null
                        ? appConfigService.getResearchMaximumEvaluationRows() : options.maximumEvaluationRows(),
                options == null || options.isolationForestMaximumTrainingRows() == null
                        ? appConfigService.getResearchIsolationForestMaximumTrainingRows()
                        : options.isolationForestMaximumTrainingRows(),
                appConfigService.getResearchIsolationForestEstimators(),
                appConfigService.getResearchAutoencoderMaxTrainingRows(),
                appConfigService.getResearchRandomSeed(),
                appConfigService.getHstParameters(),
                appConfigService.getOnlineOneClassSvmParameters()
        ));
    }
}
