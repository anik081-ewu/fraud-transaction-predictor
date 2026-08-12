package com.ftd.fraud_transaction_detector.aml.training.application;

import com.ftd.fraud_transaction_detector.aml.training.api.RegisterBatchCandidateRequest;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlTrainingRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers one batch model in its own transaction.
 *
 * REQUIRES_NEW matters here: the caller runs inside a transaction, so without a separate one
 * a single failed registration would mark the caller's transaction rollback-only and take the
 * whole pipeline down at commit — even though the models are trained and already serving.
 * The sibling run is created inside this same new transaction so it never reads uncommitted
 * state from the caller.
 */
@Service
public class BatchCandidateRegistrar {

    private final AmlTrainingRunRepository runRepository;
    private final AmlModelRegistryService registryService;

    public BatchCandidateRegistrar(
            AmlTrainingRunRepository runRepository,
            AmlModelRegistryService registryService
    ) {
        this.runRepository = runRepository;
        this.registryService = registryService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AmlModelRegistryEntry register(
            AmlTrainingRun snapshot,
            String modelType,
            RegisterBatchCandidateRequest request
    ) {
        AmlTrainingRun modelRun = runRepository.createReadySibling(snapshot, modelType);
        registryService.startTraining(modelRun.trainingRunId(), null);
        return registryService.registerBatchCandidate(modelRun.trainingRunId(), request);
    }
}
