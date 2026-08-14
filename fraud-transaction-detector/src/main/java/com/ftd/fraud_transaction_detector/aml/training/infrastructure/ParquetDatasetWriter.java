package com.ftd.fraud_transaction_detector.aml.training.infrastructure;

import com.ftd.fraud_transaction_detector.aml.training.domain.ExportFeatureRow;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class ParquetDatasetWriter {

    private static final Schema SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "AmlTransactionFeature",
              "namespace": "com.ftd.aml.dataset",
              "fields": [
                {"name":"transaction_id","type":"string"},
                {"name":"customer_id","type":"string"},
                {"name":"account_id","type":"string"},
                {"name":"business_date","type":{"type":"int","logicalType":"date"}},
                {"name":"transaction_date","type":{"type":"long","logicalType":"timestamp-micros"}},
                {"name":"feature_version","type":"string"},
                {"name":"model_feature_schema","type":"string"},
                {"name":"model_features_json","type":"string"},
                {"name":"current_amount","type":"double"},
                {"name":"current_balance","type":["null","double"],"default":null},
                {"name":"amount_balance_ratio","type":["null","double"],"default":null},
                {"name":"transaction_hour","type":"int"},
                {"name":"transaction_day_of_week","type":"int"},
                {"name":"is_night","type":"boolean"},
                {"name":"is_weekend","type":"boolean"},
                {"name":"customer_history_count","type":"long"},
                {"name":"trusted_history_count","type":"long"},
                {"name":"recent_transaction_count","type":"int"},
                {"name":"profile_confidence","type":"double"},
                {"name":"last_30_avg_amount","type":["null","double"],"default":null},
                {"name":"last_30_median_amount","type":["null","double"],"default":null},
                {"name":"last_30_std_amount","type":["null","double"],"default":null},
                {"name":"amount_vs_last_30_avg","type":["null","double"],"default":null},
                {"name":"amount_vs_last_30_median","type":["null","double"],"default":null},
                {"name":"amount_z_score_last_30","type":["null","double"],"default":null},
                {"name":"transaction_count_1h","type":"int"},
                {"name":"transaction_count_24h","type":"int"},
                {"name":"transaction_count_7d","type":"int"},
                {"name":"transaction_count_30d","type":"int"},
                {"name":"amount_sum_24h","type":"double"},
                {"name":"amount_sum_7d","type":"double"},
                {"name":"amount_sum_30d","type":"double"},
                {"name":"new_beneficiary","type":"boolean"},
                {"name":"new_location","type":"boolean"},
                {"name":"new_channel","type":"boolean"},
                {"name":"new_device","type":"boolean"},
                {"name":"unusual_transaction_hour","type":"boolean"},
                {"name":"peer_group_code","type":["null","string"],"default":null},
                {"name":"peer_avg_amount","type":["null","double"],"default":null},
                {"name":"peer_std_amount","type":["null","double"],"default":null},
                {"name":"amount_vs_peer_avg","type":["null","double"],"default":null},
                {"name":"peer_amount_z_score","type":["null","double"],"default":null},
                {"name":"fraud_label","type":["null","boolean"],"default":null},
                {"name":"label_source","type":["null","string"],"default":null}
              ]
            }
            """);

    public void write(Path path, List<ExportFeatureRow> rows) throws IOException {
        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(new LocalOutputFile(path))
                .withSchema(SCHEMA)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .withDictionaryEncoding(true)
                .build()) {
            for (ExportFeatureRow row : rows) {
                writer.write(toRecord(row));
            }
        }
    }

    public List<String> columns() {
        return SCHEMA.getFields().stream().map(Schema.Field::name).toList();
    }

    private GenericRecord toRecord(ExportFeatureRow row) {
        GenericRecord record = new GenericData.Record(SCHEMA);
        record.put("transaction_id", row.transactionId());
        record.put("customer_id", row.customerId());
        record.put("account_id", row.accountId());
        record.put("business_date", Math.toIntExact(row.businessDate().toEpochDay()));
        record.put("transaction_date", row.transactionDate().toInstant(ZoneOffset.UTC).toEpochMilli() * 1000);
        record.put("feature_version", row.featureVersion());
        record.put("model_feature_schema", row.modelFeatureSchema());
        record.put("model_features_json", row.modelFeaturesJson());
        record.put("current_amount", row.currentAmount());
        record.put("current_balance", row.currentBalance());
        record.put("amount_balance_ratio", row.amountBalanceRatio());
        record.put("transaction_hour", row.transactionHour());
        record.put("transaction_day_of_week", row.transactionDayOfWeek());
        record.put("is_night", row.night());
        record.put("is_weekend", row.weekend());
        record.put("customer_history_count", row.customerHistoryCount());
        record.put("trusted_history_count", row.trustedHistoryCount());
        record.put("recent_transaction_count", row.recentTransactionCount());
        record.put("profile_confidence", row.profileConfidence());
        record.put("last_30_avg_amount", row.last30Average());
        record.put("last_30_median_amount", row.last30Median());
        record.put("last_30_std_amount", row.last30StandardDeviation());
        record.put("amount_vs_last_30_avg", row.amountVsLast30Average());
        record.put("amount_vs_last_30_median", row.amountVsLast30Median());
        record.put("amount_z_score_last_30", row.amountZScoreLast30());
        record.put("transaction_count_1h", row.transactionCount1Hour());
        record.put("transaction_count_24h", row.transactionCount24Hours());
        record.put("transaction_count_7d", row.transactionCount7Days());
        record.put("transaction_count_30d", row.transactionCount30Days());
        record.put("amount_sum_24h", row.amountSum24Hours());
        record.put("amount_sum_7d", row.amountSum7Days());
        record.put("amount_sum_30d", row.amountSum30Days());
        record.put("new_beneficiary", row.newBeneficiary());
        record.put("new_location", row.newLocation());
        record.put("new_channel", row.newChannel());
        record.put("new_device", row.newDevice());
        record.put("unusual_transaction_hour", row.unusualTransactionHour());
        record.put("peer_group_code", row.peerGroupCode());
        record.put("peer_avg_amount", row.peerAverageAmount());
        record.put("peer_std_amount", row.peerStandardDeviationAmount());
        record.put("amount_vs_peer_avg", row.amountVsPeerAverage());
        record.put("peer_amount_z_score", row.peerAmountZScore());
        record.put("fraud_label", row.fraudLabel());
        record.put("label_source", row.labelSource());
        return record;
    }
}
