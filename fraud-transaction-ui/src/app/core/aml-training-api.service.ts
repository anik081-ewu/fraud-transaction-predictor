import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import {
  AgreementStudy,
  AmlModelRegistryEntry,
  AmlTrainingRun,
  GrowthStudy,
  LayerAblationReport,
  SystemHealth,
  SupervisedGrowthReport,
  SupervisedGrowthStudy,
} from './models';

@Injectable({ providedIn: 'root' })
export class AmlTrainingApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080';

  listRuns() {
    return this.http.get<AmlTrainingRun[]>(`${this.baseUrl}/api/v1/aml/training-runs`);
  }

  createRun(payload: Record<string, unknown>) {
    return this.http.post<AmlTrainingRun>(`${this.baseUrl}/api/v1/aml/training-runs`, payload);
  }

  closeBusinessDay(businessDate: string, closedBy: string) {
    return this.http.post(`${this.baseUrl}/api/v1/aml/business-days/${businessDate}/close`, { closedBy });
  }

  closeBusinessDateRange(fromDate: string, toDate: string, closedBy: string) {
    return this.http.post<{ fromDate: string; toDate: string; status: string; closedDateCount: number }>(
      `${this.baseUrl}/api/v1/aml/business-days/range/close`,
      { fromDate, toDate, closedBy }
    );
  }

  generateDataset(trainingRunId: string) {
    return this.http.post<AmlTrainingRun>(`${this.baseUrl}/api/v1/aml/training-runs/${trainingRunId}/dataset`, {});
  }

  runSupervisedGrowthAnalysis(trainingRunId: string) {
    return this.http.post<SupervisedGrowthStudy>(
      `${this.baseUrl}/api/v1/aml/supervised-growth-studies/training-runs/${trainingRunId}?requestedBy=supervised-comparison-ui`,
      { percentages: [10, 25, 50, 100], minimumRows: 200, maximumEvaluationRows: 200000 }
    );
  }

  latestSupervisedGrowthStudy() {
    return this.http.get<SupervisedGrowthStudy | null>(`${this.baseUrl}/api/v1/aml/supervised-growth-studies/latest`);
  }

  getSupervisedGrowthStudy(studyId: string) {
    return this.http.get<SupervisedGrowthStudy>(`${this.baseUrl}/api/v1/aml/supervised-growth-studies/${studyId}`);
  }

  startPipeline(payload: {
    featureVersion: string;
    modelSegment: string | null;
    fromBusinessDate: string;
    toBusinessDate: string;
    cutoffTimestamp: string;
    requestedBy: string;
    learningMode: 'UNSUPERVISED' | 'SUPERVISED';
    selectedModels: string[];
  }) {
    return this.http.post<AmlTrainingRun>(`${this.baseUrl}/api/v1/aml/training-runs/pipeline/start`, payload);
  }

  trainProductionCandidates(trainingRunId: string, requestedBy: string, selectedModels: string[]) {
    return this.http.post<{
      trainedModels: string[];
      trainingStatus?: string | null;
      trainingMessage?: string | null;
    }>(
      `${this.baseUrl}/api/v1/aml/training-runs/${trainingRunId}/production-candidates`,
      { requestedBy, selectedModels }
    );
  }

  listModels(status?: string) {
    const params = status ? `?status=${status}` : '';
    return this.http.get<AmlModelRegistryEntry[]>(`${this.baseUrl}/api/v1/aml/models${params}`);
  }

  /** Newest completed growth study; 204 (null body) when none has finished yet. */
  latestGrowthStudy() {
    return this.http.get<GrowthStudy | null>(`${this.baseUrl}/api/v1/aml/growth-studies/latest`);
  }

  listGrowthStudies() {
    return this.http.get<GrowthStudy[]>(`${this.baseUrl}/api/v1/aml/growth-studies`);
  }

  getGrowthStudy(studyId: string) {
    return this.http.get<GrowthStudy>(`${this.baseUrl}/api/v1/aml/growth-studies/${studyId}`);
  }

  startGrowthStudy(trainingRunId: string, requestedBy: string) {
    return this.http.post<GrowthStudy>(
      `${this.baseUrl}/api/v1/aml/growth-studies/training-runs/${trainingRunId}?requestedBy=${encodeURIComponent(requestedBy)}`,
      {}
    );
  }

  /** Newest agreement study — running takes precedence over last completed; null when none. */
  latestAgreementStudy() {
    return this.http.get<AgreementStudy | null>(`${this.baseUrl}/api/v1/aml/agreement-studies/latest`);
  }

  getAgreementStudy(studyId: string) {
    return this.http.get<AgreementStudy>(`${this.baseUrl}/api/v1/aml/agreement-studies/${studyId}`);
  }

  startAgreementStudy(trainingRunId: string, requestedBy: string) {
    return this.http.post<AgreementStudy>(
      `${this.baseUrl}/api/v1/aml/agreement-studies/training-runs/${trainingRunId}?requestedBy=${encodeURIComponent(requestedBy)}`,
      {}
    );
  }

  runLayerAblation() {
    return this.http.post<LayerAblationReport>(
      `${this.baseUrl}/api/v1/aml/growth-analysis/layer-ablation`,
      {}
    );
  }

  health() {
    return this.http.get<SystemHealth>(`${this.baseUrl}/health`);
  }
}
