package com.ftd.fraud_transaction_detector.fraud.client;

import com.ftd.fraud_transaction_detector.fraud.config.FraudMlProperties;
import com.ftd.fraud_transaction_detector.fraud.dto.ScorePercentilesRequest;
import com.ftd.fraud_transaction_detector.fraud.dto.ScorePercentilesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Service
public class RealScorePercentilesClient implements ScorePercentilesClient {

    private static final Logger log = LoggerFactory.getLogger(RealScorePercentilesClient.class);

    private final WebClient webClient;

    public RealScorePercentilesClient(WebClient.Builder webClientBuilder, FraudMlProperties properties) {
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public ScorePercentilesResponse compute(ScorePercentilesRequest request) {
        try {
            ScorePercentilesResponse response = webClient.post()
                    .uri("/api/v1/models/score-percentiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchangeToMono(this::mapResponse)
                    .timeout(Duration.ofSeconds(60))
                    .block();
            if (response == null) {
                throw new IllegalStateException("Empty response from ML service");
            }
            return response;
        } catch (Exception ex) {
            log.error("Score percentiles call failed: {}", ex.getMessage(), ex);
            return new ScorePercentilesResponse(List.of(), List.of(), List.of());
        }
    }

    private reactor.core.publisher.Mono<ScorePercentilesResponse> mapResponse(ClientResponse response) {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(ScorePercentilesResponse.class);
        }
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    log.error("ML service returned {} for score percentiles: {}", response.statusCode().value(), body);
                    return new ScorePercentilesResponse(List.of(), List.of(), List.of());
                });
    }
}

