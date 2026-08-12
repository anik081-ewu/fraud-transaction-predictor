package com.ftd.fraud_transaction_detector.aml.research.api;

import com.ftd.fraud_transaction_detector.aml.research.application.GrowthStudyJobLauncher;
import com.ftd.fraud_transaction_detector.aml.research.application.GrowthStudyService;
import com.ftd.fraud_transaction_detector.aml.research.domain.GrowthStudy;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/aml/growth-studies")
public class GrowthStudyController {

    private final GrowthStudyService studyService;
    private final GrowthStudyJobLauncher jobLauncher;

    public GrowthStudyController(GrowthStudyService studyService, GrowthStudyJobLauncher jobLauncher) {
        this.studyService = studyService;
        this.jobLauncher = jobLauncher;
    }

    /** Queues a study and returns immediately; the analysis runs for minutes in the background. */
    @PostMapping("/training-runs/{trainingRunId}")
    public ResponseEntity<GrowthStudy> start(
            @PathVariable UUID trainingRunId,
            @Valid @RequestBody(required = false) RunGrowthAnalysisRequest request,
            @RequestParam(required = false) String requestedBy
    ) {
        UUID studyId = studyService.queue(trainingRunId, requestedBy);
        jobLauncher.run(studyId, trainingRunId, request);
        return ResponseEntity.accepted().body(studyService.require(studyId));
    }

    @GetMapping
    public List<GrowthStudy> list() {
        return studyService.listRecent();
    }

    /**
     * The study the comparison page shows: a running one takes precedence over the last
     * completed result, so navigating away and back never loses sight of work in flight.
     * 204 only when no study has ever been created.
     */
    @GetMapping("/latest")
    public ResponseEntity<GrowthStudy> latest() {
        return studyService.latestRelevant()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{studyId}")
    public GrowthStudy get(@PathVariable UUID studyId) {
        return studyService.require(studyId);
    }
}
