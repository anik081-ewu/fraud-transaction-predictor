package com.ftd.fraud_transaction_detector.aml.research.application;

import com.ftd.fraud_transaction_detector.aml.research.api.LayerAblationReport;
import com.ftd.fraud_transaction_detector.aml.research.api.LayerAblationResult;
import com.ftd.fraud_transaction_detector.aml.research.domain.AblationVariant;
import com.ftd.fraud_transaction_detector.aml.research.domain.CounterfactualRisk;
import com.ftd.fraud_transaction_detector.aml.research.infrastructure.ShadowLayerScoreReader;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicy;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicyRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LayerAblationService {

    private static final List<Integer> PERCENTAGES = List.of(10, 25, 50, 100);

    private final ShadowLayerScoreReader reader;
    private final RiskPolicyRepository policyRepository;
    private final LayerAblationCalculator calculator;

    public LayerAblationService(
            ShadowLayerScoreReader reader,
            RiskPolicyRepository policyRepository,
            LayerAblationCalculator calculator
    ) {
        this.reader = reader;
        this.policyRepository = policyRepository;
        this.calculator = calculator;
    }

    public LayerAblationReport analyze() {
        long availableRows = reader.count();
        if (availableRows < 200) {
            return new LayerAblationReport(
                    "INSUFFICIENT_DATA", availableRows, List.of(),
                    java.util.Arrays.stream(AblationVariant.values()).map(Enum::name).toList(),
                    Map.of(
                            "minimumRows", 200,
                            "interpretation", "More saved layered predictions are required for reliable component impact analysis."
                    ),
                    List.of()
            );
        }
        Map<Long, Integer> checkpoints = checkpoints(availableRows);
        Map<AblationVariant, Accumulator> accumulators = new EnumMap<>(AblationVariant.class);
        for (AblationVariant variant : AblationVariant.values()) accumulators.put(variant, new Accumulator());
        Map<String, RiskPolicy> policies = new HashMap<>();
        List<LayerAblationResult> results = new ArrayList<>();
        long[] rowNumber = {0};
        reader.forEachOldest(row -> {
            rowNumber[0]++;
            RiskPolicy policy = policies.computeIfAbsent(
                    row.peerGroupCode() == null ? "GLOBAL" : row.peerGroupCode(),
                    ignored -> policyRepository.findActive(row.peerGroupCode())
            );
            CounterfactualRisk full = calculator.calculate(row.scores(), policy, AblationVariant.FULL);
            for (AblationVariant variant : AblationVariant.values()) {
                CounterfactualRisk result = calculator.calculate(row.scores(), policy, variant);
                accumulators.get(variant).accept(result, full);
            }
            Integer percentage = checkpoints.get(rowNumber[0]);
            if (percentage != null) appendSnapshot(results, percentage, rowNumber[0], accumulators);
        });
        return new LayerAblationReport(
                "COMPLETED", availableRows, new ArrayList<>(checkpoints.values()),
                java.util.Arrays.stream(AblationVariant.values()).map(Enum::name).toList(),
                Map.of(
                        "ordering", "OLDEST_SHADOW_PREDICTIONS_FIRST",
                        "policy", "CURRENT_ACTIVE_POLICY_REPLAY",
                        "weightHandling", "REMOVED_LAYER_WEIGHT_IS_RENORMALIZED_ACROSS_INCLUDED_LAYERS",
                        "hardRuleHandling", "RULE_OVERRIDE_APPLIES_ONLY_WHEN_RULES_ARE_INCLUDED",
                        "interpretation", "Decision-change rates quantify each layer's operational effect; they are not accuracy."
                ),
                results
        );
    }

    private Map<Long, Integer> checkpoints(long total) {
        Map<Long, Integer> checkpoints = new LinkedHashMap<>();
        for (int percentage : PERCENTAGES) {
            long rows = percentage == 100 ? total : Math.max(1, total * percentage / 100);
            checkpoints.put(rows, percentage);
        }
        return checkpoints;
    }

    private void appendSnapshot(
            List<LayerAblationResult> target,
            int percentage,
            long rows,
            Map<AblationVariant, Accumulator> accumulators
    ) {
        for (AblationVariant variant : AblationVariant.values()) {
            Accumulator value = accumulators.get(variant);
            target.add(new LayerAblationResult(
                    percentage, rows, variant.name(), value.scoreSum / rows,
                    value.suspiciousCount / (double) rows, value.suspiciousCount,
                    value.decisionChanges, value.decisionChanges / (double) rows,
                    value.scoreDeltaSum / rows, value.hardOverrides
            ));
        }
    }

    private static final class Accumulator {
        private double scoreSum;
        private double scoreDeltaSum;
        private long suspiciousCount;
        private long decisionChanges;
        private long hardOverrides;

        private void accept(CounterfactualRisk result, CounterfactualRisk full) {
            scoreSum += result.score();
            scoreDeltaSum += result.score() - full.score();
            suspiciousCount += result.suspicious() ? 1 : 0;
            decisionChanges += result.suspicious() != full.suspicious() ? 1 : 0;
            hardOverrides += result.hardRuleOverride() ? 1 : 0;
        }
    }
}
