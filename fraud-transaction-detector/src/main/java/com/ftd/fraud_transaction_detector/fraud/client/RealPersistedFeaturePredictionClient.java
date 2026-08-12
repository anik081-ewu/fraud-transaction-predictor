package com.ftd.fraud_transaction_detector.fraud.client;

import com.ftd.fraud_transaction_detector.comparison.dto.ComparisonPredictResponse;
import com.ftd.fraud_transaction_detector.fraud.config.FraudMlProperties;
import com.ftd.fraud_transaction_detector.fraud.dto.PersistedFeaturePredictRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class RealPersistedFeaturePredictionClient implements PersistedFeaturePredictionClient {

    private static final Logger log = LoggerFactory.getLogger(RealPersistedFeaturePredictionClient.class);

    private final WebClient webClient;

    public RealPersistedFeaturePredictionClient(
            WebClient.Builder webClientBuilder,
            FraudMlProperties properties
    ) {
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public ComparisonPredictResponse predict(PersistedFeaturePredictRequest request) {
        try {
            ComparisonPredictResponse response = webClient.post()
                    .uri("/api/v2/fraud/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ComparisonPredictResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            if (response == null) {
                throw new IllegalStateException("Empty response from persisted-feature prediction API");
            }
            return response;
        } catch (Exception exception) {
            log.warn("Persisted-feature prediction unavailable; legacy compatibility fallback will be used", exception);
            return new ComparisonPredictResponse(
                    request.transactionId(), request.accountId(), Map.of(),
                    request.featureSummary(),
                    List.of("Persisted-feature prediction unavailable")
            );
        }
    }
}
