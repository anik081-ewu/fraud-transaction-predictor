package com.ftd.fraud_transaction_detector.comparison.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.comparison.dto.AnomalyConfigRequest;
import com.ftd.fraud_transaction_detector.comparison.repo.AnomalyConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AnomalyConfigServiceTest {

    @Test
    void rejectsOfflineOnlyModelFromProductionConfiguration() {
        AnomalyConfigRepository repository = mock(AnomalyConfigRepository.class);
        AnomalyConfigService service = new AnomalyConfigService(repository, new ObjectMapper());
        AnomalyConfigRequest request = new AnomalyConfigRequest(
                "Production anomaly policy", List.of("LOF"), "SIMPLE_COUNT",
                1, 1, 1, false, Map.of(), 1L, "models/bundle", true, "admin"
        );

        assertThrows(IllegalArgumentException.class, () -> service.saveConfig(request));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
