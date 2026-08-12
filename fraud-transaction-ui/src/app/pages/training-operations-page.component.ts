import { CommonModule } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';

import { AlertService } from '../core/alert.service';
import { AmlTrainingApiService } from '../core/aml-training-api.service';
import { AuthService } from '../core/auth.service';
import { ComparisonApiService } from '../core/comparison-api.service';
import { AmlTrainingRun, SystemHealth } from '../core/models';

const ACTIVE_STATUSES = ['QUEUED', 'EXPORTING', 'TRAINING'];
const POLL_INTERVAL_MS = 2000;
const RUNS_PER_PAGE = 8;

@Component({
  selector: 'app-training-operations-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './training-operations-page.component.html',
  styleUrls: ['./page.css', './training-operations-page.component.css'],
})
export class TrainingOperationsPageComponent {
  private readonly api = inject(AmlTrainingApiService);
  private readonly comparisonApi = inject(ComparisonApiService);
  private readonly auth = inject(AuthService);
  private readonly alerts = inject(AlertService);

  readonly runs = signal<AmlTrainingRun[]>([]);
  readonly health = signal<SystemHealth | null>(null);
  readonly loading = signal(false);
  readonly actionRunId = signal<string | null>(null);
  readonly loadingDates = signal(false);
  readonly selectedRunId = signal<string | null>(null);
  readonly selectedRun = computed(() => this.runs().find((run) => run.trainingRunId === this.selectedRunId()) ?? null);
  readonly activeCount = computed(() => this.runs().filter((run) => ['QUEUED', 'EXPORTING', 'TRAINING'].includes(run.status)).length);
  readonly candidateCount = computed(() => this.runs().filter((run) => run.status === 'CANDIDATE_READY').length);
  readonly failedCount = computed(() => this.runs().filter((run) => run.status.includes('FAILED')).length);

  // A pipeline creates one run per model, so the list grows five rows at a time and needs
  // paging rather than an ever-lengthening table.
  readonly pageSize = RUNS_PER_PAGE;
  private readonly requestedPage = signal(1);
  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.runs().length / this.pageSize)));
  /** Clamped so deleting or reloading runs can never strand the view on an empty page. */
  readonly currentPage = computed(() => Math.min(this.requestedPage(), this.totalPages()));
  readonly pagedRuns = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.runs().slice(start, start + this.pageSize);
  });
  readonly rangeStart = computed(() => (this.runs().length ? (this.currentPage() - 1) * this.pageSize + 1 : 0));
  readonly rangeEnd = computed(() => Math.min(this.currentPage() * this.pageSize, this.runs().length));

  private readonly pollHandle: ReturnType<typeof setInterval>;
  /** Guards against overlapping background polls piling up on the connection pool. */
  private pollInFlight = false;

  featureVersion = 'AML_FEATURES_V2';
  fromBusinessDate = this.today();
  toBusinessDate = this.today();
  cutoffTimestamp = `${this.today()}T23:59`;
  closeDate = this.today();

  constructor() {
    this.refresh();
    // Poll only while a run is mid-pipeline, so the progress bar advances on its own.
    // The in-flight guard matters: setInterval fires on a fixed schedule regardless of
    // whether the previous request returned, so during a slow period (a bulk upload
    // holding database connections) requests would otherwise stack up without bound and
    // exhaust the connection pool.
    this.pollHandle = setInterval(() => {
      if (this.pollInFlight) return;
      if (!this.runs().some((run) => this.isActive(run))) return;
      this.pollInFlight = true;
      this.load(true);
    }, POLL_INTERVAL_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(this.pollHandle));
  }

  refresh(): void {
    this.loading.set(true);
    this.load(false);
  }

  private load(silent: boolean): void {
    forkJoin({ runs: this.api.listRuns(), health: this.api.health() })
      .pipe(finalize(() => {
        if (silent) this.pollInFlight = false;
        else this.loading.set(false);
      }))
      .subscribe({
        next: ({ runs, health }) => {
          this.runs.set(runs);
          this.health.set(health);
          if (!this.selectedRunId() && runs.length) this.selectedRunId.set(runs[0].trainingRunId);
        },
        error: (error) => {
          if (!silent) this.alerts.error(this.message(error), 'Operations unavailable');
        },
      });
  }

  closeBusinessDay(): void {
    this.api.closeBusinessDay(this.closeDate, this.actor()).subscribe({
      next: () => this.alerts.success('Business day closed', `${this.closeDate} is eligible for controlled training export.`),
      error: (error) => this.alerts.error(this.message(error), 'Business day not closed'),
    });
  }

  fillFromUpload(): void {
    this.loadingDates.set(true);
    this.comparisonApi.getLatestBatch()
      .pipe(finalize(() => this.loadingDates.set(false)))
      .subscribe({
        next: (batch) => {
          if (batch.minTransactionDate && batch.maxTransactionDate) {
            this.fromBusinessDate = batch.minTransactionDate;
            this.toBusinessDate = batch.maxTransactionDate;
            this.cutoffTimestamp = `${batch.maxTransactionDate}T23:59`;
            this.alerts.success(
              'Dates auto-filled',
              `Batch ${batch.batchNo}: ${batch.minTransactionDate} → ${batch.maxTransactionDate}`
            );
          } else {
            this.alerts.error('Upload has no transaction date information.', 'Cannot auto-fill');
          }
        },
        error: () => this.alerts.error('No completed upload found.', 'Cannot auto-fill dates'),
      });
  }

  startPipeline(): void {
    this.loading.set(true);
    this.api.startPipeline({
      featureVersion: this.featureVersion.trim(),
      modelSegment: null,
      fromBusinessDate: this.fromBusinessDate,
      toBusinessDate: this.toBusinessDate,
      cutoffTimestamp: `${this.cutoffTimestamp}:00`,
      requestedBy: this.actor(),
    }).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (run) => {
        this.alerts.success('Pipeline started', 'Exporting dataset then training all models automatically.');
        this.selectedRunId.set(run.trainingRunId);
        this.refresh();
      },
      error: (error) => this.alerts.error(this.message(error), 'Pipeline not started'),
    });
  }

  canRetryTraining(run: AmlTrainingRun): boolean {
    return run.status === 'TRAINING_FAILED' && !!run.datasetPath;
  }

  isActive(run: AmlTrainingRun): boolean {
    return ACTIVE_STATUSES.includes(run.status);
  }

  goToPage(page: number): void {
    this.requestedPage.set(Math.max(1, Math.min(page, this.totalPages())));
  }

  previousPage(): void { this.goToPage(this.currentPage() - 1); }
  nextPage(): void { this.goToPage(this.currentPage() + 1); }

  /**
   * Complete means every model in the pipeline trained — not merely that this run's own
   * model finished. Only the pipeline sets the COMPLETED stage.
   */
  isComplete(run: AmlTrainingRun): boolean {
    return run.progressStage === 'COMPLETED';
  }

  isPartial(run: AmlTrainingRun): boolean {
    return run.progressStage === 'PARTIAL';
  }

  /** Percentage for the bar, or null when the stage has no countable total. */
  progressPercent(run: AmlTrainingRun): number | null {
    if (this.isComplete(run)) return 100;
    const total = run.progressTotal ?? 0;
    const current = run.progressCurrent ?? 0;
    if (total <= 0) return null;
    return Math.min(100, Math.round((current / total) * 100));
  }

  /** True while work is running but no countable total is available yet. */
  isIndeterminate(run: AmlTrainingRun): boolean {
    return this.isActive(run) && this.progressPercent(run) === null;
  }

  isTrainingFailed(run: AmlTrainingRun): boolean {
    return run.progressStage === 'TRAINING_FAILED' || run.status === 'TRAINING_FAILED';
  }

  progressLabel(run: AmlTrainingRun): string {
    if (this.isComplete(run)) return 'All models trained';
    if (this.isPartial(run)) return 'Finished with models missing';
    if (run.progressStage === 'TRAINING_FAILED') return 'Training failed — no models produced';
    if (run.status === 'FAILED' || run.status === 'TRAINING_FAILED') return 'Stopped';
    const labels: Record<string, string> = {
      MATERIALIZING: 'Building feature rows',
      EXPORTING: 'Writing dataset files',
      EXPORTED: 'Dataset ready',
      TRAINING: 'Training models',
    };
    return labels[run.progressStage ?? ''] ?? 'Preparing';
  }

  progressDetail(run: AmlTrainingRun): string | null {
    const total = run.progressTotal ?? 0;
    const current = run.progressCurrent ?? 0;
    if (total <= 0) return null;
    // During and after training the counters are models; earlier stages count rows.
    const unit = ['TRAINING', 'COMPLETED', 'PARTIAL'].includes(run.progressStage ?? '')
      ? 'models'
      : 'rows';
    return `${current.toLocaleString()} / ${total.toLocaleString()} ${unit}`;
  }


  retryTraining(run: AmlTrainingRun): void {
    this.actionRunId.set(run.trainingRunId);
    this.api.retryIncrementalTraining(run.trainingRunId, this.actor())
      .pipe(finalize(() => this.actionRunId.set(null)))
      .subscribe({
        next: () => {
          this.alerts.success('Retry started', 'Incremental training job re-queued.');
          this.refresh();
        },
        error: (error) => this.alerts.error(this.message(error), 'Retry failed'),
      });
  }
  statusClass(status: string): string { return `status-${status.toLowerCase().replaceAll('_', '-')}`; }
  modelLabel(modelType: string): string {
    const labels: Record<string, string> = {
      HALF_SPACE_TREES: 'Half-Space Trees',
      ONLINE_ONE_CLASS_SVM: 'Online One-Class SVM',
      ISOLATION_FOREST: 'Isolation Forest',
      ONE_CLASS_SVM: 'One-Class SVM',
      AUTOENCODER: 'Autoencoder',
    };
    return labels[modelType] || modelType.replaceAll('_', ' ');
  }
  runPurpose(run: AmlTrainingRun): string {
    return ['CREATED', 'QUEUED', 'EXPORTING', 'DATASET_READY', 'FAILED'].includes(run.status)
      ? 'Shared production snapshot'
      : this.modelLabel(run.modelType);
  }
  trainingTypeLabel(trainingType: AmlTrainingRun['trainingType']): string {
    if (trainingType === 'FULL_REBUILD') return 'Production rebuild';
    if (trainingType === 'BACKTEST') return 'Backtest comparison';
    if (trainingType === 'REPLAY') return 'Replay run';
    return trainingType.replaceAll('_', ' ');
  }
  private actor(): string { return this.auth.currentUser()?.username || 'training-ui'; }
  private today(): string { return new Date().toISOString().slice(0, 10); }
  private message(error: unknown): string {
    const payload = (error as { error?: { message?: string; detail?: string } })?.error;
    return payload?.detail || payload?.message || 'The operation could not be completed.';
  }
}
