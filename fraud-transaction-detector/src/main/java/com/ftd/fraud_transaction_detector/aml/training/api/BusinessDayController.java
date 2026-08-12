package com.ftd.fraud_transaction_detector.aml.training.api;

import com.ftd.fraud_transaction_detector.aml.training.application.BusinessDayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/aml/business-days")
public class BusinessDayController {

    private final BusinessDayService businessDayService;

    public BusinessDayController(BusinessDayService businessDayService) {
        this.businessDayService = businessDayService;
    }

    @PostMapping("/{businessDate}/close")
    public ResponseEntity<Map<String, Object>> close(
            @PathVariable LocalDate businessDate,
            @RequestBody(required = false) CloseBusinessDayRequest request
    ) {
        businessDayService.close(businessDate, request == null ? null : request.closedBy());
        return ResponseEntity.ok(Map.of(
                "businessDate", businessDate,
                "status", "CLOSED"
        ));
    }

    @PostMapping("/range/close")
    public ResponseEntity<Map<String, Object>> closeRange(@RequestBody CloseBusinessDateRangeRequest request) {
        int closedDateCount = businessDayService.closeRange(
                request.fromDate(), request.toDate(), request.closedBy());
        return ResponseEntity.ok(Map.of(
                "fromDate", request.fromDate(),
                "toDate", request.toDate(),
                "status", "CLOSED",
                "closedDateCount", closedDateCount
        ));
    }
}
