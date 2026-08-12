package com.ftd.fraud_transaction_detector.aml.peer.application;

import com.ftd.fraud_transaction_detector.aml.feature.domain.PeerContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TrustedProfileSnapshot;
import com.ftd.fraud_transaction_detector.aml.peer.domain.PeerGroupDefinition;
import com.ftd.fraud_transaction_detector.aml.peer.domain.PeerGroupStats;
import com.ftd.fraud_transaction_detector.aml.peer.infrastructure.PeerGroupStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class PeerContextLoader {

    private static final Logger log = LoggerFactory.getLogger(PeerContextLoader.class);
    private static final long REFERENCE_CACHE_NANOS = java.time.Duration.ofMinutes(5).toNanos();

    private final PeerGroupAssigner assigner;
    private final PeerGroupStatsRepository statsRepository;
    private final ConcurrentMap<String, CachedReferenceDate> referenceDateCache = new ConcurrentHashMap<>();

    public PeerContextLoader(PeerGroupAssigner assigner, PeerGroupStatsRepository statsRepository) {
        this.assigner = assigner;
        this.statsRepository = statsRepository;
    }

    public PeerContext load(
            String occupation,
            Integer age,
            String accountId,
            TrustedProfileSnapshot profile,
            LocalDateTime referenceDate
    ) {
        List<PeerGroupDefinition> candidates;
        try {
            candidates = assigner.candidates(occupation, age);
        } catch (Exception exception) {
            log.warn("Peer group assignment failed for accountId={}: {}", accountId, exception.getMessage());
            return PeerContext.empty();
        }

        PeerGroupDefinition primaryGroup = candidates.get(0);
        Optional<LocalDateTime> trainingReferenceDate;
        try {
            trainingReferenceDate = trainingReferenceDate(referenceDate);
        } catch (Exception exception) {
            log.warn("Training-data reference query failed for group={} accountId={}: {}",
                    primaryGroup.code(), accountId, exception.getMessage());
            return emptyStatsContext(primaryGroup, profile);
        }
        if (trainingReferenceDate.isEmpty()) {
            log.warn("Peer baseline unavailable because no completed training dataset exists for group={}",
                    primaryGroup.code());
            return emptyStatsContext(primaryGroup, profile);
        }

        LocalDateTime peerReferenceDate = trainingReferenceDate.get();
        for (PeerGroupDefinition group : candidates) {
            try {
                Optional<PeerGroupStats> stats = statsRepository.findStats(group, peerReferenceDate);
                if (stats.isEmpty()) continue;
                double percentile = statsRepository
                        .findFrequencyPercentile(accountId, group, peerReferenceDate)
                        .orElse(0.5);
                log.debug(
                        "Peer baseline anchored to training data date={} selectedGroup={} primaryGroup={} scoringDate={}",
                        peerReferenceDate, group.code(), primaryGroup.code(), referenceDate
                );
                return context(group, stats.get(), percentile, profile);
            } catch (Exception exception) {
                log.warn("Peer group stats query failed for group={} accountId={}: {}",
                        group.code(), accountId, exception.getMessage());
            }
        }
        return emptyStatsContext(primaryGroup, profile);
    }

    private Optional<LocalDateTime> trainingReferenceDate(LocalDateTime scoringReferenceDate) {
        String cacheKey = scoringReferenceDate.toLocalDate().toString();
        long now = System.nanoTime();
        CachedReferenceDate cached = referenceDateCache.get(cacheKey);
        if (cached != null && cached.expiresAtNanos() > now) {
            return cached.referenceDate();
        }
        Optional<LocalDateTime> resolved = statsRepository
                .findLatestTrainingDataTransactionDate(scoringReferenceDate);
        referenceDateCache.put(cacheKey, new CachedReferenceDate(resolved, now + REFERENCE_CACHE_NANOS));
        return resolved;
    }

    public PeerContext loadWithCache(
            String occupation,
            Integer age,
            String accountId,
            TrustedProfileSnapshot profile,
            LocalDateTime referenceDate,
            Map<String, PeerGroupStats> statsCache,
            Map<String, Double> percentileCache
    ) {
        List<PeerGroupDefinition> candidates;
        try {
            candidates = assigner.candidates(occupation, age);
        } catch (Exception exception) {
            log.warn("Peer group assignment failed for accountId={}: {}", accountId, exception.getMessage());
            return PeerContext.empty();
        }

        String dateKey = referenceDate.toLocalDate().toString();
        for (PeerGroupDefinition group : candidates) {
            String statsKey = group.code() + "|" + dateKey;
            String percentileKey = accountId + "|" + statsKey;
            PeerGroupStats stats = statsCache.computeIfAbsent(statsKey, key -> {
                try {
                    return statsRepository.findStats(group, referenceDate).orElse(null);
                } catch (Exception exception) {
                    log.warn("Peer group stats query failed for group={}: {}", group.code(), exception.getMessage());
                    return null;
                }
            });
            if (stats == null) continue;
            double percentile = percentileCache.computeIfAbsent(percentileKey, key -> {
                try {
                    return statsRepository.findFrequencyPercentile(accountId, group, referenceDate).orElse(0.5);
                } catch (Exception exception) {
                    return 0.5;
                }
            });
            return context(group, stats, percentile, profile);
        }
        return emptyStatsContext(candidates.get(0), profile);
    }

    private PeerContext context(
            PeerGroupDefinition group,
            PeerGroupStats stats,
            double percentile,
            TrustedProfileSnapshot profile
    ) {
        return new PeerContext(
                group.code(), stats.averageAmount(), stats.medianAmount(), stats.standardDeviationAmount(),
                percentile, group.customerType(), deriveRiskRating(profile), stats.expectedMonthlyTurnover()
        );
    }

    private PeerContext emptyStatsContext(PeerGroupDefinition group, TrustedProfileSnapshot profile) {
        return new PeerContext(
                group.code(), null, null, null, null,
                group.customerType(), deriveRiskRating(profile), null
        );
    }

    private String deriveRiskRating(TrustedProfileSnapshot profile) {
        if (profile == null) return "UNKNOWN";
        return switch (profile.status()) {
            case COLD_START, LOW_CONFIDENCE -> "HIGH";
            case DEVELOPING -> "MEDIUM";
            case ESTABLISHED -> "LOW";
            default -> "UNKNOWN";
        };
    }

    private record CachedReferenceDate(Optional<LocalDateTime> referenceDate, long expiresAtNanos) {
    }
}
