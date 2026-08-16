package com.ftd.fraud_transaction_detector.comparison.service;

import com.ftd.fraud_transaction_detector.comparison.dto.LearningModelCatalogResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LearningModelCatalogService {

    public List<LearningModelCatalogResponse> catalog() {
        return List.of(
                new LearningModelCatalogResponse(
                        "UNSUPERVISED", false,
                        List.of("EXCESS_MASS_AUC", "SCORE_SEPARATION", "ANOMALY_RATE_STABILITY", "THROUGHPUT"),
                        List.of(
                                model("ISOLATION_FOREST", "Isolation Forest", "UNSUPERVISED", "Broad nonlinear anomaly isolation"),
                                model("AUTOENCODER", "Autoencoder", "UNSUPERVISED", "Complex feature reconstruction anomalies"),
                                model("BEHAVIORAL_CLUSTER_OUTLIER", "Behavioral Cluster Outlier", "UNSUPERVISED", "Cluster-conditional behavioural anomaly detection")
                        )
                ),
                new LearningModelCatalogResponse(
                        "SUPERVISED", true,
                        List.of("PR_AUC", "PRECISION", "RECALL", "F1", "BRIER_SCORE", "THROUGHPUT"),
                        List.of(
                                model("XGBOOST_CLASSIFIER", "XGBoost", "SUPERVISED", "High-performance nonlinear fraud classification"),
                                model("RANDOM_FOREST_CLASSIFIER", "Class-Balanced Random Forest", "SUPERVISED", "Bootstrap-balanced nonlinear fraud classification"),
                                model("EXTRA_TREES_CLASSIFIER", "Extra Trees", "SUPERVISED", "High-variance randomized trees for complementary fraud patterns")
                        )
                )
        );
    }

    private LearningModelCatalogResponse.LearningModelResponse model(
            String key, String name, String trainingStyle, String purpose
    ) {
        return new LearningModelCatalogResponse.LearningModelResponse(
                key, name, trainingStyle, true, purpose
        );
    }
}
