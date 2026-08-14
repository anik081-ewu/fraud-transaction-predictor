import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import {
  BulkUploadResponse,
  BulkUploadStartResponse,
  BatchSummaryResponse,
  AnomalyConfig,
  ColdStartConfigItem,
  DatasetPartition,
  ModelVersion,
  ModelTuningItem,
  LearningModelCatalog,
  RiskPolicyConfig,
  RiskPolicyConfigUpdateRequest,
  UploadedDataset,
} from './models';

@Injectable({ providedIn: 'root' })
export class ComparisonApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080/api/v1';

  uploadDataset(file: File, uploadedBy: string) {
    const formData = new FormData();
    formData.append('file', file);
    if (uploadedBy.trim()) {
      formData.append('uploadedBy', uploadedBy.trim());
    }
    return this.http.post<BulkUploadStartResponse>(`${this.baseUrl}/uploads/transactions`, formData);
  }

  getBatchStatus(batchNo: string) {
    return this.http.get<BatchSummaryResponse>(`${this.baseUrl}/uploads/batches/${batchNo}`);
  }

  getLatestBatch() {
    return this.http.get<BatchSummaryResponse>(`${this.baseUrl}/uploads/batches/latest`);
  }

  listDatasets() {
    return this.http.get<UploadedDataset[]>(`${this.baseUrl}/anomaly-model-comparisons/datasets`);
  }

  getDataset(datasetId: number) {
    return this.http.get<UploadedDataset>(`${this.baseUrl}/anomaly-model-comparisons/datasets/${datasetId}`);
  }

  createDatabaseSnapshot(requestedBy: string) {
    return this.http.post<UploadedDataset>(
      `${this.baseUrl}/anomaly-model-comparisons/datasets/database-snapshot`,
      { requestedBy }
    );
  }

  listPartitions(datasetId: number) {
    return this.http.get<DatasetPartition[]>(`${this.baseUrl}/anomaly-model-comparisons/datasets/${datasetId}/partitions`);
  }

  trainPartition(partitionId: number, requestedBy: string, modelNames: string[]) {
    return this.http.post<ModelVersion[]>(
      `${this.baseUrl}/anomaly-model-comparisons/partitions/${partitionId}/train`,
      { requestedBy, modelNames }
    );
  }

  listModelVersions(partitionId: number) {
    return this.http.get<ModelVersion[]>(
      `${this.baseUrl}/anomaly-model-comparisons/partitions/${partitionId}/model-versions`
    );
  }

  listConfigs() {
    return this.http.get<AnomalyConfig[]>(`${this.baseUrl}/anomaly-model-comparisons/configs`);
  }

  getActiveConfig() {
    return this.http.get<AnomalyConfig | null>(`${this.baseUrl}/anomaly-model-comparisons/configs/active`);
  }

  saveConfig(payload: Record<string, unknown>) {
    return this.http.post<AnomalyConfig>(`${this.baseUrl}/anomaly-model-comparisons/configs`, payload);
  }

  listColdStartConfigs() {
    return this.http.get<ColdStartConfigItem[]>(`${this.baseUrl}/anomaly-model-comparisons/settings`);
  }

  updateColdStartConfigs(values: Record<string, string>) {
    return this.http.put<ColdStartConfigItem[]>(
      `${this.baseUrl}/anomaly-model-comparisons/settings`,
      { values }
    );
  }

  listModelTuning() {
    return this.http.get<ModelTuningItem[]>(
      `${this.baseUrl}/anomaly-model-comparisons/model-tuning`
    );
  }

  getModelCatalog() {
    return this.http.get<LearningModelCatalog[]>(
      `${this.baseUrl}/anomaly-model-comparisons/model-catalog`
    );
  }

  updateModelTuning(values: Record<string, string>) {
    return this.http.put<ModelTuningItem[]>(
      `${this.baseUrl}/anomaly-model-comparisons/model-tuning`,
      { values }
    );
  }

  getRiskPolicy() {
    return this.http.get<RiskPolicyConfig>(
      `${this.baseUrl}/anomaly-model-comparisons/risk-policy`
    );
  }

  updateRiskPolicy(payload: RiskPolicyConfigUpdateRequest) {
    return this.http.put<RiskPolicyConfig>(
      `${this.baseUrl}/anomaly-model-comparisons/risk-policy`,
      payload
    );
  }

}
