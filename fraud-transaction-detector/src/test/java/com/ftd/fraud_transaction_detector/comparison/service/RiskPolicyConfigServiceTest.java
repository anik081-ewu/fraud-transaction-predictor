package com.ftd.fraud_transaction_detector.comparison.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.comparison.dto.RiskPolicyModelConfigRequest;
import com.ftd.fraud_transaction_detector.comparison.dto.RiskPolicyConfigUpdateRequest;
import com.ftd.fraud_transaction_detector.config.entity.AppConfig;
import com.ftd.fraud_transaction_detector.config.repo.AppConfigRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskPolicyConfigServiceTest {

    @Test
    void updatesValidatedPolicyAndCreatesNewVersion() {
        Map<String, AppConfig> values = new HashMap<>();
        AppConfigRepository repository = repository(values);
        Instant now = Instant.parse("2026-08-06T06:00:00Z");
        RiskPolicyConfigService service = new RiskPolicyConfigService(
                repository, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC)
        );

        var response = service.update(new RiskPolicyConfigUpdateRequest(
                0.20, 0.15, 0.40, 0.25,
                java.util.List.of(
                        new RiskPolicyModelConfigRequest("ISOLATION_FOREST", true, 0.50),
                        new RiskPolicyModelConfigRequest("HALF_SPACE_TREES", true, 0.30),
                        new RiskPolicyModelConfigRequest("ONLINE_ONE_CLASS_SVM", true, 0.20)
                ),
                "DAILY", "WEEKLY",
                0.40, 0.65, 0.80, true, true
        ));

        assertThat(response.policyVersion()).isEqualTo("AML_RISK_POLICY_20260806060000000");
        assertThat(response.mlEnsembleWeight()).isEqualTo(0.40);
        assertThat(response.mediumRiskThreshold()).isEqualTo(0.65);
        assertThat(response.updatedAt()).isEqualTo(now);
        assertThat(values).hasSize(15);
    }

    @Test
    void rejectsWeightsThatDoNotTotalOne() {
        RiskPolicyConfigService service = new RiskPolicyConfigService(
                repository(new HashMap<>()), new ObjectMapper(), Clock.systemUTC()
        );

        assertThatThrownBy(() -> service.update(new RiskPolicyConfigUpdateRequest(
                0.20, 0.15, 0.40, 0.25,
                java.util.List.of(
                        new RiskPolicyModelConfigRequest("ISOLATION_FOREST", true, 0.60),
                        new RiskPolicyModelConfigRequest("HALF_SPACE_TREES", true, 0.30)
                ),
                "DAILY", "WEEKLY",
                0.40, 0.65, 0.80, true, true
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total exactly 1.0");
    }

    @Test
    void rejectsUnorderedRiskThresholds() {
        RiskPolicyConfigService service = new RiskPolicyConfigService(
                repository(new HashMap<>()), new ObjectMapper(), Clock.systemUTC()
        );

        assertThatThrownBy(() -> service.update(new RiskPolicyConfigUpdateRequest(
                0.20, 0.15, 0.40, 0.25,
                java.util.List.of(
                        new RiskPolicyModelConfigRequest("ISOLATION_FOREST", true, 0.50),
                        new RiskPolicyModelConfigRequest("HALF_SPACE_TREES", true, 0.30),
                        new RiskPolicyModelConfigRequest("ONLINE_ONE_CLASS_SVM", true, 0.20)
                ),
                "DAILY", "WEEKLY",
                0.70, 0.65, 0.80, true, true
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thresholds must be ordered");
    }

    private AppConfigRepository repository(Map<String, AppConfig> values) {
        return (AppConfigRepository) Proxy.newProxyInstance(
                AppConfigRepository.class.getClassLoader(),
                new Class<?>[]{AppConfigRepository.class},
                (_proxy, method, arguments) -> switch (method.getName()) {
                    case "findById" -> Optional.ofNullable(values.get((String) arguments[0]));
                    case "save" -> {
                        AppConfig config = (AppConfig) arguments[0];
                        values.put(config.getConfigKey(), config);
                        yield config;
                    }
                    case "toString" -> "InMemoryAppConfigRepository";
                    case "hashCode" -> System.identityHashCode(values);
                    case "equals" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
