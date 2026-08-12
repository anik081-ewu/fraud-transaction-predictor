package com.ftd.fraud_transaction_detector.aml.risk.infrastructure;

import com.ftd.fraud_transaction_detector.config.entity.AppConfig;
import com.ftd.fraud_transaction_detector.config.repo.AppConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppConfigRiskPolicyRepositoryTest {

    @Test
    void loadsCompleteVersionedPolicyFromAppConfig() {
        AppConfigRiskPolicyRepository repository = repository(Map.of(
                AppConfigRiskPolicyRepository.VERSION, "AML_RISK_POLICY_V2",
                AppConfigRiskPolicyRepository.CUSTOMER_WEIGHT, "0.20",
                AppConfigRiskPolicyRepository.PEER_WEIGHT, "0.15",
                AppConfigRiskPolicyRepository.ISOLATION_FOREST_WEIGHT, "0.10",
                AppConfigRiskPolicyRepository.HST_WEIGHT, "0.25",
                AppConfigRiskPolicyRepository.ONLINE_OCSVM_WEIGHT, "0.05",
                AppConfigRiskPolicyRepository.RULES_WEIGHT, "0.25",
                AppConfigRiskPolicyRepository.LOW_THRESHOLD, "0.40",
                AppConfigRiskPolicyRepository.MEDIUM_THRESHOLD, "0.65",
                AppConfigRiskPolicyRepository.HIGH_THRESHOLD, "0.80"
        ));

        var policy = repository.findActive("RETAIL_SALARIED");

        assertEquals("AML_RISK_POLICY_V2", policy.version());
        assertEquals(0.10, policy.isolationForestWeight());
        assertEquals(0.25, policy.halfSpaceTreesWeight());
        assertEquals(0.65, policy.mediumRiskThreshold());
    }

    @Test
    void rejectsIncompletePolicyInsteadOfUsingHiddenDefaults() {
        AppConfigRiskPolicyRepository repository = repository(Map.of(
                AppConfigRiskPolicyRepository.VERSION, "AML_RISK_POLICY_V2"
        ));

        assertThrows(IllegalStateException.class, () -> repository.findActive(null));
    }

    private AppConfigRiskPolicyRepository repository(Map<String, String> values) {
        AppConfigRepository configRepository = mock(AppConfigRepository.class);
        when(configRepository.findById(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = values.get(key);
            if (value == null) return Optional.empty();
            AppConfig config = new AppConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setValueType("STRING");
            return Optional.of(config);
        });
        return new AppConfigRiskPolicyRepository(configRepository);
    }
}
