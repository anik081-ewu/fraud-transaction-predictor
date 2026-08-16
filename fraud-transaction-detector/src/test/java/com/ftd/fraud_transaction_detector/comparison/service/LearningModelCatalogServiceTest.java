package com.ftd.fraud_transaction_detector.comparison.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningModelCatalogServiceTest {

    @Test
    void exposesThreeBaseModelsWithoutTheTemporalStack() {
        var catalog = new LearningModelCatalogService().catalog();

        assertEquals(2, catalog.size());
        assertEquals(3, catalog.get(0).models().size());
        assertEquals(3, catalog.get(1).models().size());
        assertFalse(catalog.get(1).models().stream()
                .anyMatch(model -> "STACKED_ENSEMBLE".equals(model.modelKey())));
        assertTrue(catalog.get(1).labelsRequired());
    }
}
