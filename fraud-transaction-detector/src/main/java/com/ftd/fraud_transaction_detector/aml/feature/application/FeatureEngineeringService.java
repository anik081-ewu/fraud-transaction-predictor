package com.ftd.fraud_transaction_detector.aml.feature.application;

import com.ftd.fraud_transaction_detector.aml.feature.calculator.AmountFeatureCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.BehaviorFeatureCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.ComprehensiveModelFeatureCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.NoveltyFeatureCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.LegacyModelFeatureCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.ProfileConfidenceCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.PeerFeatureCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.TimeFeatureCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.TerminalRiskFeatureCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.VelocityFeatureCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class FeatureEngineeringService {

    private final AmountFeatureCalculator amountCalculator;
    private final BehaviorFeatureCalculator behaviorCalculator;
    private final TimeFeatureCalculator timeCalculator;
    private final VelocityFeatureCalculator velocityCalculator;
    private final NoveltyFeatureCalculator noveltyCalculator;
    private final ProfileConfidenceCalculator profileCalculator;
    private final PeerFeatureCalculator peerCalculator;
    private final TerminalRiskFeatureCalculator terminalRiskCalculator;
    private final LegacyModelFeatureCalculator modelFeatureCalculator;
    private final ComprehensiveModelFeatureCalculator comprehensiveModelFeatureCalculator;
    private final Clock clock;

    public FeatureEngineeringService() {
        this(Clock.systemUTC());
    }

    public FeatureEngineeringService(Clock clock) {
        this(
                new AmountFeatureCalculator(),
                new BehaviorFeatureCalculator(),
                new TimeFeatureCalculator(),
                new VelocityFeatureCalculator(),
                new NoveltyFeatureCalculator(),
                new ProfileConfidenceCalculator(),
                new PeerFeatureCalculator(),
                new TerminalRiskFeatureCalculator(),
                new LegacyModelFeatureCalculator(),
                new ComprehensiveModelFeatureCalculator(),
                clock
        );
    }

    FeatureEngineeringService(
            AmountFeatureCalculator amountCalculator,
            BehaviorFeatureCalculator behaviorCalculator,
            TimeFeatureCalculator timeCalculator,
            VelocityFeatureCalculator velocityCalculator,
            NoveltyFeatureCalculator noveltyCalculator,
            ProfileConfidenceCalculator profileCalculator,
            PeerFeatureCalculator peerCalculator,
            TerminalRiskFeatureCalculator terminalRiskCalculator,
            LegacyModelFeatureCalculator modelFeatureCalculator,
            ComprehensiveModelFeatureCalculator comprehensiveModelFeatureCalculator,
            Clock clock
    ) {
        this.amountCalculator = Objects.requireNonNull(amountCalculator);
        this.behaviorCalculator = Objects.requireNonNull(behaviorCalculator);
        this.timeCalculator = Objects.requireNonNull(timeCalculator);
        this.velocityCalculator = Objects.requireNonNull(velocityCalculator);
        this.noveltyCalculator = Objects.requireNonNull(noveltyCalculator);
        this.profileCalculator = Objects.requireNonNull(profileCalculator);
        this.peerCalculator = Objects.requireNonNull(peerCalculator);
        this.terminalRiskCalculator = Objects.requireNonNull(terminalRiskCalculator);
        this.modelFeatureCalculator = Objects.requireNonNull(modelFeatureCalculator);
        this.comprehensiveModelFeatureCalculator = Objects.requireNonNull(comprehensiveModelFeatureCalculator);
        this.clock = Objects.requireNonNull(clock);
    }

    public TransactionFeatureVector calculate(
            FeatureContext context,
            String featureVersion,
            BigDecimal reportingThreshold
    ) {
        if (featureVersion == null || featureVersion.isBlank()) {
            throw new IllegalArgumentException("featureVersion is required");
        }
        var current = context.currentTransaction();
        var amount = amountCalculator.calculate(context);
        var behavior = behaviorCalculator.calculate(context);
        var time = timeCalculator.calculate(context);
        var velocity = velocityCalculator.calculate(context, reportingThreshold);
        var novelty = noveltyCalculator.calculate(context);
        var profile = profileCalculator.calculate(context);
        var peer = peerCalculator.calculate(context);
        var terminalRisk = terminalRiskCalculator.calculate(context.terminalRiskContext());
        var modelFeatures = comprehensiveModelFeatureCalculator.calculate(
                modelFeatureCalculator.calculate(context), amount, behavior, time, velocity, novelty, profile, peer, terminalRisk
        );
        return new TransactionFeatureVector(
                current.transactionId(),
                current.customerId(),
                current.accountId(),
                current.transactionDate().toLocalDate(),
                current.transactionDate(),
                featureVersion,
                amount,
                behavior,
                time,
                velocity,
                novelty,
                profile,
                peer,
                terminalRisk,
                ComprehensiveModelFeatureCalculator.SCHEMA,
                modelFeatures,
                Instant.now(clock)
        );
    }
}
