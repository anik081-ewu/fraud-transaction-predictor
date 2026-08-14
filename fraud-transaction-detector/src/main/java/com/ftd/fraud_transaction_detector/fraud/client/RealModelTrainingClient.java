package com.ftd.fraud_transaction_detector.fraud.client;

import com.ftd.fraud_transaction_detector.fraud.config.FraudMlProperties;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainModelRequest;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class RealModelTrainingClient implements ModelTrainingClient {

    private static final Logger log = LoggerFactory.getLogger(RealModelTrainingClient.class);

    private final WebClient webClient;
    private final Duration trainingTimeout;

    public RealModelTrainingClient(WebClient.Builder webClientBuilder, FraudMlProperties properties) {
        this.webClient = webClientBuilder
                .baseUrl(properties.baseUrl())
                .build();
        this.trainingTimeout = Duration.ofSeconds(properties.trainingTimeoutSecondsOrDefault());
    }

    @Override
    public TrainModelResponse train(TrainModelRequest request) {
        try {
            log.info("Training {} model(s) on {} rows (timeout {}s)",
                    request.modelNames() == null ? 0 : request.modelNames().size(),
                    request.transactions() == null ? 0 : request.transactions().size(),
                    trainingTimeout.toSeconds());
            TrainModelResponse response = webClient.post()
                    .uri("/api/v1/models/train")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchangeToMono(this::mapResponse)
                    .timeout(trainingTimeout)
                    .block();
            if (response == null) {
                throw new IllegalStateException("Empty response from ML service");
            }
            return response;
        } catch (Exception ex) {
            log.error("Model training call failed: {}", ex.getMessage());
            return new TrainModelResponse(
                    "FAILED",
                    "ML service unavailable: " + ex.getMessage(),
                    0,
                    0,
                    List.of("IsolationForest", "LocalOutlierFactor", "OneClassSVM"),
                    Map.of(),
                    null,
                    Map.of()
            );
        }
    }

    private reactor.core.publisher.Mono<TrainModelResponse> mapResponse(ClientResponse response) {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(TrainModelResponse.class);
        }
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new TrainModelResponse(
                        "FAILED",
                        "ML service returned " + response.statusCode().value() + ": " + body,
                        0,
                        0,
                        List.of("IsolationForest", "LocalOutlierFactor", "OneClassSVM"),
                        Map.of(),
                        null,
                        Map.of()
                ));
    }
}
