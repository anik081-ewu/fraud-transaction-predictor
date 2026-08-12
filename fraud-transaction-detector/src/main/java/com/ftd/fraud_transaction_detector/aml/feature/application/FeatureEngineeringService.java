package com.ftd.fraud_transaction_detector.aml.feature.application;

import com.ftd.fraud_transaction_detector.aml.feature.calculator.AmountFeatureCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.BehaviorFeatureCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.NoveltyFeatureCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.LegacyModelFeatureCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.ProfileConfidenceCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.PeerFeatureCalculator;
import com.ftd.fraud_transaction_detector.aml.feature.calculator.TimeFeatureCalculator;
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
    private final LegacyModelFeatureCalculator modelFeatureCalculator;
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
                new LegacyModelFeatureCalculator(),
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
            LegacyModelFeatureCalculator modelFeatureCalculator,
            Clock clock
    ) {
        this.amountCalculator = Objects.requireNonNull(amountCalculator);
        this.behaviorCalculator = Objects.requireNonNull(behaviorCalculator);
        this.timeCalculator = Objects.requireNonNull(timeCalculator);
        this.velocityCalculator = Objects.requireNonNull(velocityCalculator);
        this.noveltyCalculator = Objects.requireNonNull(noveltyCalculator);
        this.profileCalculator = Objects.requireNonNull(profileCalculator);
        this.peerCalculator = Objects.requireNonNull(peerCalculator);
        this.modelFeatureCalculator = Objects.requireNonNull(modelFeatureCalculator);
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
        return new TransactionFeatureVector(
                current.transactionId(),
                current.customerId(),
                current.accountId(),
                current.transactionDate().toLocalDate(),
                current.transactionDate(),
                featureVersion,
                amountCalculator.calculate(context),
                behaviorCalculator.calculate(context),
                timeCalculator.calculate(context),
                velocityCalculator.calculate(context, reportingThreshold),
                noveltyCalculator.calculate(context),
                profileCalculator.calculate(context),
                peerCalculator.calculate(context),
                LegacyModelFeatureCalculator.SCHEMA,
                modelFeatureCalculator.calculate(context),
                Instant.now(clock)
        );
    }
}
