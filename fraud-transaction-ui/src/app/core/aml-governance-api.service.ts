import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import {
  ActiveModelPointer,
  AmlModelRegistryEntry,
  ModelDeploymentEvent,
  ModelValidationReport,
  LayeredDeploymentEvent,
  LayeredDeploymentPointer,
  LayeredShadowValidationReport,
} from './models';

@Injectable({ providedIn: 'root' })
export class AmlGovernanceApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080/api/v1/aml/models';
  private readonly layeredValidationUrl = 'http://localhost:8080/api/v1/aml/layered-shadow';
  private readonly layeredDeploymentUrl = 'http://localhost:8080/api/v1/aml/layered-deployments';

  listModels(modelType?: string) {
    const options = modelType
      ? { params: new HttpParams().set('modelType', modelType) }
      : {};
    return this.http.get<AmlModelRegistryEntry[]>(this.baseUrl, options);
  }

  listActiveModels() {
    return this.http.get<ActiveModelPointer[]>(`${this.baseUrl}/active`);
  }

  listValidations(modelVersion: string) {
    return this.http.get<ModelValidationReport[]>(
      `${this.baseUrl}/${encodeURIComponent(modelVersion)}/validations`
    );
  }

  validate(modelVersion: string, validatedBy: string) {
    return this.http.post<ModelValidationReport>(
      `${this.baseUrl}/${encodeURIComponent(modelVersion)}/validate`,
      { validatedBy }
    );
  }

  promote(modelVersion: string, actionId: string, reason: string) {
    return this.http.post<ModelDeploymentEvent>(
      `${this.baseUrl}/${encodeURIComponent(modelVersion)}/promote`,
      { actionId, reason }
    );
  }

  rollback(modelVersion: string, actionId: string, reason: string) {
    return this.http.post<ModelDeploymentEvent>(
      `${this.baseUrl}/${encodeURIComponent(modelVersion)}/rollback`,
      { actionId, reason }
    );
  }

  listDeployments(modelType: string, modelSegment?: string | null) {
    let params = new HttpParams().set('modelType', modelType);
    if (modelSegment) {
      params = params.set('modelSegment', modelSegment);
    }
    return this.http.get<ModelDeploymentEvent[]>(`${this.baseUrl}/deployments`, { params });
  }

  listLayeredValidations() {
    return this.http.get<LayeredShadowValidationReport[]>(`${this.layeredValidationUrl}/validations`);
  }

  validateLayered(peerGroupCode: string, validatedBy: string) {
    return this.http.post<LayeredShadowValidationReport>(`${this.layeredValidationUrl}/validate`, {
      peerGroupCode,
      validatedBy,
    });
  }

  listLayeredPointers() {
    return this.http.get<LayeredDeploymentPointer[]>(`${this.layeredDeploymentUrl}/active`);
  }

  listLayeredDeployments(peerGroupCode?: string) {
    const params = peerGroupCode ? new HttpParams().set('peerGroupCode', peerGroupCode) : undefined;
    return this.http.get<LayeredDeploymentEvent[]>(`${this.layeredDeploymentUrl}/history`, { params });
  }

  promoteLayered(validationId: string, peerGroupCode: string, canaryPercentage: number, reason: string) {
    return this.http.post<LayeredDeploymentEvent>(`${this.layeredDeploymentUrl}/promote`, {
      actionId: crypto.randomUUID(),
      validationId,
      peerGroupCode,
      canaryPercentage,
      reason,
    });
  }

  rollbackLayered(peerGroupCode: string, reason: string) {
    return this.http.post<LayeredDeploymentEvent>(`${this.layeredDeploymentUrl}/rollback`, {
      actionId: crypto.randomUUID(),
      peerGroupCode,
      reason,
    });
  }
}
