package com.ftd.fraud_transaction_detector.aml.training.application;

import com.ftd.fraud_transaction_detector.aml.feature.application.FeatureContextLoader;
import com.ftd.fraud_transaction_detector.aml.feature.application.FeatureEngineeringService;
import com.ftd.fraud_transaction_detector.aml.feature.application.FeatureVersionProvider;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.aml.feature.infrastructure.FeaturePersistenceService;
import com.ftd.fraud_transaction_detector.aml.peer.domain.PeerGroupStats;
import com.ftd.fraud_transaction_detector.aml.profile.domain.TrustedCustomerProfile;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlTrainingRunRepository;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.HistoricalFeatureMaterializationRepository;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class HistoricalFeatureMaterializationService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalFeatureMaterializationService.class);
    private static final int BATCH_SIZE = 500;

    private final HistoricalFeatureMaterializationRepository materializationRepository;
    private final AmlTrainingRunRepository runRepository;
    private final TransactionRepository transactionRepository;
    private final FeatureContextLoader contextLoader;
    private final FeatureEngineeringService featureEngineeringService;
    private final FeatureVersionProvider featureVersionProvider;
    private final FeaturePersistenceService featurePersistenceService;
    private final AppConfigService appConfigService;

    public HistoricalFeatureMaterializationService(
            HistoricalFeatureMaterializationRepository materializationRepository,
            AmlTrainingRunRepository runRepository,
            TransactionRepository transactionRepository,
            FeatureContextLoader contextLoader,
            FeatureEngineeringService featureEngineeringService,
            FeatureVersionProvider featureVersionProvider,
            FeaturePersistenceService featurePersistenceService,
            AppConfigService appConfigService
    ) {
        this.materializationRepository = materializationRepository;
        this.runRepository = runRepository;
        this.transactionRepository = transactionRepository;
        this.contextLoader = contextLoader;
        this.featureEngineeringService = featureEngineeringService;
        this.featureVersionProvider = featureVersionProvider;
        this.featurePersistenceService = featurePersistenceService;
        this.appConfigService = appConfigService;
    }

    public long materialize(AmlTrainingRun run) {
        if (!featureVersionProvider.currentVersion().equals(run.featureVersion())) {
            throw new IllegalStateException(
                    "Historical feature materialization supports " + featureVersionProvider.currentVersion()
                            + "; requested " + run.featureVersion()
            );
        }
        BigDecimal reportingThreshold = appConfigService.getStructuringReportingThreshold(BigDecimal.valueOf(10_000));

        long totalToMaterialize = materializationRepository.countMissingTransactions(run);
        runRepository.updateProgress(run.trainingRunId(), "MATERIALIZING", 0, totalToMaterialize);

        // Loaded once for the whole run — replaces three history queries per transaction
        MaterializationHistoryIndex historyIndex = materializationRepository.loadAccountHistory(run);
        log.info("Loaded history index for {} accounts", historyIndex.accountCount());

        // Shared across all batches: peer group stats and profile rows repeat heavily
        Map<String, PeerGroupStats> peerStatsCache = new HashMap<>();
        Map<String, Double> peerPercentileCache = new HashMap<>();
        Map<String, Optional<TrustedCustomerProfile>> profileCache = new HashMap<>();

        // Keyset cursor so each batch resumes where the previous one stopped
        LocalDateTime cursorDate = LocalDateTime.of(1, 1, 1, 0, 0);
        long cursorId = 0;

        long materialized = 0;
        while (true) {
            var ids = materializationRepository.findMissingTransactionIds(run, BATCH_SIZE, cursorDate, cursorId);
            if (ids.isEmpty()) break;
            var transactions = transactionRepository.findAllById(ids).stream()
                    .sorted(Comparator.comparing(Transaction::getTransactionDate).thenComparing(Transaction::getId))
                    .toList();
            if (transactions.isEmpty()) {
                throw new IllegalStateException("Missing transactions could not be loaded for feature materialization");
            }
            Transaction lastInBatch = transactions.get(transactions.size() - 1);
            cursorDate = lastInBatch.getTransactionDate();
            cursorId = lastInBatch.getId();

            var vectors = new ArrayList<TransactionFeatureVector>(transactions.size());
            var completedIds = new ArrayList<Long>(transactions.size());
            for (Transaction transaction : transactions) {
                normalize(transaction);
                var vector = featureEngineeringService.calculate(
                        contextLoader.load(
                                transaction, historyIndex, profileCache, peerStatsCache, peerPercentileCache
                        ),
                        run.featureVersion(),
                        reportingThreshold
                );
                vectors.add(vector);
                completedIds.add(transaction.getId());
                materialized++;
            }
            featurePersistenceService.saveAll(vectors);
            materializationRepository.markCompleted(completedIds);
            runRepository.updateProgress(run.trainingRunId(), "MATERIALIZING", materialized, totalToMaterialize);
            log.info("Materialized {}/{} feature rows", materialized, totalToMaterialize);
        }
        materializationRepository.insertMissingLearningEligibility(run);
        return materialized;
    }

    public void normalize(AmlTrainingRun run) {
        materializationRepository.normalizeTransactions(run);
    }

    private void normalize(Transaction transaction) {
        if (transaction.getCustomerId() == null || transaction.getCustomerId().isBlank()) {
            transaction.setCustomerId(transaction.getAccountId());
        }
        if (transaction.getBusinessDate() == null) {
            transaction.setBusinessDate(transaction.getTransactionDate().toLocalDate());
        }
    }
}
