package com.ftd.fraud_transaction_detector.aml.research.api;

import com.ftd.fraud_transaction_detector.aml.research.application.SupervisedGrowthStudyJobLauncher;
import com.ftd.fraud_transaction_detector.aml.research.application.SupervisedGrowthStudyService;
import com.ftd.fraud_transaction_detector.aml.research.domain.SupervisedGrowthStudy;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/aml/supervised-growth-studies")
public class SupervisedGrowthStudyController {

    private final SupervisedGrowthStudyService studyService;
    private final SupervisedGrowthStudyJobLauncher jobLauncher;

    public SupervisedGrowthStudyController(
            SupervisedGrowthStudyService studyService,
            SupervisedGrowthStudyJobLauncher jobLauncher
    ) {
        this.studyService = studyService;
        this.jobLauncher = jobLauncher;
    }

    @PostMapping("/training-runs/{trainingRunId}")
    public ResponseEntity<SupervisedGrowthStudy> start(
            @PathVariable UUID trainingRunId,
            @Valid @RequestBody(required = false) RunGrowthAnalysisRequest request,
            @RequestParam(required = false) String requestedBy
    ) {
        SupervisedGrowthStudyService.QueueResult result = studyService.queueOrReuse(trainingRunId, requestedBy);
        if (result.created()) {
            jobLauncher.run(result.study().studyId(), trainingRunId, request);
            return ResponseEntity.accepted().body(result.study());
        }
        return ResponseEntity.ok(result.study());
    }

    @GetMapping("/latest")
    public ResponseEntity<SupervisedGrowthStudy> latest() {
        return studyService.latestRelevant()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{studyId}")
    public SupervisedGrowthStudy get(@PathVariable UUID studyId) {
        return studyService.require(studyId);
    }
}
