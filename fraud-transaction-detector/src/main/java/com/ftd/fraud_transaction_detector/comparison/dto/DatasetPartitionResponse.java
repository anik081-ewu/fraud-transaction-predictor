package com.ftd.fraud_transaction_detector.comparison.dto;

import com.ftd.fraud_transaction_detector.comparison.entity.DatasetPartition;

import java.time.Instant;

public record DatasetPartitionResponse(
        Long id,
        String partitionNo,
        String partitionLabel,
        Integer partitionSize,
        String orderingStrategy,
        Integer startRowNo,
        Integer endRowNo,
        Instant createdAt
) {
    public static DatasetPartitionResponse from(DatasetPartition partition) {
        return new DatasetPartitionResponse(
                partition.getId(),
                partition.getPartitionNo(),
                partition.getPartitionLabel(),
                partition.getPartitionSize(),
                partition.getOrderingStrategy(),
                partition.getStartRowNo(),
                partition.getEndRowNo(),
                partition.getCreatedAt()
        );
    }
}
