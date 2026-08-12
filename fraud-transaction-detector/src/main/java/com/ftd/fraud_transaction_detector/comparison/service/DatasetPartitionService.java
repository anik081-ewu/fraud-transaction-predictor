package com.ftd.fraud_transaction_detector.comparison.service;

import com.ftd.fraud_transaction_detector.comparison.entity.DatasetPartition;
import com.ftd.fraud_transaction_detector.comparison.entity.UploadedDataset;
import com.ftd.fraud_transaction_detector.comparison.repo.DatasetPartitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DatasetPartitionService {

    public static final String ORDERING_STRATEGY_OLDEST_BY_TRANSACTION_DATE = "OLDEST_BY_TRANSACTION_DATE";

    private static final List<Integer> PARTITION_PERCENTAGES = List.of(10, 25, 50, 100);
    public static final double COMMON_HOLDOUT_FRACTION = 0.20;

    private final DatasetPartitionRepository datasetPartitionRepository;

    public DatasetPartitionService(DatasetPartitionRepository datasetPartitionRepository) {
        this.datasetPartitionRepository = datasetPartitionRepository;
    }

    @Transactional
    public List<DatasetPartition> createPartitionsFor(UploadedDataset dataset) {
        List<PartitionTarget> targets = buildPartitionTargets(dataset.getTotalRows());
        List<DatasetPartition> created = new ArrayList<>();
        for (PartitionTarget target : targets) {
            if (datasetPartitionRepository.existsByUploadedDatasetIdAndPartitionSize(dataset.getId(), target.size())) {
                continue;
            }
            DatasetPartition partition = new DatasetPartition();
            partition.setUploadedDataset(dataset);
            partition.setPartitionNo("PT-" + Instant.now().toEpochMilli() + "-" + target.percentage());
            partition.setPartitionLabel("OLDEST_" + target.percentage() + "_PERCENT");
            partition.setPartitionSize(target.size());
            partition.setOrderingStrategy(ORDERING_STRATEGY_OLDEST_BY_TRANSACTION_DATE);
            partition.setStartRowNo(1);
            partition.setEndRowNo(target.size());
            partition.setCreatedAt(Instant.now());
            created.add(datasetPartitionRepository.save(partition));
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<DatasetPartition> listPartitions(Long uploadedDatasetId) {
        return datasetPartitionRepository.findByUploadedDatasetIdOrderByPartitionSizeAscIdAsc(uploadedDatasetId);
    }

    static List<PartitionTarget> buildPartitionTargets(int totalRows) {
        if (totalRows <= 0) {
            return List.of();
        }
        int trainingPoolRows = totalRows - commonHoldoutRows(totalRows);
        Set<Integer> usedSizes = new LinkedHashSet<>();
        List<PartitionTarget> targets = new ArrayList<>();
        for (Integer percentage : PARTITION_PERCENTAGES) {
            int size = percentage == 100
                    ? trainingPoolRows
                    : Math.max(1, (int) Math.floor(trainingPoolRows * (percentage / 100.0)));
            if (usedSizes.add(size)) {
                targets.add(new PartitionTarget(percentage, size));
            }
        }
        return targets;
    }

    public static int commonHoldoutRows(int totalRows) {
        if (totalRows <= 1) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(totalRows * COMMON_HOLDOUT_FRACTION));
    }

    record PartitionTarget(int percentage, int size) {
    }
}
