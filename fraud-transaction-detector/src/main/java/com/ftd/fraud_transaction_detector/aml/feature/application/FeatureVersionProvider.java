package com.ftd.fraud_transaction_detector.aml.feature.application;

import org.springframework.stereotype.Component;

@Component
public class FeatureVersionProvider {

    public String currentVersion() {
        return "AML_FEATURES_V2";
    }

    public String generatorVersion() {
        return "spring-aml-features-v2";
    }
}
