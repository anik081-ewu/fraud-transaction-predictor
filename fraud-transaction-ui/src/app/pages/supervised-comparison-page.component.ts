import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { AlertService } from '../core/alert.service';
import { AmlTrainingApiService } from '../core/aml-training-api.service';
import { AmlTrainingRun, SupervisedGrowthMetric, SupervisedGrowthReport, SupervisedGrowthStudy } from '../core/models';

const SUPERVISED_MODEL_TYPES = new Set([
  'SUPERVISED_ENSEMBLE',
  'XGBOOST_CLASSIFIER',
  'RANDOM_FOREST_CLASSIFIER',
  'LOGISTIC_REGRESSION',
]);

@Component({
  selector: 'app-supervised-comparison-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './supervised-comparison-page.component.html',
  styleUrls: ['./page.css', './supervised-comparison-page.component.css'],
})
export class SupervisedComparisonPageComponent implements OnInit, OnDestroy {
  private readonly api = inject(AmlTrainingApiService);
  private readonly alerts = inject(AlertService);
  readonly runs = signal<AmlTrainingRun[]>([]);
  readonly report = signal<SupervisedGrowthReport | null>(null);
  readonly study = signal<SupervisedGrowthStudy | null>(null);
  readonly loading = signal(false);
  selectedRunId = '';
  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private pollInFlight = false;

  readonly rankedModels = computed(() => {
    const rows = this.report()?.results ?? [];
    const models = [...new Set(rows.map((row) => row.detector))];
    return models.map((model) => {
      const modelRows = rows.filter((row) => row.detector === model);
      const average = (key: keyof SupervisedGrowthMetric) =>
        modelRows.reduce((sum, row) => sum + Number(row[key]), 0) / modelRows.length;
      return {
        model,
        prAuc: average('prAuc'),
        prAucLift: average('prAucLift'),
        accuracy: average('accuracy'),
        balancedAccuracy: average('balancedAccuracy'),
        precision: average('precision'),
        recall: average('recall'),
        f1: average('f1'),
        brier: average('brierScore'),
      };
    }).sort((a, b) => b.prAuc - a.prAuc || b.f1 - a.f1);
  });

  ngOnInit(): void {
    this.api.listRuns().subscribe({
      next: (runs) => {
        const candidates = runs.filter((run) =>
          !!run.datasetPath && !!run.datasetChecksum && SUPERVISED_MODEL_TYPES.has(run.modelType));
        const ready = [...new Map(candidates.map((run) => [run.datasetChecksum, run])).values()];
        this.runs.set(ready);
        this.selectedRunId = ready[0]?.trainingRunId ?? '';
        this.loadCachedStudy();
      },
      error: (error) => this.alerts.error(this.message(error), 'Training snapshots unavailable'),
    });
  }

  ngOnDestroy(): void { this.stopPolling(); }

  run(): void {
    if (!this.selectedRunId) {
      this.alerts.error('Generate a labelled training snapshot first.', 'No snapshot selected');
      return;
    }
    this.loading.set(true);
    this.api.runSupervisedGrowthAnalysis(this.selectedRunId)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (study) => {
          this.applyStudy(study);
          if (study.status === 'COMPLETED') {
            this.alerts.success('Cached comparison loaded', 'This snapshot was already evaluated.');
          } else {
            this.alerts.success('Comparison queued', 'You can leave this page; the result remains available.');
            this.startPolling(study.studyId);
          }
        },
        error: (error) => this.alerts.error(this.message(error), 'Supervised comparison failed'),
      });
  }

  partitions(): number[] { return this.report()?.partitionPercentages ?? []; }
  metric(model: string, percentage: number): SupervisedGrowthMetric | undefined {
    return this.report()?.results.find((row) => row.detector === model && row.partitionPercentage === percentage);
  }
  fullMetric(model: string): SupervisedGrowthMetric | undefined { return this.metric(model, 100); }
  percent(value?: number): string { return value == null ? '—' : `${(value * 100).toFixed(1)}%`; }
  matrixTotal(metric: SupervisedGrowthMetric): number {
    return metric.trueNegative + metric.falsePositive + metric.falseNegative + metric.truePositive;
  }
  matrixRate(value: number, metric: SupervisedGrowthMetric): string {
    return this.percent(value / Math.max(1, this.matrixTotal(metric)));
  }

  private loadCachedStudy(): void {
    this.api.latestSupervisedGrowthStudy().subscribe({
      next: (study) => {
        if (!study) return;
        this.applyStudy(study);
        if (this.runs().some((run) => run.trainingRunId === study.trainingRunId)) {
          this.selectedRunId = study.trainingRunId;
        }
        if (study.status === 'QUEUED' || study.status === 'RUNNING') this.startPolling(study.studyId);
      },
      error: () => undefined,
    });
  }

  private startPolling(studyId: string): void {
    this.stopPolling();
    this.pollHandle = setInterval(() => {
      if (this.pollInFlight) return;
      this.pollInFlight = true;
      this.api.getSupervisedGrowthStudy(studyId)
        .pipe(finalize(() => { this.pollInFlight = false; }))
        .subscribe({
          next: (study) => {
            this.applyStudy(study);
            if (study.status === 'COMPLETED') {
              this.stopPolling();
              this.alerts.success('Comparison complete', `${this.report()?.datasetRows.toLocaleString()} labelled rows evaluated and cached.`);
            } else if (study.status === 'FAILED') {
              this.stopPolling();
              this.alerts.error(study.failureReason || 'Supervised comparison failed.', 'Comparison failed');
            }
          },
          error: () => this.stopPolling(),
        });
    }, 5000);
  }

  private stopPolling(): void {
    if (this.pollHandle) clearInterval(this.pollHandle);
    this.pollHandle = null;
  }

  private applyStudy(study: SupervisedGrowthStudy): void {
    this.study.set(study);
    if (!study.resultJson) return;
    try { this.report.set(JSON.parse(study.resultJson) as SupervisedGrowthReport); }
    catch { this.report.set(null); }
  }

  private message(error: unknown): string {
    const payload = (error as { error?: unknown })?.error;
    if (typeof payload === 'string') {
      try { return this.payloadMessage(JSON.parse(payload)) || payload; }
      catch { return payload; }
    }
    return this.payloadMessage(payload) || 'The comparison could not be completed.';
  }

  private payloadMessage(payload: unknown): string | null {
    if (!payload || typeof payload !== 'object') return null;
    const value = payload as { message?: unknown; detail?: unknown };
    for (const candidate of [value.detail, value.message]) {
      if (typeof candidate !== 'string' || !candidate.trim()) continue;
      try {
        const nested = this.payloadMessage(JSON.parse(candidate));
        if (nested) return nested;
      } catch { return candidate; }
      return candidate;
    }
    return null;
  }
}
