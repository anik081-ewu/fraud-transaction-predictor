package com.ftd.fraud_transaction_detector.comparison.service;

import com.ftd.fraud_transaction_detector.comparison.dto.ModelVersionResponse;
import com.ftd.fraud_transaction_detector.comparison.entity.ModelVersion;
import com.ftd.fraud_transaction_detector.comparison.repo.ModelVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ModelVersionLifecycleService {

    private final ModelVersionRepository modelVersionRepository;

    public ModelVersionLifecycleService(ModelVersionRepository modelVersionRepository) {
        this.modelVersionRepository = modelVersionRepository;
    }

    @Transactional
    public List<ModelVersionResponse> promoteTrainingRun(Long trainingRunId, String promotedBy) {
        List<ModelVersion> candidates = modelVersionRepository.findByTrainingRunIdOrderByModelNameAsc(trainingRunId);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No model bundle found for training run: " + trainingRunId);
        }
        boolean fullPartition = candidates.stream().allMatch(
                version -> "OLDEST_100_PERCENT".equals(version.getDatasetPartition().getPartitionLabel())
        );
        if (!fullPartition) {
            throw new IllegalArgumentException("Only the 100% training-pool bundle can be promoted");
        }

        Instant promotedAt = Instant.now();
        String actor = promotedBy == null || promotedBy.isBlank() ? "comparison-ui" : promotedBy.trim();
        for (ModelVersion candidate : candidates) {
            for (ModelVersion active : modelVersionRepository.findByModelNameAndIsActiveTrue(candidate.getModelName())) {
                if (!active.getId().equals(candidate.getId())) {
                    active.setIsActive(Boolean.FALSE);
                    active.setLifecycleStatus("RETIRED");
                    modelVersionRepository.save(active);
                }
            }
            candidate.setIsActive(Boolean.TRUE);
            candidate.setLifecycleStatus("PROMOTED");
            candidate.setPromotedAt(promotedAt);
            candidate.setPromotedBy(actor);
            modelVersionRepository.save(candidate);
        }
        return candidates.stream().map(ModelVersionResponse::from).toList();
    }
}
