package com.ftd.fraud_transaction_detector.aml.research.application;

import com.ftd.fraud_transaction_detector.aml.research.api.RunGrowthAnalysisRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Runs a growth study off the request thread. The analysis trains 5 detectors across 4
 * partitions, so it takes minutes — the HTTP caller gets a study id immediately and polls.
 */
@Service
public class GrowthStudyJobLauncher {

    private final GrowthStudyService studyService;

    public GrowthStudyJobLauncher(GrowthStudyService studyService) {
        this.studyService = studyService;
    }

    @Async
    public void run(UUID studyId, UUID trainingRunId, RunGrowthAnalysisRequest options) {
        studyService.runAndPersist(studyId, trainingRunId, options);
    }
}
