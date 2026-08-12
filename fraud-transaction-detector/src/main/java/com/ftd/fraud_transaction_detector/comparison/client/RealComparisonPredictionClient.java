package com.ftd.fraud_transaction_detector.comparison.client;

import com.ftd.fraud_transaction_detector.comparison.dto.ComparisonPredictRequest;
import com.ftd.fraud_transaction_detector.comparison.dto.ComparisonPredictResponse;
import com.ftd.fraud_transaction_detector.fraud.config.FraudMlProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class RealComparisonPredictionClient implements ComparisonPredictionClient {

    private static final Logger log = LoggerFactory.getLogger(RealComparisonPredictionClient.class);

    private final WebClient webClient;

    public RealComparisonPredictionClient(WebClient.Builder webClientBuilder, FraudMlProperties properties) {
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public ComparisonPredictResponse compare(ComparisonPredictRequest request) {
        try {
            ComparisonPredictResponse response = webClient.post()
                    .uri("/api/v1/fraud/compare")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ComparisonPredictResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            if (response == null) {
                throw new IllegalStateException("Empty response from Fraud ML compare API");
            }
            return response;
        } catch (Exception ex) {
            log.error("Fraud ML comparison API unavailable", ex);
            return new ComparisonPredictResponse(
                    request.transaction().transactionId(),
                    request.transaction().accountId(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    List.of("Comparison prediction unavailable: " + ex.getMessage())
            );
        }
    }
}
