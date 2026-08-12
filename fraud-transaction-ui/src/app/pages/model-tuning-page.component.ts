import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ComparisonApiService } from '../core/comparison-api.service';
import { AlertService } from '../core/alert.service';
import { ModelTuningItem } from '../core/models';

const GROUP_ORDER = [
  'Evaluation Protocol',
  'Half-Space Trees',
  'Online One-Class SVM',
  'Isolation Forest',
  'One-Class SVM',
  'Autoencoder',
];

const GROUP_DESCRIPTIONS: Record<string, string> = {
  'Evaluation Protocol': 'Controls chronological holdout size, evaluation row cap, minimum partition data, and the shared reproducibility seed used across all training and comparison runs.',
  'Half-Space Trees': 'Incremental streaming model — updated continuously on live transaction windows. Hyperparameters apply to both production training and model comparison runs.',
  'Online One-Class SVM': 'Incremental streaming model using bounded random Fourier features. Hyperparameters apply to both production training and model comparison runs.',
  'Isolation Forest': 'Batch model trained on historical snapshots. Max samples per tree controls how many rows each tree sees — increasing it significantly improves anomaly signal on large datasets.',
  'One-Class SVM': 'Batch RBF kernel model using gamma="auto" (1/n_features) for well-calibrated boundaries on high-dimensional data. Trains on all available rows by default.',
  'Autoencoder': 'Batch neural reconstruction model — learns normal behaviour and flags high reconstruction error as anomalies. The bottleneck layer size controls compression strength.',
};

@Component({
  selector: 'app-model-tuning-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './model-tuning-page.component.html',
  styleUrls: ['./page.css', './model-tuning-page.component.css']
})
export class ModelTuningPageComponent implements OnInit {
  private readonly api = inject(ComparisonApiService);
  private readonly alerts = inject(AlertService);

  readonly items = signal<ModelTuningItem[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly message = signal('');
  readonly groups = computed(() => GROUP_ORDER
    .map((name) => ({
      name,
      description: GROUP_DESCRIPTIONS[name],
      items: this.items().filter((item) => item.groupName === name),
    }))
    .filter((group) => group.items.length > 0));

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.message.set('');
    this.api.listModelTuning().subscribe({
      next: (items) => this.items.set(items),
      error: (error) => {
        this.alerts.error(this.extractMessage(error), 'Model settings unavailable');
        this.loading.set(false);
      },
      complete: () => this.loading.set(false),
    });
  }

  save(): void {
    const values = this.items().reduce<Record<string, string>>((result, item) => {
      result[item.configKey] = String(item.configValue);
      return result;
    }, {});

    this.saving.set(true);
    this.message.set('');
    this.api.updateModelTuning(values).subscribe({
      next: (items) => {
        this.items.set(items);
        this.alerts.success(
          'Model tuning saved',
          'Production training and research analysis will use the applicable settings on their next run.'
        );
      },
      error: (error) => {
        this.alerts.error(this.extractMessage(error), 'Model tuning not saved');
        this.saving.set(false);
      },
      complete: () => this.saving.set(false),
    });
  }

  abbreviate(groupName: string): string {
    const words = groupName.split(' ').filter(w => w.length > 0);
    return words.length === 1
      ? groupName.slice(0, 3).toUpperCase()
      : words.map(w => w[0]).join('').toUpperCase();
  }

  trackByKey(_index: number, item: ModelTuningItem): string {
    return item.configKey;
  }

  private extractMessage(error: unknown): string {
    const payload = (error as { error?: { message?: string; detail?: string } })?.error;
    return payload?.detail || payload?.message || 'Unable to save model tuning settings.';
  }
}
