package com.ftd.fraud_transaction_detector.comparison.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningModelCatalogServiceTest {

    @Test
    void exposesThreeRecommendedModelsPerLearningMode() {
        var catalog = new LearningModelCatalogService().catalog();

        assertEquals(2, catalog.size());
        assertEquals(3, catalog.get(0).models().size());
        assertEquals(3, catalog.get(1).models().size());
        assertTrue(catalog.get(1).labelsRequired());
    }
}
