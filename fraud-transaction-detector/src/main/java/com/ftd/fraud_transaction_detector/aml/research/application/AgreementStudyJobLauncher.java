package com.ftd.fraud_transaction_detector.aml.research.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Runs an agreement study off the request thread — scoring the whole snapshot with every
 * model takes minutes, so the caller gets a study id immediately and polls.
 */
@Service
public class AgreementStudyJobLauncher {

    private final AgreementStudyService studyService;

    public AgreementStudyJobLauncher(AgreementStudyService studyService) {
        this.studyService = studyService;
    }

    @Async
    public void run(UUID studyId, UUID trainingRunId) {
        studyService.runAndPersist(studyId, trainingRunId);
    }
}
