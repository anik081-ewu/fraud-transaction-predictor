package com.ftd.fraud_transaction_detector.fraud.web;

import com.ftd.fraud_transaction_detector.fraud.entity.FraudAlert;
import com.ftd.fraud_transaction_detector.fraud.service.FraudAlertReviewService;
import com.ftd.fraud_transaction_detector.fraud.web.dto.AlertReviewRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fraud-alerts")
public class FraudAlertReviewController {

    private final FraudAlertReviewService fraudAlertReviewService;

    public FraudAlertReviewController(FraudAlertReviewService fraudAlertReviewService) {
        this.fraudAlertReviewService = fraudAlertReviewService;
    }

    @PutMapping("/{alertId}/false-positive")
    public FraudAlert markFalsePositive(
            @PathVariable Long alertId,
            @RequestBody AlertReviewRequest request
    ) {
        return fraudAlertReviewService.markFalsePositive(alertId, request.reviewedBy());
    }

    @PutMapping("/{alertId}/str-generated")
    public FraudAlert markStrGenerated(
            @PathVariable Long alertId,
            @RequestBody AlertReviewRequest request
    ) {
        return fraudAlertReviewService.markStrGenerated(alertId, request.reviewedBy(), request.strFileName());
    }
}
