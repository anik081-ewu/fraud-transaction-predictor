package com.ftd.fraud_transaction_detector.aml.research.client;

import com.ftd.fraud_transaction_detector.fraud.config.FraudMlProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Component
public class RealModelAgreementClient {

    private final WebClient webClient;

    public RealModelAgreementClient(WebClient.Builder builder, FraudMlProperties properties) {
        this.webClient = builder.baseUrl(properties.baseUrl()).build();
    }

    /** Scoring a whole snapshot with every model takes minutes, hence the generous timeout. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> analyze(Map<String, Object> request) {
        Map<String, Object> response = webClient.post()
                .uri("/api/v1/aml/model-agreement")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse -> clientResponse
                        .bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new IllegalStateException(
                                "Model agreement analysis rejected: " + body))))
                .bodyToMono(Map.class)
                .timeout(Duration.ofHours(1))
                .block();
        if (response == null) {
            throw new IllegalStateException("Empty model-agreement response");
        }
        return response;
    }
}
