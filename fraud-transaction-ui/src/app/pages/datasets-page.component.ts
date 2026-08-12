import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { AlertService } from '../core/alert.service';
import { AmlTrainingApiService } from '../core/aml-training-api.service';
import {
  AgreementResult,
  AgreementStudy,
  AmlModelRegistryEntry,
  AmlTrainingRun,
  DetectorGrowthMetric,
  GrowthStudy,
} from '../core/models';

const MODEL_TYPES = [
  'ISOLATION_FOREST',
  'ONE_CLASS_SVM',
  'AUTOENCODER',
  'HALF_SPACE_TREES',
  'ONLINE_ONE_CLASS_SVM',
] as const;

const STATUS_RANK: Record<string, number> = {
  CHAMPION: 0, CHALLENGER: 1, VALIDATED: 2, APPROVED: 3, CANDIDATE: 4, REJECTED: 99, RETIRED: 99,
};

@Component({
  selector: 'app-datasets-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './datasets-page.component.html',
  styleUrls: ['./page.css', './datasets-page.component.css'],
})
export class DatasetsPageComponent implements OnInit {
  private readonly api = inject(AmlTrainingApiService);
  private readonly alerts = inject(AlertService);
  // Injected as a field: inject() is only valid in an injection context, not in ngOnInit
  private readonly destroyRef = inject(DestroyRef);

  readonly models = signal<AmlModelRegistryEntry[]>([]);
  readonly loading = signal(false);

  readonly modelTypes = MODEL_TYPES;

  // ---- Analysis tab (agreement + data growth over one snapshot) ----------------------
  readonly tab = signal<'models' | 'analysis'>('models');
  readonly study = signal<GrowthStudy | null>(null);
  readonly loadingStudy = signal(false);
  readonly startingStudy = signal(false);
  readonly runs = signal<AmlTrainingRun[]>([]);
  selectedRunId = '';

  private pollHandle: ReturnType<typeof setInterval> | null = null;
  /** Guards against overlapping background polls piling up on the connection pool. */
  private pollInFlight = false;

  readonly agreementStudy = signal<AgreementStudy | null>(null);
  readonly loadingAgreement = signal(false);
  readonly startingAgreement = signal(false);
  private agreementPollHandle: ReturnType<typeof setInterval> | null = null;
  private agreementPollInFlight = false;

  /** Detectors keyed as the growth analysis reports them. */
  readonly growthDetectors = MODEL_TYPES;

  readonly partitions = computed(() => {
    const current = this.study();
    if (current?.partitionPercentages?.length) return current.partitionPercentages;
    const fromMetrics = new Set((current?.metrics ?? []).map((m) => m.partitionPercentage));
    return [...fromMetrics].sort((a, b) => a - b);
  });

  readonly studyRunning = computed(() => {
    const status = this.study()?.status;
    return status === 'QUEUED' || status === 'RUNNING';
  });

  /**
   * Distinct exported datasets available to study.
   *
   * A pipeline creates one run per model and every sibling copies the snapshot's
   * datasetPath and datasetChecksum, so five runs share one dataset. Offering all five
   * would present five identical choices; they are deduplicated by checksum, keeping the
   * earliest run — the one that actually performed the export.
   */
  readonly studyableRuns = computed(() => {
    const byDataset = new Map<string, AmlTrainingRun>();
    for (const run of this.runs()) {
      if (!run.datasetPath || !run.datasetChecksum) continue;
      const existing = byDataset.get(run.datasetChecksum);
      if (!existing || run.createdAt < existing.createdAt) {
        byDataset.set(run.datasetChecksum, run);
      }
    }
    return [...byDataset.values()].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  });

  /** Human label for a snapshot: what it covers and how big it is, not an opaque id. */
  snapshotLabel(run: AmlTrainingRun): string {
    const rows = run.exportedRowCount ?? run.requestedRowCount;
    const rowText = rows ? `${rows.toLocaleString()} rows` : 'exported';
    return `${run.fromBusinessDate} → ${run.toBusinessDate} · ${rowText}`;
  }

  // Best model per type: CHAMPION first, then most recent CANDIDATE
  readonly bestByType = computed(() => {
    const map: Partial<Record<string, AmlModelRegistryEntry>> = {};
    for (const entry of this.models()) {
      const current = map[entry.modelType];
      if (!current) { map[entry.modelType] = entry; continue; }
      const currentRank = STATUS_RANK[current.status] ?? 99;
      const entryRank = STATUS_RANK[entry.status] ?? 99;
      if (entryRank < currentRank || (entryRank === currentRank && entry.createdAt > current.createdAt)) {
        map[entry.modelType] = entry;
      }
    }
    return map;
  });

  readonly hasAnyModel = computed(() => this.models().length > 0);

  ngOnInit(): void {
    this.load();
    this.loadStudy();
    this.loadAgreement();
    this.api.listRuns().subscribe({ next: (runs) => this.runs.set(runs), error: () => {} });
    this.destroyRef.onDestroy(() => {
      this.stopPolling();
      this.stopAgreementPolling();
    });
  }

  selectTab(tab: 'models' | 'analysis'): void {
    this.tab.set(tab);
  }

  /**
   * Starts both analyses over one snapshot from a single click.
   *
   * They share a snapshot but not their computation — agreement scores the existing
   * champions, growth retrains at each partition — so they stay separate jobs. Agreement
   * finishes in under a minute and growth takes several, so results arrive progressively
   * rather than making the user wait for the slowest one before seeing anything.
   */
  runAnalysis(): void {
    const runId = this.selectedRunId || this.studyableRuns()[0]?.trainingRunId;
    if (!runId) {
      this.alerts.error('No training run has an exported dataset yet.', 'Cannot start analysis');
      return;
    }
    this.startAgreementStudy(runId);
    this.startStudy(runId);
    this.alerts.success(
      'Analysis started',
      'Model agreement lands in under a minute; the growth study takes several.'
    );
  }

  /** True while either analysis is still in flight. */
  readonly analysisRunning = computed(() => this.studyRunning() || this.agreementRunning());

  // ---- Model agreement ---------------------------------------------------------------

  loadAgreement(): void {
    this.loadingAgreement.set(true);
    this.api.latestAgreementStudy()
      .pipe(finalize(() => this.loadingAgreement.set(false)))
      .subscribe({
        next: (study) => {
          this.agreementStudy.set(study);
          if (study && (study.status === 'QUEUED' || study.status === 'RUNNING')) {
            this.startAgreementPolling(study.studyId);
          }
        },
        error: () => this.agreementStudy.set(null),
      });
  }

  startAgreementStudy(runId: string): void {
    this.startingAgreement.set(true);
    this.api.startAgreementStudy(runId, 'model-comparison-ui')
      .pipe(finalize(() => this.startingAgreement.set(false)))
      .subscribe({
        next: (study) => {
          this.agreementStudy.set(study);
          this.startAgreementPolling(study.studyId);
        },
        error: (error) => this.alerts.error(this.message(error), 'Agreement analysis not started'),
      });
  }

  private startAgreementPolling(studyId: string): void {
    this.stopAgreementPolling();
    this.agreementPollHandle = setInterval(() => {
      if (this.agreementPollInFlight) return;
      this.agreementPollInFlight = true;
      this.api.getAgreementStudy(studyId)
        .pipe(finalize(() => { this.agreementPollInFlight = false; }))
        .subscribe({
          next: (study) => {
            this.agreementStudy.set(study);
            if (study.status === 'COMPLETED' || study.status === 'FAILED') {
              this.stopAgreementPolling();
              if (study.status === 'FAILED') {
                this.alerts.error(study.failureReason || 'Analysis failed', 'Agreement analysis failed');
              }
            }
          },
          error: () => this.stopAgreementPolling(),
        });
    }, 5000);
  }

  private stopAgreementPolling(): void {
    if (this.agreementPollHandle) clearInterval(this.agreementPollHandle);
    this.agreementPollHandle = null;
  }

  /** Jaccard for a pair regardless of the order the backend listed them in. */
  jaccardFor(a: string, b: string): number | null {
    if (a === b) return null;
    const pair = (this.agreement()?.pairs ?? []).find(
      (p) => (p.modelA === a && p.modelB === b) || (p.modelA === b && p.modelB === a)
    );
    return pair ? pair.jaccard : null;
  }

  bothCountFor(a: string, b: string): number | null {
    if (a === b) return null;
    const pair = (this.agreement()?.pairs ?? []).find(
      (p) => (p.modelA === a && p.modelB === b) || (p.modelA === b && p.modelB === a)
    );
    return pair ? pair.bothCount : null;
  }

  /** Heat shading: stronger agreement reads darker, so clusters stand out at a glance. */
  jaccardShade(a: string, b: string): string {
    const value = this.jaccardFor(a, b);
    if (value === null) return 'transparent';
    // Cap at 0.5 — real overlaps sit well below 1, so scaling to 1 washes everything out
    const intensity = Math.min(1, value / 0.5);
    return `rgba(37, 99, 235, ${(0.06 + intensity * 0.5).toFixed(3)})`;
  }

  flaggedFor(modelType: string): { flaggedCount: number; flaggedRate: number } | undefined {
    return this.agreement()?.models.find((m) => m.modelType === modelType);
  }

  /** Only the models that actually produced flags; skipped ones would render empty rows. */
  readonly agreementModels = computed(() =>
    (this.agreement()?.models ?? []).map((m) => m.modelType)
  );

  readonly agreementRunning = computed(() => {
    const status = this.agreementStudy()?.status;
    return status === 'QUEUED' || status === 'RUNNING';
  });

  readonly agreement = computed<AgreementResult | null>(() => {
    const json = this.agreementStudy()?.resultJson;
    if (!json) return null;
    try { return JSON.parse(json) as AgreementResult; } catch { return null; }
  });

  readonly skippedModels = computed(() => {
    const skipped = this.agreement()?.skippedModels ?? {};
    return Object.entries(skipped).map(([modelType, reason]) => ({ modelType, reason }));
  });

  loadStudy(): void {
    this.loadingStudy.set(true);
    this.api.latestGrowthStudy()
      .pipe(finalize(() => this.loadingStudy.set(false)))
      .subscribe({
        next: (study) => {
          this.study.set(study);
          if (study && (study.status === 'QUEUED' || study.status === 'RUNNING')) {
            this.startPolling(study.studyId);
          }
        },
        error: () => this.study.set(null),
      });
  }

  startStudy(runId: string): void {
    this.startingStudy.set(true);
    this.api.startGrowthStudy(runId, 'model-comparison-ui')
      .pipe(finalize(() => this.startingStudy.set(false)))
      .subscribe({
        next: (study) => {
          this.study.set(study);
          this.startPolling(study.studyId);
        },
        error: (error) => this.alerts.error(this.message(error), 'Growth study not started'),
      });
  }

  /**
   * Polls only while a study is in flight; a completed study never needs refreshing.
   *
   * The in-flight guard is load-bearing: setInterval fires on a fixed schedule whether or
   * not the previous request came back, so while the database is busy these would stack up
   * without bound and starve the connection pool.
   */
  private startPolling(studyId: string): void {
    this.stopPolling();
    this.pollHandle = setInterval(() => {
      if (this.pollInFlight) return;
      this.pollInFlight = true;
      this.api.getGrowthStudy(studyId)
        .pipe(finalize(() => { this.pollInFlight = false; }))
        .subscribe({
          next: (study) => {
            this.study.set(study);
            if (study.status === 'COMPLETED' || study.status === 'FAILED') {
              this.stopPolling();
              if (study.status === 'FAILED') {
                this.alerts.error(study.failureReason || 'Growth study failed', 'Study failed');
              }
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

  metricFor(detector: string, partition: number): DetectorGrowthMetric | undefined {
    return (this.study()?.metrics ?? [])
      .find((m) => m.detector === detector && m.partitionPercentage === partition);
  }

  /** True for detectors that forget older data, so a falling curve is window behaviour. */
  isStreamingDetector(detector: string): boolean {
    return detector === 'HALF_SPACE_TREES' || detector === 'ONLINE_ONE_CLASS_SVM';
  }

  hasBoundedCells(detector: string): boolean {
    return this.partitions().some((p) => this.metricFor(detector, p)?.boundedTrainingSample);
  }

  /** EM-AUC across partitions as an SVG polyline, so the trend is visible at a glance. */
  sparklinePoints(detector: string): string {
    const partitions = this.partitions();
    if (partitions.length < 2) return '';
    const width = 120;
    const height = 28;
    return partitions
      .map((partition, index) => {
        const metric = this.metricFor(detector, partition);
        const value = metric?.excessMassAuc ?? 0;
        const x = (index / (partitions.length - 1)) * width;
        const y = height - Math.max(0, Math.min(1, value)) * height;
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
  }

  emAucAt(detector: string, partition: number): number | null {
    const value = this.metricFor(detector, partition)?.excessMassAuc;
    return typeof value === 'number' && Number.isFinite(value) ? value * 100 : null;
  }

  studyStatusClass(status: string | undefined): string {
    return `status-${(status ?? '').toLowerCase()}`;
  }

  load(): void {
    this.loading.set(true);
    this.api.listModels()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (models) => this.models.set(models),
        error: (error) => this.alerts.error(this.message(error), 'Could not load model registry'),
      });
  }

  modelFor(modelType: string): AmlModelRegistryEntry | undefined {
    return this.bestByType()[modelType];
  }

  label(modelType: string): string {
    const labels: Record<string, string> = {
      ISOLATION_FOREST: 'Isolation Forest',
      ONE_CLASS_SVM: 'One-Class SVM',
      AUTOENCODER: 'Autoencoder',
      HALF_SPACE_TREES: 'Half-Space Trees',
      ONLINE_ONE_CLASS_SVM: 'Online One-Class SVM',
    };
    return labels[modelType] ?? modelType.replaceAll('_', ' ');
  }

  family(modelType: string): string {
    return ['HALF_SPACE_TREES', 'ONLINE_ONE_CLASS_SVM'].includes(modelType) ? 'Incremental' : 'Batch';
  }

  private metrics(entry: AmlModelRegistryEntry): Record<string, unknown> | null {
    try {
      return entry.metricsJson ? JSON.parse(entry.metricsJson) : null;
    } catch { return null; }
  }

  private metricNumber(entry: AmlModelRegistryEntry | undefined, key: string): number | null {
    if (!entry) return null;
    const value = this.metrics(entry)?.[key];
    return typeof value === 'number' && Number.isFinite(value) ? value : null;
  }

  trainingDurationMs(entry: AmlModelRegistryEntry): number | null {
    return this.metricNumber(entry, 'trainingDurationMs');
  }

  /**
   * Excess-Mass AUC (Goix et al.) — label-free detector quality on a 0-100 scale.
   * Scale-invariant, so unlike the raw score rows this one is comparable across models.
   */
  excessMassAuc(modelType: string): number | null {
    const value = this.metricNumber(this.modelFor(modelType), 'excessMassAuc');
    return value === null ? null : value * 100;
  }

  scoreSkewness(modelType: string): number | null {
    const value = this.metricNumber(this.modelFor(modelType), 'scoreSkewness');
    return value === null ? null : value * 100;
  }

  /** Highest EM-AUC across model types, used to highlight the leader. */
  readonly bestEmAuc = computed(() => {
    const values = this.modelTypes
      .map((type) => this.excessMassAuc(type))
      .filter((value): value is number => value !== null);
    return values.length ? Math.max(...values) : null;
  });

  isEmAucLeader(modelType: string): boolean {
    const value = this.excessMassAuc(modelType);
    const best = this.bestEmAuc();
    return value !== null && best !== null && value === best;
  }

  private message(error: unknown): string {
    const e = error as { error?: { message?: string; detail?: string }; message?: string };
    return e.error?.detail || e.error?.message || e.message || 'Something went wrong.';
  }
}
