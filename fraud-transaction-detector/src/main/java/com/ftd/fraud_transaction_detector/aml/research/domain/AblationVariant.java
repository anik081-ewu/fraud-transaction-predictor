package com.ftd.fraud_transaction_detector.aml.research.domain;

import java.util.EnumSet;
import java.util.Set;

public enum AblationVariant {
    FULL(LayerComponent.values()),
    WITHOUT_RULES(LayerComponent.CUSTOMER, LayerComponent.PEER, LayerComponent.ML_ENSEMBLE),
    WITHOUT_CUSTOMER_BEHAVIOUR(LayerComponent.PEER, LayerComponent.ML_ENSEMBLE, LayerComponent.RULES),
    WITHOUT_PEER_BEHAVIOUR(LayerComponent.CUSTOMER, LayerComponent.ML_ENSEMBLE, LayerComponent.RULES),
    WITHOUT_ML_ENSEMBLE(LayerComponent.CUSTOMER, LayerComponent.PEER, LayerComponent.RULES),
    RULES_ONLY(LayerComponent.RULES),
    ML_ONLY(LayerComponent.ML_ENSEMBLE),
    BEHAVIOUR_ONLY(LayerComponent.CUSTOMER, LayerComponent.PEER);

    private final Set<LayerComponent> included;

    AblationVariant(LayerComponent... included) {
        this.included = included.length == 0
                ? EnumSet.noneOf(LayerComponent.class) : EnumSet.of(included[0], included);
    }

    public boolean includes(LayerComponent component) {
        return included.contains(component);
    }

    public enum LayerComponent { CUSTOMER, PEER, ML_ENSEMBLE, RULES }
}
