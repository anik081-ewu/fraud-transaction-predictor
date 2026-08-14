package com.ftd.fraud_transaction_detector.aml.research.application;

import com.ftd.fraud_transaction_detector.aml.research.api.RunGrowthAnalysisRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SupervisedGrowthStudyJobLauncher {

    private final SupervisedGrowthStudyService studyService;

    public SupervisedGrowthStudyJobLauncher(SupervisedGrowthStudyService studyService) {
        this.studyService = studyService;
    }

    @Async
    public void run(UUID studyId, UUID trainingRunId, RunGrowthAnalysisRequest options) {
        studyService.runAndPersist(studyId, trainingRunId, options);
    }
}
