package com.ftd.fraud_transaction_detector.aml.training.application;

import com.ftd.fraud_transaction_detector.aml.training.infrastructure.BusinessDayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BusinessDayService {

    private final BusinessDayRepository repository;

    public BusinessDayService(BusinessDayRepository repository) {
        this.repository = repository;
    }

    public void close(LocalDate businessDate, String closedBy) {
        if (businessDate == null) {
            throw new IllegalArgumentException("businessDate is required");
        }
        repository.close(businessDate, normalizeUser(closedBy));
    }

    @Transactional
    public int closeRange(LocalDate fromDate, LocalDate toDate, String closedBy) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate are required");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must be on or before toDate");
        }
        return repository.closeRange(fromDate, toDate, normalizeUser(closedBy));
    }

    public void requireClosed(LocalDate fromDate, LocalDate toDate) {
        List<LocalDate> unclosed = repository.findUnclosedTransactionDates(fromDate, toDate);
        if (!unclosed.isEmpty()) {
            throw new IllegalStateException("Business dates must be closed before export: " + unclosed);
        }
    }

    private String normalizeUser(String value) {
        return value == null || value.isBlank() ? "system" : value.trim();
    }
}
