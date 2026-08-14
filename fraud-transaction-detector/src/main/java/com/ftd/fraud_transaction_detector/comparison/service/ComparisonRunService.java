package com.ftd.fraud_transaction_detector.comparison.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.comparison.client.ComparisonPredictionClient;
import com.ftd.fraud_transaction_detector.comparison.dto.*;
import com.ftd.fraud_transaction_detector.comparison.entity.*;
import com.ftd.fraud_transaction_detector.comparison.repo.*;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ComparisonRunService {

    private final UploadedDatasetRepository uploadedDatasetRepository;
    private final ScenarioSetRepository scenarioSetRepository;
    private final DatasetPartitionRepository datasetPartitionRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ComparisonRunRepository comparisonRunRepository;
    private final ComparisonResultRepository comparisonResultRepository;
    private final ScenarioLibraryService scenarioLibraryService;
    private final ComparisonPredictionClient comparisonPredictionClient;
    private final ObjectMapper objectMapper;

    public ComparisonRunService(
            UploadedDatasetRepository uploadedDatasetRepository,
            ScenarioSetRepository scenarioSetRepository,
            DatasetPartitionRepository datasetPartitionRepository,
            ModelVersionRepository modelVersionRepository,
            ComparisonRunRepository comparisonRunRepository,
            ComparisonResultRepository comparisonResultRepository,
            ScenarioLibraryService scenarioLibraryService,
            ComparisonPredictionClient comparisonPredictionClient,
            ObjectMapper objectMapper
    ) {
        this.uploadedDatasetRepository = uploadedDatasetRepository;
        this.scenarioSetRepository = scenarioSetRepository;
        this.datasetPartitionRepository = datasetPartitionRepository;
        this.modelVersionRepository = modelVersionRepository;
        this.comparisonRunRepository = comparisonRunRepository;
        this.comparisonResultRepository = comparisonResultRepository;
        this.scenarioLibraryService = scenarioLibraryService;
        this.comparisonPredictionClient = comparisonPredictionClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ComparisonRunResponse createAndExecute(ComparisonRunCreateRequest request) {
        if (request == null || request.uploadedDatasetId() == null) {
            throw new IllegalArgumentException("uploadedDatasetId is required");
        }
        if (request.scenarioSetId() == null) {
            throw new IllegalArgumentException("scenarioSetId is required");
        }
        if (request.partitionIds() == null || request.partitionIds().isEmpty()) {
            throw new IllegalArgumentException("At least one partitionId is required");
        }

        UploadedDataset dataset = uploadedDatasetRepository.findById(request.uploadedDatasetId())
                .orElseThrow(() -> new IllegalArgumentException("Uploaded dataset not found: " + request.uploadedDatasetId()));
        ScenarioSet scenarioSet = scenarioSetRepository.findById(request.scenarioSetId())
                .orElseThrow(() -> new IllegalArgumentException("Scenario set not found: " + request.scenarioSetId()));

        List<DatasetPartition> partitions = request.partitionIds().stream()
                .map(partitionId -> datasetPartitionRepository.findById(partitionId)
                        .orElseThrow(() -> new IllegalArgumentException("Dataset partition not found: " + partitionId)))
                .toList();

        List<ComparisonScenario> scenarios = scenarioLibraryService.findScenarioEntities(scenarioSet.getId());
        if (scenarios.isEmpty()) {
            throw new IllegalArgumentException("Scenario set has no scenarios: " + scenarioSet.getId());
        }

        String requestedBy = request.requestedBy() == null || request.requestedBy().isBlank()
                ? "comparison-ui"
                : request.requestedBy().trim();

        ComparisonRun run = new ComparisonRun();
        Instant now = Instant.now();
        run.setComparisonRunNo("CMPRUN-" + now.toEpochMilli());
        run.setUploadedDataset(dataset);
        run.setScenarioSet(scenarioSet);
        run.setSelectedPartitionSizes(partitions.stream()
                .map(DatasetPartition::getPartitionSize)
                .map(String::valueOf)
                .collect(Collectors.joining(",")));
        run.setSelectedModels(String.join(",", normalizeModelNames(request.modelNames())));
        run.setRunStatus("RUNNING");
        run.setRequestedBy(requestedBy);
        run.setStartedAt(now);
        run.setCreatedAt(now);
        comparisonRunRepository.save(run);

        try {
            executeRun(run, partitions, scenarios, normalizeModelNames(request.modelNames()));
            run.setRunStatus("SUCCESS");
            run.setCompletedAt(Instant.now());
            comparisonRunRepository.save(run);
            return ComparisonRunResponse.from(run);
        } catch (RuntimeException ex) {
            run.setRunStatus("FAILED");
            run.setCompletedAt(Instant.now());
            comparisonRunRepository.save(run);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<ComparisonRunResponse> listRuns() {
        return comparisonRunRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(ComparisonRunResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ComparisonRunDetailResponse getRunDetail(Long comparisonRunId) {
        ComparisonRun run = comparisonRunRepository.findById(comparisonRunId)
                .orElseThrow(() -> new IllegalArgumentException("Comparison run not found: " + comparisonRunId));
        List<ComparisonResultResponse> results = comparisonResultRepository.findByComparisonRunIdOrderByDatasetPartitionIdAscScenarioIdAscModelNameAsc(comparisonRunId)
                .stream()
                .map(ComparisonResultResponse::from)
                .toList();
        return new ComparisonRunDetailResponse(
                ComparisonRunResponse.from(run),
                results,
                buildSummary(results)
        );
    }

    private void executeRun(
            ComparisonRun run,
            List<DatasetPartition> partitions,
            List<ComparisonScenario> scenarios,
            List<String> requestedModels
    ) {
        for (DatasetPartition partition : partitions) {
            PartitionModelBundle bundle = resolveLatestBundle(partition.getId(), requestedModels);
            for (ComparisonScenario scenario : scenarios) {
                FraudPredictionRequest.TransactionDto transaction = scenarioLibraryService.readTransaction(scenario);
                FraudPredictionRequest.CustomerDto customer = scenarioLibraryService.readCustomer(scenario);
                FraudPredictionRequest.AccountProfileDto accountProfile = scenarioLibraryService.readAccountProfile(scenario);

                ComparisonPredictResponse response = comparisonPredictionClient.compare(
                        new ComparisonPredictRequest(
                                transaction,
                                customer,
                                accountProfile,
                                bundle.artifactBasePath(),
                                bundle.modelNames()
                        )
                );
                saveResults(run, scenario, partition, bundle, response);
            }
        }
    }

    private PartitionModelBundle resolveLatestBundle(Long partitionId, List<String> requestedModels) {
        List<ModelVersion> versions = modelVersionRepository.findByDatasetPartitionIdOrderByCreatedAtDescIdDesc(partitionId);
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("No trained model versions found for partition: " + partitionId);
        }
        String artifactBasePath = versions.get(0).getArtifactBasePath();
        Map<String, ModelVersion> byModel = new LinkedHashMap<>();
        for (ModelVersion version : versions) {
            if (!artifactBasePath.equals(version.getArtifactBasePath())) {
                continue;
            }
            byModel.putIfAbsent(version.getModelName(), version);
        }
        List<String> modelNames = requestedModels.stream()
                .filter(byModel::containsKey)
                .toList();
        if (modelNames.isEmpty()) {
            throw new IllegalArgumentException("Requested models not found for partition: " + partitionId);
        }
        return new PartitionModelBundle(artifactBasePath, modelNames, byModel);
    }

    private void saveResults(
            ComparisonRun run,
            ComparisonScenario scenario,
            DatasetPartition partition,
            PartitionModelBundle bundle,
            ComparisonPredictResponse response
    ) {
        for (String modelName : bundle.modelNames()) {
            ModelVersion modelVersion = bundle.byModel().get(modelName);
            Map<String, Object> modelResult = response.modelResults().get(modelName);
            if (modelResult == null) {
                continue;
            }
            ComparisonResult result = new ComparisonResult();
            result.setComparisonRun(run);
            result.setScenario(scenario);
            result.setDatasetPartition(partition);
            result.setModelVersion(modelVersion);
            result.setModelName(modelName);
            result.setRawPrediction(readInteger(modelResult.get("rawPrediction")));
            result.setAnomalyVote(Boolean.TRUE.equals(modelResult.get("anomaly")) ? 1 : 0);
            result.setRiskLevel(Boolean.TRUE.equals(modelResult.get("anomaly")) ? "LOW" : "NORMAL");
            result.setSuspicious(Boolean.TRUE.equals(modelResult.get("anomaly")));
            result.setRecommendedAction(Boolean.TRUE.equals(modelResult.get("anomaly")) ? "ALLOW_AND_LOG" : "ALLOW");
            result.setScoreValue(readDouble(modelResult.get("scoreSamples")));
            result.setDecisionValue(readDouble(modelResult.get("decisionFunction")));
            result.setReasonsJson(toJsonSafe(response.reasons()));
            result.setResponseJson(toJsonSafe(modelResult));
            result.setPredictionDurationMs(readLong(modelResult.get("predictionDurationMs")));
            result.setCreatedAt(Instant.now());
            comparisonResultRepository.save(result);
        }
    }

    private static Integer readInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private static Long readLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static Double readDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }

    private static List<String> normalizeModelNames(List<String> modelNames) {
        List<String> defaults = List.of("IsolationForest", "Autoencoder", "LOF");
        if (modelNames == null || modelNames.isEmpty()) {
            return defaults;
        }
        return modelNames.stream()
                .map(modelName -> modelName == null ? "" : modelName.trim())
                .filter(modelName -> !modelName.isBlank())
                .distinct()
                .toList();
    }

    private Map<String, Object> buildSummary(List<ComparisonResultResponse> results) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalResults", results.size());
        summary.put("methodology", Map.of(
                "name", "Label-free temporal robustness assessment",
                "groundTruthAvailable", false,
                "qualityWeight", 0.45,
                "growthStabilityWeight", 0.30,
                "crossModelAgreementWeight", 0.15,
                "latencyWeight", 0.10,
                "caveat", "Synthetic-anomaly proxy metrics are model-selection evidence, not real fraud accuracy."
        ));
        if (results.isEmpty()) {
            summary.put("modelLeaderboard", List.of());
            summary.put("growthSeries", List.of());
            summary.put("agreementMatrix", List.of());
            return summary;
        }

        Map<Long, Integer> partitionSizes = datasetPartitionRepository.findAllById(
                        results.stream().map(ComparisonResultResponse::datasetPartitionId).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(DatasetPartition::getId, DatasetPartition::getPartitionSize));

        Map<Long, ModelVersion> versions = modelVersionRepository.findAllById(
                        results.stream().map(ComparisonResultResponse::modelVersionId).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(ModelVersion::getId, version -> version));

        Map<String, List<ComparisonResultResponse>> resultsByModel = results.stream()
                .collect(Collectors.groupingBy(ComparisonResultResponse::modelName, LinkedHashMap::new, Collectors.toList()));
        double minimumLatency = resultsByModel.values().stream()
                .mapToDouble(this::averageLatency)
                .filter(value -> value > 0)
                .min()
                .orElse(1.0);

        List<Map<String, Object>> leaderboard = new ArrayList<>();
        for (Map.Entry<String, List<ComparisonResultResponse>> entry : resultsByModel.entrySet()) {
            String modelName = entry.getKey();
            List<ComparisonResultResponse> modelResults = entry.getValue();
            MetricAverage quality = averageTrainingMetric(modelResults, versions, "qualityScore");
            MetricAverage trainingStability = averageTrainingMetric(modelResults, versions, "stabilityScore");
            double growthStability = calculateGrowthStability(modelResults, partitionSizes);
            double agreement = calculateCrossModelAgreement(modelName, results);
            double averageLatency = averageLatency(modelResults);
            double latencyScore = averageLatency <= 0 ? 50.0 : Math.min(100.0, 100.0 * minimumLatency / averageLatency);
            double anomalyRate = modelResults.stream().mapToInt(result -> safeVote(result.anomalyVote())).average().orElse(0.0);
            double qualityScore = quality.available() ? quality.value() : 50.0;
            double overallScore = 0.45 * qualityScore + 0.30 * growthStability + 0.15 * agreement + 0.10 * latencyScore;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("modelName", modelName);
            item.put("overallScore", round(overallScore));
            item.put("qualityScore", round(qualityScore));
            item.put("qualityMetricAvailable", quality.available());
            item.put("trainingStabilityScore", round(trainingStability.available() ? trainingStability.value() : 0.0));
            item.put("growthStabilityScore", round(growthStability));
            item.put("agreementScore", round(agreement));
            item.put("latencyScore", round(latencyScore));
            item.put("averageLatencyMs", round(averageLatency));
            item.put("anomalyRate", round(anomalyRate));
            item.put("partitionCount", modelResults.stream().map(ComparisonResultResponse::datasetPartitionId).distinct().count());
            item.put("scenarioCount", modelResults.stream().map(ComparisonResultResponse::scenarioId).distinct().count());
            leaderboard.add(item);
        }
        leaderboard.sort(Comparator.comparingDouble(item -> -readMapDouble(item, "overallScore")));
        for (int index = 0; index < leaderboard.size(); index++) {
            Map<String, Object> item = leaderboard.get(index);
            item.put("rank", index + 1);
            item.put("recommendation", index == 0 ? "BEST_FIT" : index < 3 ? "ENSEMBLE_CANDIDATE" : "SECONDARY");
        }
        summary.put("modelLeaderboard", leaderboard);
        summary.put("bestModel", leaderboard.get(0).get("modelName"));
        summary.put("bestModelScore", leaderboard.get(0).get("overallScore"));
        summary.put("recommendationConfidence", recommendationConfidence(leaderboard, partitionSizes.size()));
        summary.put("growthSeries", buildGrowthSeries(resultsByModel, partitionSizes));
        summary.put("agreementMatrix", buildAgreementMatrix(resultsByModel, results));
        summary.put("partitionSizes", partitionSizes.values().stream().sorted().toList());
        return summary;
    }

    private List<Map<String, Object>> buildGrowthSeries(
            Map<String, List<ComparisonResultResponse>> resultsByModel,
            Map<Long, Integer> partitionSizes
    ) {
        List<Map<String, Object>> series = new ArrayList<>();
        resultsByModel.forEach((modelName, modelResults) -> {
            Map<Long, List<ComparisonResultResponse>> byPartition = modelResults.stream()
                    .collect(Collectors.groupingBy(ComparisonResultResponse::datasetPartitionId));
            List<Map<String, Object>> points = byPartition.entrySet().stream()
                    .map(entry -> {
                        List<ComparisonResultResponse> partitionResults = entry.getValue();
                        Map<String, Object> point = new LinkedHashMap<>();
                        point.put("partitionId", entry.getKey());
                        point.put("partitionSize", partitionSizes.getOrDefault(entry.getKey(), 0));
                        point.put("anomalyRate", round(partitionResults.stream()
                                .mapToInt(result -> safeVote(result.anomalyVote())).average().orElse(0.0)));
                        point.put("averageDecision", round(partitionResults.stream()
                                .map(ComparisonResultResponse::decisionValue)
                                .filter(Objects::nonNull)
                                .mapToDouble(Double::doubleValue)
                                .average().orElse(0.0)));
                        return point;
                    })
                    .sorted(Comparator.comparingInt(point -> ((Number) point.get("partitionSize")).intValue()))
                    .toList();
            series.add(Map.of("modelName", modelName, "points", points));
        });
        return series;
    }

    private List<Map<String, Object>> buildAgreementMatrix(
            Map<String, List<ComparisonResultResponse>> resultsByModel,
            List<ComparisonResultResponse> allResults
    ) {
        List<String> modelNames = new ArrayList<>(resultsByModel.keySet());
        List<Map<String, Object>> matrix = new ArrayList<>();
        for (String left : modelNames) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("modelName", left);
            Map<String, Double> agreements = new LinkedHashMap<>();
            for (String right : modelNames) {
                agreements.put(right, round(pairAgreement(left, right, allResults)));
            }
            row.put("agreements", agreements);
            matrix.add(row);
        }
        return matrix;
    }

    private double calculateGrowthStability(
            List<ComparisonResultResponse> modelResults,
            Map<Long, Integer> partitionSizes
    ) {
        Map<Long, List<ComparisonResultResponse>> byScenario = modelResults.stream()
                .collect(Collectors.groupingBy(ComparisonResultResponse::scenarioId));
        return byScenario.values().stream().mapToDouble(scenarioResults -> {
            ComparisonResultResponse largest = scenarioResults.stream()
                    .max(Comparator.comparingInt(result -> partitionSizes.getOrDefault(result.datasetPartitionId(), 0)))
                    .orElseThrow();
            int referenceVote = safeVote(largest.anomalyVote());
            return 100.0 * scenarioResults.stream()
                    .filter(result -> safeVote(result.anomalyVote()) == referenceVote)
                    .count() / scenarioResults.size();
        }).average().orElse(0.0);
    }

    private double calculateCrossModelAgreement(String modelName, List<ComparisonResultResponse> allResults) {
        List<String> peers = allResults.stream().map(ComparisonResultResponse::modelName)
                .filter(name -> !name.equals(modelName)).distinct().toList();
        if (peers.isEmpty()) return 100.0;
        return peers.stream().mapToDouble(peer -> pairAgreement(modelName, peer, allResults)).average().orElse(0.0);
    }

    private double pairAgreement(String left, String right, List<ComparisonResultResponse> allResults) {
        if (left.equals(right)) return 100.0;
        Map<String, Integer> leftVotes = allResults.stream()
                .filter(result -> result.modelName().equals(left))
                .collect(Collectors.toMap(this::comparisonKey, result -> safeVote(result.anomalyVote()), (first, ignored) -> first));
        int matches = 0;
        int compared = 0;
        for (ComparisonResultResponse result : allResults) {
            if (!result.modelName().equals(right)) continue;
            Integer leftVote = leftVotes.get(comparisonKey(result));
            if (leftVote == null) continue;
            compared++;
            if (leftVote == safeVote(result.anomalyVote())) matches++;
        }
        return compared == 0 ? 0.0 : 100.0 * matches / compared;
    }

    private String comparisonKey(ComparisonResultResponse result) {
        return result.datasetPartitionId() + ":" + result.scenarioId();
    }

    private MetricAverage averageTrainingMetric(
            List<ComparisonResultResponse> results,
            Map<Long, ModelVersion> versions,
            String metricName
    ) {
        Set<Long> visited = new HashSet<>();
        DoubleSummaryStatistics statistics = new DoubleSummaryStatistics();
        for (ComparisonResultResponse result : results) {
            if (!visited.add(result.modelVersionId())) continue;
            ModelVersion version = versions.get(result.modelVersionId());
            Double value = version == null ? null : readMetric(version.getMetricsJson(), metricName);
            if (value != null) statistics.accept(value);
        }
        return statistics.getCount() == 0
                ? new MetricAverage(0.0, false)
                : new MetricAverage(statistics.getAverage(), true);
    }

    private Double readMetric(String metricsJson, String metricName) {
        if (metricsJson == null || metricsJson.isBlank()) return null;
        try {
            Map<String, Object> metrics = objectMapper.readValue(metricsJson, new TypeReference<>() {});
            Object value = metrics.get(metricName);
            return value instanceof Number number ? number.doubleValue() : null;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private double averageLatency(List<ComparisonResultResponse> results) {
        return results.stream()
                .map(ComparisonResultResponse::predictionDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
    }

    private String recommendationConfidence(List<Map<String, Object>> leaderboard, int partitionCount) {
        if (leaderboard.size() < 2 || partitionCount < 2) return "LOW";
        double margin = readMapDouble(leaderboard.get(0), "overallScore")
                - readMapDouble(leaderboard.get(1), "overallScore");
        boolean qualityAvailable = Boolean.TRUE.equals(leaderboard.get(0).get("qualityMetricAvailable"));
        if (qualityAvailable && partitionCount >= 3 && margin >= 8.0) return "HIGH";
        if (qualityAvailable && margin >= 3.0) return "MEDIUM";
        return "LOW";
    }

    private static int safeVote(Integer vote) {
        return vote == null ? 0 : vote;
    }

    private static double readMapDouble(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record MetricAverage(double value, boolean available) {
    }

    private String toJsonSafe(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":\"" + ex.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private record PartitionModelBundle(
            String artifactBasePath,
            List<String> modelNames,
            Map<String, ModelVersion> byModel
    ) {
    }
}
