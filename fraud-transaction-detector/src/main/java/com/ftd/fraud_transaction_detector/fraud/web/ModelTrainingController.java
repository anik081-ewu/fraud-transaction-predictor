package com.ftd.fraud_transaction_detector.fraud.web;

import com.ftd.fraud_transaction_detector.fraud.dto.ScorePercentilesResponse;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainModelResponse;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainingRunResponse;
import com.ftd.fraud_transaction_detector.fraud.service.ModelTrainingService;
import com.ftd.fraud_transaction_detector.fraud.service.ScorePercentilesService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ml/models")
public class ModelTrainingController {

    private final ModelTrainingService modelTrainingService;
    private final ScorePercentilesService scorePercentilesService;

    public ModelTrainingController(ModelTrainingService modelTrainingService, ScorePercentilesService scorePercentilesService) {
        this.modelTrainingService = modelTrainingService;
        this.scorePercentilesService = scorePercentilesService;
    }

    @PostMapping("/train")
    public TrainModelResponse trainModels(@RequestParam(value = "requestedBy", required = false) String requestedBy) {
        return modelTrainingService.trainFromDatabase(requestedBy);
    }

    @GetMapping("/training-runs")
    public Page<TrainingRunResponse> listTrainingRuns(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return modelTrainingService.listTrainingRuns(pageable);
    }

    @PostMapping("/score-percentiles")
    public ScorePercentilesResponse scorePercentiles(@RequestParam(value = "requestedBy", required = false) String requestedBy) {
        return scorePercentilesService.computeFromDatabase(requestedBy);
    }
}
