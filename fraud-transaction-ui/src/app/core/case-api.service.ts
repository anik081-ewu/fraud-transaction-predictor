import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { CaseRecord, PageResponse, TransactionRecord } from './models';

@Injectable({ providedIn: 'root' })
export class CaseApiService {
  private readonly http = inject(HttpClient);
  private readonly backendBaseUrl = 'http://localhost:8080';

  listCases() {
    return this.http.get<CaseRecord[]>(`${this.backendBaseUrl}/api/cases`);
  }

  getCase(caseId: number) {
    return this.http.get<CaseRecord>(`${this.backendBaseUrl}/api/cases/${caseId}`);
  }

  createCase(payload: Record<string, unknown>) {
    return this.http.post<CaseRecord>(`${this.backendBaseUrl}/api/cases`, payload);
  }

  updateStatus(caseId: number, payload: Record<string, unknown>) {
    return this.http.put<CaseRecord>(`${this.backendBaseUrl}/api/cases/${caseId}/status`, payload);
  }

  addNote(caseId: number, payload: Record<string, unknown>) {
    return this.http.post<CaseRecord>(`${this.backendBaseUrl}/api/cases/${caseId}/notes`, payload);
  }

  markFalsePositive(caseId: number, performedBy: string) {
    return this.http.post<CaseRecord>(
      `${this.backendBaseUrl}/api/cases/${caseId}/false-positive`,
      { performedBy }
    );
  }

  generateStrXml(caseId: number, performedBy: string) {
    return this.http.post(
      `${this.backendBaseUrl}/api/cases/${caseId}/str-xml`,
      { performedBy },
      { observe: 'response', responseType: 'blob' }
    );
  }

  searchTransactions(query: string, page = 0, size = 20) {
    return this.http.get<PageResponse<TransactionRecord>>(
      `${this.backendBaseUrl}/api/v1/transactions`,
      { params: { query, page, size } }
    );
  }
}
