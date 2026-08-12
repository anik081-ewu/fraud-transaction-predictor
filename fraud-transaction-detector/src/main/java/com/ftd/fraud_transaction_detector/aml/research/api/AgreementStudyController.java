package com.ftd.fraud_transaction_detector.aml.research.api;

import com.ftd.fraud_transaction_detector.aml.research.application.AgreementStudyJobLauncher;
import com.ftd.fraud_transaction_detector.aml.research.application.AgreementStudyService;
import com.ftd.fraud_transaction_detector.aml.research.domain.AgreementStudy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/aml/agreement-studies")
public class AgreementStudyController {

    private final AgreementStudyService studyService;
    private final AgreementStudyJobLauncher jobLauncher;

    public AgreementStudyController(
            AgreementStudyService studyService,
            AgreementStudyJobLauncher jobLauncher
    ) {
        this.studyService = studyService;
        this.jobLauncher = jobLauncher;
    }

    @PostMapping("/training-runs/{trainingRunId}")
    public ResponseEntity<AgreementStudy> start(
            @PathVariable UUID trainingRunId,
            @RequestParam(required = false) String requestedBy
    ) {
        UUID studyId = studyService.queue(trainingRunId, requestedBy);
        jobLauncher.run(studyId, trainingRunId);
        return ResponseEntity.accepted().body(studyService.require(studyId));
    }

    /** Running study takes precedence over the last completed one; 204 when none exists. */
    @GetMapping("/latest")
    public ResponseEntity<AgreementStudy> latest() {
        return studyService.latestRelevant()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{studyId}")
    public AgreementStudy get(@PathVariable UUID studyId) {
        return studyService.require(studyId);
    }

    @GetMapping
    public List<AgreementStudy> list() {
        return studyService.listRecent();
    }
}
