package com.ftd.fraud_transaction_detector.aml.peer.application;

import com.ftd.fraud_transaction_detector.aml.peer.domain.PeerGroupDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeerGroupAssignerTest {

    private final PeerGroupAssigner assigner = new PeerGroupAssigner();

    @Test
    void combinesMedicalOccupationWithSeniorAgeInsteadOfForcingRetiree() {
        List<String> groups = assigner.candidates("Doctor", 68).stream()
                .map(PeerGroupDefinition::code)
                .toList();

        assertEquals(List.of("MEDICAL_AGE_66_PLUS", "MEDICAL", "AGE_66_PLUS", "GLOBAL"), groups);
        assertEquals("MEDICAL_AGE_66_PLUS", assigner.assign("Doctor", 68).code());
    }

    @Test
    void fallsBackToAgeWhenOccupationCannotBeNormalized() {
        List<String> groups = assigner.candidates("Unknown occupation", 68).stream()
                .map(PeerGroupDefinition::code)
                .toList();

        assertEquals(List.of("AGE_66_PLUS", "GLOBAL"), groups);
    }

    @Test
    void recognizesExplicitRetirementAsAnOccupationCategory() {
        assertEquals("RETIREE_AGE_66_PLUS", assigner.assign("Retired doctor", 68).code());
    }
}
