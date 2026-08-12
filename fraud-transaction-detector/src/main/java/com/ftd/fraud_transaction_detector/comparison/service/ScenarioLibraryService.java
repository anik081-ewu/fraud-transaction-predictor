package com.ftd.fraud_transaction_detector.comparison.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.comparison.dto.ComparisonScenarioCreateRequest;
import com.ftd.fraud_transaction_detector.comparison.dto.ComparisonScenarioResponse;
import com.ftd.fraud_transaction_detector.comparison.dto.ScenarioSetCreateRequest;
import com.ftd.fraud_transaction_detector.comparison.dto.ScenarioSetResponse;
import com.ftd.fraud_transaction_detector.comparison.entity.ComparisonScenario;
import com.ftd.fraud_transaction_detector.comparison.entity.ScenarioSet;
import com.ftd.fraud_transaction_detector.comparison.repo.ComparisonScenarioRepository;
import com.ftd.fraud_transaction_detector.comparison.repo.ScenarioSetRepository;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Service
public class ScenarioLibraryService {

    private final ScenarioSetRepository scenarioSetRepository;
    private final ComparisonScenarioRepository comparisonScenarioRepository;
    private final ObjectMapper objectMapper;

    public ScenarioLibraryService(
            ScenarioSetRepository scenarioSetRepository,
            ComparisonScenarioRepository comparisonScenarioRepository,
            ObjectMapper objectMapper
    ) {
        this.scenarioSetRepository = scenarioSetRepository;
        this.comparisonScenarioRepository = comparisonScenarioRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ScenarioSetResponse createScenarioSet(ScenarioSetCreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Scenario set name is required");
        }
        ScenarioSet scenarioSet = new ScenarioSet();
        scenarioSet.setScenarioSetNo("SCSET-" + Instant.now().toEpochMilli());
        scenarioSet.setName(request.name().trim());
        scenarioSet.setDescription(request.description());
        scenarioSet.setCreatedBy(request.createdBy());
        scenarioSet.setCreatedAt(Instant.now());
        return ScenarioSetResponse.from(scenarioSetRepository.save(scenarioSet));
    }

    @Transactional(readOnly = true)
    public List<ScenarioSetResponse> listScenarioSets() {
        return scenarioSetRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(ScenarioSetResponse::from)
                .toList();
    }

    @Transactional
    public ComparisonScenarioResponse createScenario(Long scenarioSetId, ComparisonScenarioCreateRequest request) {
        ScenarioSet scenarioSet = scenarioSetRepository.findById(scenarioSetId)
                .orElseThrow(() -> new IllegalArgumentException("Scenario set not found: " + scenarioSetId));
        if (request == null || request.scenarioName() == null || request.scenarioName().isBlank()) {
            throw new IllegalArgumentException("Scenario name is required");
        }
        if (request.transaction() == null) {
            throw new IllegalArgumentException("Scenario transaction is required");
        }
        if (request.accountProfile() == null) {
            throw new IllegalArgumentException("Scenario accountProfile is required");
        }

        ComparisonScenario scenario = new ComparisonScenario();
        scenario.setScenarioNo("SCN-" + Instant.now().toEpochMilli());
        scenario.setScenarioSet(scenarioSet);
        scenario.setScenarioName(request.scenarioName().trim());
        scenario.setScenarioType(request.scenarioType());
        scenario.setTransactionJson(writeJson(request.transaction()));
        scenario.setCustomerJson(writeJson(request.customer()));
        scenario.setAccountProfileJson(writeJson(request.accountProfile()));
        scenario.setExpectedNotes(request.expectedNotes());
        scenario.setCreatedAt(Instant.now());

        ComparisonScenario saved = comparisonScenarioRepository.save(scenario);
        return ComparisonScenarioResponse.from(
                saved,
                request.transaction(),
                request.customer(),
                request.accountProfile()
        );
    }

    @Transactional(readOnly = true)
    public List<ComparisonScenarioResponse> listScenarios(Long scenarioSetId) {
        return comparisonScenarioRepository.findByScenarioSetIdOrderByCreatedAtAscIdAsc(scenarioSetId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ComparisonScenario> findScenarioEntities(Long scenarioSetId) {
        return comparisonScenarioRepository.findByScenarioSetIdOrderByCreatedAtAscIdAsc(scenarioSetId);
    }

    public FraudPredictionRequest.TransactionDto readTransaction(ComparisonScenario scenario) {
        return readJson(scenario.getTransactionJson(), FraudPredictionRequest.TransactionDto.class);
    }

    public FraudPredictionRequest.CustomerDto readCustomer(ComparisonScenario scenario) {
        if (scenario.getCustomerJson() == null || scenario.getCustomerJson().isBlank()) {
            return new FraudPredictionRequest.CustomerDto(null, null);
        }
        return readJson(scenario.getCustomerJson(), FraudPredictionRequest.CustomerDto.class);
    }

    public FraudPredictionRequest.AccountProfileDto readAccountProfile(ComparisonScenario scenario) {
        return readJson(scenario.getAccountProfileJson(), FraudPredictionRequest.AccountProfileDto.class);
    }

    private ComparisonScenarioResponse toResponse(ComparisonScenario scenario) {
        return ComparisonScenarioResponse.from(
                scenario,
                readTransaction(scenario),
                readCustomer(scenario),
                readAccountProfile(scenario)
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize scenario payload: " + ex.getMessage(), ex);
        }
    }

    private <T> T readJson(String raw, Class<T> type) {
        try {
            return objectMapper.readValue(raw, type);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read stored scenario JSON", ex);
        }
    }
}
