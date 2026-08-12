import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';

import { AlertService } from '../core/alert.service';
import { AmlGovernanceApiService } from '../core/aml-governance-api.service';
import { AuthService } from '../core/auth.service';
import {
  ActiveModelPointer,
  AmlModelRegistryEntry,
  ModelDeploymentEvent,
  ModelValidationReport,
  LayeredShadowValidationReport,
} from '../core/models';

@Component({
  selector: 'app-model-governance-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './model-governance-page.component.html',
  styleUrls: ['./page.css', './model-governance-page.component.css'],
})
export class ModelGovernancePageComponent {
  private readonly api = inject(AmlGovernanceApiService);
  private readonly authService = inject(AuthService);
  private readonly alerts = inject(AlertService);

  readonly models = signal<AmlModelRegistryEntry[]>([]);
  readonly activeModels = signal<ActiveModelPointer[]>([]);
  readonly validations = signal<ModelValidationReport[]>([]);
  readonly deployments = signal<ModelDeploymentEvent[]>([]);
  readonly selectedVersion = signal<string | null>(null);
  readonly loading = signal(false);
  readonly detailLoading = signal(false);
  readonly action = signal<'validate' | 'promote' | 'rollback' | null>(null);
  readonly layeredValidations = signal<LayeredShadowValidationReport[]>([]);
  readonly layeredValidating = signal(false);

  layeredSegment = 'RETAIL_SALARIED';
  actionReason = '';

  readonly selectedModel = computed(() =>
    this.models().find((model) => model.modelVersion === this.selectedVersion()) ?? null
  );
  readonly latestValidation = computed(() => this.validations()[0] ?? null);
  readonly selectedPointer = computed(() => {
    const model = this.selectedModel();
    if (!model) return null;
    return this.activeModels().find((pointer) =>
      pointer.modelType === model.modelType
        && this.segmentKey(pointer.modelSegment) === this.segmentKey(model.modelSegment)
    ) ?? null;
  });
  readonly championCount = computed(() => this.activeModels().length);
  readonly candidateCount = computed(() =>
    this.models().filter((model) => model.status === 'CANDIDATE').length
  );
  readonly validatedCount = computed(() =>
    this.models().filter((model) => model.status === 'VALIDATED').length
  );
  readonly isAdministrator = computed(() => {
    const role = this.authService.currentUser()?.roleName?.toUpperCase();
    return role === 'ADMIN' || role === 'AML_ADMIN';
  });
  readonly latestLayeredValidation = computed(() =>
    this.layeredValidations().find((report) =>
      report.peerGroupCode === this.normalizedLayeredSegment()
    ) ?? null
  );
  readonly allSegmentValidations = computed(() => {
    const seen = new Set<string>();
    return this.layeredValidations().filter((report) => {
      const key = report.peerGroupCode ?? 'GLOBAL';
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  });

  constructor() {
    this.refresh();
  }

  refresh(preferredVersion?: string): void {
    this.loading.set(true);
    forkJoin({
      models: this.api.listModels(),
      activeModels: this.api.listActiveModels(),
      layeredValidations: this.api.listLayeredValidations(),
    }).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: ({ models, activeModels, layeredValidations }) => {
        this.models.set(models);
        this.activeModels.set(activeModels);
        this.layeredValidations.set(layeredValidations);
        const selected = preferredVersion
          ? models.find((model) => model.modelVersion === preferredVersion)
          : models.find((model) => model.status === 'VALIDATED')
            ?? models.find((model) => model.status === 'CANDIDATE')
            ?? models.find((model) => model.status === 'CHAMPION')
            ?? models[0];
        if (selected) {
          this.selectModel(selected);
        } else {
          this.selectedVersion.set(null);
          this.validations.set([]);
          this.deployments.set([]);
        }
      },
      error: (error) => this.alerts.error(this.extractMessage(error), 'Governance data unavailable'),
    });
  }

  selectModel(model: AmlModelRegistryEntry): void {
    this.selectedVersion.set(model.modelVersion);
    this.actionReason = '';
    this.detailLoading.set(true);
    forkJoin({
      validations: this.api.listValidations(model.modelVersion),
      deployments: this.api.listDeployments(model.modelType, model.modelSegment),
    }).pipe(finalize(() => this.detailLoading.set(false))).subscribe({
      next: ({ validations, deployments }) => {
        this.validations.set(validations);
        this.deployments.set(deployments);
      },
      error: (error) => this.alerts.error(this.extractMessage(error), 'Model evidence unavailable'),
    });
  }

  validateSelected(): void {
    const model = this.selectedModel();
    if (!model || !this.isAdministrator() || this.action()) return;
    this.action.set('validate');
    const actor = this.authService.currentUser()?.username || 'governance-ui';
    this.api.validate(model.modelVersion, actor)
      .pipe(finalize(() => this.action.set(null)))
      .subscribe({
        next: (report) => {
          this.alerts.success(
            report.validationStatus === 'PASSED' ? 'Validation passed' : 'Validation recorded',
            this.validationMessage(report)
          );
          this.refresh(model.modelVersion);
        },
        error: (error) => this.alerts.error(this.extractMessage(error), 'Validation failed'),
      });
  }

  promoteSelected(): void {
    const model = this.selectedModel();
    if (!model || !this.canPromote() || !this.requireReason()) return;
    this.action.set('promote');
    this.api.promote(model.modelVersion, crypto.randomUUID(), this.actionReason.trim())
      .pipe(finalize(() => this.action.set(null)))
      .subscribe({
        next: () => {
          this.alerts.success('Model promoted', `${model.modelVersion} is now the active segment champion.`);
          this.refresh(model.modelVersion);
        },
        error: (error) => this.alerts.error(this.extractMessage(error), 'Promotion failed'),
      });
  }

  rollbackSelected(): void {
    const model = this.selectedModel();
    const pointer = this.selectedPointer();
    if (!model || !pointer || !this.canRollback() || !this.requireReason()) return;
    this.action.set('rollback');
    this.api.rollback(model.modelVersion, crypto.randomUUID(), this.actionReason.trim())
      .pipe(finalize(() => this.action.set(null)))
      .subscribe({
        next: () => {
          this.alerts.success(
            'Rollback complete',
            `${pointer.previousModelVersion} has been restored for ${this.segmentLabel(pointer.modelSegment)}.`
          );
          this.refresh(pointer.previousModelVersion ?? undefined);
        },
        error: (error) => this.alerts.error(this.extractMessage(error), 'Rollback failed'),
      });
  }

  validateLayered(): void {
    if (!this.isAdministrator() || this.layeredValidating() || !this.normalizedLayeredSegment()) return;
    this.layeredValidating.set(true);
    const actor = this.authService.currentUser()?.username || 'governance-ui';
    this.api.validateLayered(this.normalizedLayeredSegment(), actor)
      .pipe(finalize(() => this.layeredValidating.set(false)))
      .subscribe({
        next: (report) => {
          this.alerts.success(
            report.validationStatus === 'PASSED' ? 'Validation passed' : 'Validation recorded',
            report.blockingReasons[0] || `${report.metrics.sampleCount.toLocaleString()} shadow predictions evaluated.`
          );
          this.refresh(this.selectedVersion() || undefined);
        },
        error: (error) => this.alerts.error(this.extractMessage(error), 'Layered validation failed'),
      });
  }

  canValidate(): boolean {
    const status = this.selectedModel()?.status;
    return this.isAdministrator() && (status === 'CANDIDATE' || status === 'VALIDATED');
  }

  canPromote(): boolean {
    return this.isAdministrator() && this.selectedModel()?.status === 'VALIDATED';
  }

  canRollback(): boolean {
    const model = this.selectedModel();
    const pointer = this.selectedPointer();
    return this.isAdministrator()
      && model?.status === 'CHAMPION'
      && pointer?.activeModelVersion === model.modelVersion
      && !!pointer.previousModelVersion;
  }

  segmentLabel(segment?: string | null): string {
    return segment || 'GLOBAL';
  }

  statusClass(status: string): string {
    return `status-${status.toLowerCase().replaceAll('_', '-')}`;
  }

  rate(value?: number | null): string {
    return value == null ? '—' : `${(value * 100).toFixed(2)}%`;
  }

  private requireReason(): boolean {
    if (this.actionReason.trim().length >= 10) return true;
    this.alerts.error('Enter an audit reason of at least 10 characters.', 'Reason required');
    return false;
  }

  private validationMessage(report: ModelValidationReport): string {
    if (report.validationStatus === 'PASSED') {
      return `${report.metrics.sampleCount.toLocaleString()} silent predictions passed the configured safety gates.`;
    }
    return report.failureReason || 'The result was persisted for review.';
  }

  private segmentKey(segment?: string | null): string {
    return segment || 'GLOBAL';
  }

  private normalizedLayeredSegment(): string {
    return this.layeredSegment.trim().toUpperCase();
  }

  private extractMessage(error: unknown): string {
    const payload = (error as { error?: { message?: string; detail?: string } })?.error;
    return payload?.detail || payload?.message || 'Something went wrong.';
  }
}
