package com.ftd.fraud_transaction_detector.aml.peer.application;

import com.ftd.fraud_transaction_detector.aml.feature.domain.PeerContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TrustedProfileSnapshot;
import com.ftd.fraud_transaction_detector.aml.peer.domain.PeerGroupDefinition;
import com.ftd.fraud_transaction_detector.aml.peer.domain.PeerGroupStats;
import com.ftd.fraud_transaction_detector.aml.peer.infrastructure.PeerGroupStatsRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PeerContextLoaderTest {

    @Test
    void fallsBackFromSparseCompositeGroupToOccupationGroup() {
        PeerGroupAssigner assigner = new PeerGroupAssigner();
        PeerGroupStatsRepository repository = mock(PeerGroupStatsRepository.class);
        PeerContextLoader loader = new PeerContextLoader(assigner, repository);
        LocalDateTime scoringDate = LocalDateTime.of(2026, 8, 11, 10, 0);
        LocalDateTime trainingDate = LocalDateTime.of(2024, 1, 1, 16, 0);
        PeerGroupDefinition exact = assigner.assign("Doctor", 68);
        PeerGroupDefinition medical = assigner.candidates("Doctor", 68).get(1);

        when(repository.findLatestTrainingDataTransactionDate(scoringDate))
                .thenReturn(Optional.of(trainingDate));
        when(repository.findStats(exact, trainingDate)).thenReturn(Optional.empty());
        when(repository.findStats(medical, trainingDate)).thenReturn(Optional.of(
                new PeerGroupStats(2_000, 1_500, 500, 50_000, 25)
        ));
        when(repository.findFrequencyPercentile("AC00455", medical, trainingDate))
                .thenReturn(Optional.of(0.80));

        PeerContext result = loader.load(
                "Doctor", 68, "AC00455", TrustedProfileSnapshot.empty(), scoringDate
        );

        assertEquals("MEDICAL", result.peerGroupCode());
        assertEquals(2_000.0, result.averageAmount(), 0.000001);
        assertEquals(0.80, result.frequencyPercentile(), 0.000001);
    }
}
