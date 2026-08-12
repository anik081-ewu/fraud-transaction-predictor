package com.ftd.fraud_transaction_detector.aml.training.client;

import com.ftd.fraud_transaction_detector.fraud.config.FraudMlProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class RealIncrementalTrainingClient implements IncrementalTrainingClient {

    private final WebClient webClient;

    public RealIncrementalTrainingClient(WebClient.Builder builder, FraudMlProperties properties) {
        this.webClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public IncrementalTrainingResponse train(IncrementalTrainingRequest request) {
        IncrementalTrainingResponse response = webClient.post()
                .uri("/api/v1/aml/training/incremental")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(IncrementalTrainingResponse.class)
                .timeout(Duration.ofHours(12))
                .block();
        if (response == null) throw new IllegalStateException("Python returned an empty incremental-training response");
        return response;
    }
}
