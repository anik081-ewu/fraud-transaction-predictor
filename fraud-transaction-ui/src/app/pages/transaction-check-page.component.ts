import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface TransactionCheckRequest {
  transactionId: string;
  accountId: string;
  transactionAmount: number;
  transactionType: string;
  transactionDate: string;
  location: string;
  channel: string;
  customerAge: number | null;
  customerOccupation: string;
  loginAttempts: number;
  accountBalance: number | null;
}

interface TransactionCheckResult {
  transactionId: string;
  accountId: string;
  riskLevel: string;
  suspicious: boolean;
  anomalyVotes: number;
  modelResults: Record<string, unknown>;
  featureSummary: Record<string, unknown>;
  reasons: string[];
  recommendedAction: string;
}

@Component({
  selector: 'app-transaction-check-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './transaction-check-page.component.html',
  styleUrls: ['./page.css', './transaction-check-page.component.css'],
})
export class TransactionCheckPageComponent {
  private readonly SIGNAL_LABELS: Record<string, string> = {
    profileConfidence: 'Profile confidence',
    trustedHistoryCount: 'Trusted history count',
    amountVsLast30Average: 'Amount vs recent average',
    amountZScoreLast30: 'Amount z-score',
    newLocation: 'New location',
    transactionCount24Hours: '24h transaction count',
    persistedFeatureCount: 'Persisted features',
    featureVersion: 'Feature version',
    modelFeatureSchema: 'Feature schema',
    scoringContract: 'Scoring contract',
    productionArchitecture: 'Production architecture',
    riskPolicyVersion: 'Risk policy version',
    finalRiskScore: 'Final risk score',
  };

  private readonly http = inject(HttpClient);

  readonly submitting = signal(false);
  readonly result = signal<TransactionCheckResult | null>(null);
  readonly errorMessage = signal('');

  readonly TRANSACTION_TYPES = ['TRANSFER', 'PAYMENT', 'WITHDRAWAL', 'DEPOSIT', 'PURCHASE'];
  readonly CHANNELS = ['ONLINE', 'ATM', 'BRANCH', 'MOBILE', 'POS'];

  form: TransactionCheckRequest = this.blank();

  submit(): void {
    this.submitting.set(true);
    this.result.set(null);
    this.errorMessage.set('');
    const payload = {
      ...this.form,
      transactionDate: this.form.transactionDate
        ? new Date(this.form.transactionDate).toISOString().slice(0, 19)
        : null,
    };
    this.http.post<TransactionCheckResult>('http://localhost:8080/api/v1/transactions', payload).subscribe({
      next: (res) => {
        this.result.set(res);
        this.submitting.set(false);
      },
      error: (err) => {
        const payload = err?.error;
        this.errorMessage.set(payload?.detail || payload?.message || 'Transaction check failed.');
        this.submitting.set(false);
      },
    });
  }

  reset(): void {
    this.form = this.blank();
    this.result.set(null);
    this.errorMessage.set('');
  }

  riskClass(level: string): string {
    return `risk-${level?.toLowerCase() ?? 'normal'}`;
  }

  decisionScore(result: TransactionCheckResult): number | null {
    const layered = this.layeredResult(result.modelResults);
    const score = layered?.['finalRiskScore'];
    return typeof score === 'number' ? score * 100 : null;
  }

  detectorCount(result: TransactionCheckResult): number {
    return this.detectorRows(result.modelResults).length;
  }

  detectorAnomalyCount(result: TransactionCheckResult): number {
    return this.detectorRows(result.modelResults).filter((row) => row.anomaly === true).length;
  }

  explanationPoints(result: TransactionCheckResult): string[] {
    const totalDetectors = this.detectorCount(result);
    const anomalyDetectors = this.detectorAnomalyCount(result);
    const score = this.decisionScore(result);
    const points = [
      `${anomalyDetectors} of ${totalDetectors || 0} detector${totalDetectors === 1 ? '' : 's'} flagged this transaction as unusual.`,
      `The final action is ${this.humanizeToken(result.recommendedAction)} because the production risk layer classified it as ${result.riskLevel}.`,
    ];
    if (score !== null) {
      points.unshift(`The weighted production score is ${score.toFixed(1)} out of 100.`);
    }
    if (!result.reasons?.length) {
      points.push('No additional rule or model reasons were returned for this prediction.');
    }
    return points;
  }

  reasonBadges(reasons: string[]): string[] {
    return (reasons ?? []).map((reason) => this.humanizeToken(reason));
  }

  keySignals(summary: Record<string, unknown>): { label: string; value: string }[] {
    const preferredOrder = [
      'profileConfidence',
      'trustedHistoryCount',
      'amountVsLast30Average',
      'amountZScoreLast30',
      'newLocation',
      'transactionCount24Hours',
      'persistedFeatureCount',
      'featureVersion',
    ];
    return preferredOrder
      .filter((key) => summary[key] !== undefined && summary[key] !== null)
      .map((key) => ({
        label: this.SIGNAL_LABELS[key] ?? this.humanizeToken(key),
        value: this.formatDisplayValue(summary[key]),
      }));
  }

  detectorRows(modelResults: Record<string, unknown>): Array<{
    name: string;
    anomaly: boolean | null;
    score: string;
    duration: string;
    details: string;
  }> {
    return Object.entries(modelResults)
      .filter(([key, value]) => key !== 'LayeredRiskArchitecture' && typeof value === 'object' && value !== null)
      .map(([key, value]) => {
        const model = value as Record<string, unknown>;
        return {
          name: this.humanizeModelName(key),
          anomaly: typeof model['anomaly'] === 'boolean' ? (model['anomaly'] as boolean) : null,
          score: this.primaryModelScore(model),
          duration: this.durationLabel(model['predictionDurationMs']),
          details: this.formatModelValue(value),
        };
      });
  }

  summaryEntries(summary: Record<string, unknown>): { label: string; value: string }[] {
    return Object.entries(summary).map(([key, value]) => ({
      label: this.SIGNAL_LABELS[key] ?? this.humanizeToken(key),
      value: this.formatDisplayValue(value),
    }));
  }

  layeredEntries(modelResults: Record<string, unknown>): { label: string; value: string }[] {
    const layered = this.layeredResult(modelResults);
    if (!layered) {
      return [];
    }
    return Object.entries(layered).map(([key, value]) => ({
      label: this.humanizeToken(key),
      value: this.formatDisplayValue(value),
    }));
  }

  modelEntries(modelResults: Record<string, unknown>): { key: string; value: unknown }[] {
    return Object.entries(modelResults).map(([key, value]) => ({ key, value }));
  }

  formatModelValue(value: unknown): string {
    if (value === null || value === undefined) return '—';
    if (typeof value === 'object') return JSON.stringify(value, null, 2);
    return String(value);
  }

  private layeredResult(modelResults: Record<string, unknown>): Record<string, unknown> | null {
    const layered = modelResults['LayeredRiskArchitecture'];
    return typeof layered === 'object' && layered !== null ? (layered as Record<string, unknown>) : null;
  }

  private primaryModelScore(model: Record<string, unknown>): string {
    const scoreFields = ['normalizedScore', 'score', 'decisionFunction', 'rawScore', 'scoreSamples'];
    for (const field of scoreFields) {
      const value = model[field];
      if (typeof value === 'number') {
        return this.formatNumber(value);
      }
    }
    return '—';
  }

  private durationLabel(value: unknown): string {
    if (typeof value !== 'number') {
      return '—';
    }
    return `${Math.round(value)} ms`;
  }

  private formatDisplayValue(value: unknown): string {
    if (value === null || value === undefined) return '—';
    if (typeof value === 'boolean') return value ? 'Yes' : 'No';
    if (typeof value === 'number') return this.formatNumber(value);
    if (typeof value === 'object') return JSON.stringify(value);
    return String(value);
  }

  private formatNumber(value: number): string {
    if (!Number.isFinite(value)) return '—';
    if (Math.abs(value) >= 1000) {
      return value.toLocaleString(undefined, { maximumFractionDigits: 2 });
    }
    return value.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 3 });
  }

  private humanizeModelName(value: string): string {
    return value
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .replace(/\bPCA\b/g, 'PCA')
      .replace(/\bLOF\b/g, 'LOF')
      .trim();
  }

  private humanizeToken(value: string): string {
    return value
      .replace(/[._]/g, ' ')
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .toLowerCase()
      .replace(/\baml\b/g, 'AML')
      .replace(/\bsvm\b/g, 'SVM')
      .replace(/\bpca\b/g, 'PCA')
      .replace(/\blof\b/g, 'LOF')
      .replace(/\b[a-z]/g, (char) => char.toUpperCase())
      .trim();
  }

  private blank(): TransactionCheckRequest {
    const now = new Date();
    now.setSeconds(0, 0);
    return {
      transactionId: `TXN-${Date.now()}`,
      accountId: '',
      transactionAmount: 0,
      transactionType: 'TRANSFER',
      transactionDate: now.toISOString().slice(0, 16),
      location: '',
      channel: 'ONLINE',
      customerAge: null,
      customerOccupation: '',
      loginAttempts: 0,
      accountBalance: null,
    };
  }
}
