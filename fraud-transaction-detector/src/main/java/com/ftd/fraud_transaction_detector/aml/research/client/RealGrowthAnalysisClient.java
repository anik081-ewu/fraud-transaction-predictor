package com.ftd.fraud_transaction_detector.aml.research.client;

import com.ftd.fraud_transaction_detector.fraud.config.FraudMlProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class RealGrowthAnalysisClient implements GrowthAnalysisClient {

    private final WebClient webClient;

    public RealGrowthAnalysisClient(WebClient.Builder builder, FraudMlProperties properties) {
        this.webClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public GrowthAnalysisResponse analyze(GrowthAnalysisRequest request) {
        GrowthAnalysisResponse response = webClient.post()
                .uri("/api/v1/aml/research/growth-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("No response body")
                        .map(body -> new IllegalStateException(
                                "Python growth analysis rejected the request: " + body)))
                .bodyToMono(GrowthAnalysisResponse.class)
                .timeout(Duration.ofHours(12))
                .block();
        if (response == null) throw new IllegalStateException("Python returned an empty growth-analysis response");
        return response;
    }
}
