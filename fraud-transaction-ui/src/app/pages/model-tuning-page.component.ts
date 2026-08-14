import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { ComparisonApiService } from '../core/comparison-api.service';
import { AlertService } from '../core/alert.service';
import { ModelTuningItem } from '../core/models';

const GROUP_DESCRIPTIONS: Record<string, string> = {
  'Evaluation Protocol': 'Controls chronological holdout size, evaluation row cap, minimum partition data, and the shared reproducibility seed used across all training and comparison runs.',
  'Isolation Forest': 'Tree-based anomaly detector trained on the selected immutable transaction snapshot.',
  'Autoencoder': 'Neural reconstruction detector that learns normal behaviour and flags high reconstruction error.',
  'Local Outlier Factor': 'Local-density detector that identifies transactions unlike their nearest neighbours.',
  'XGBoost': 'Gradient-boosted decision trees optimized for nonlinear fraud classification on labelled transactions.',
  'Random Forest': 'Class-balanced tree ensemble providing a robust supervised benchmark.',
  'Logistic Regression': 'Regularized, interpretable probability baseline for labelled fraud detection.',
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
  readonly learningMode = signal<'UNSUPERVISED' | 'SUPERVISED'>('UNSUPERVISED');
  readonly groups = computed(() => this.groupOrder()
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
    forkJoin({ tuning: this.api.listModelTuning(), settings: this.api.listColdStartConfigs() }).subscribe({
      next: ({ tuning, settings }) => {
        this.learningMode.set(settings.find((item) => item.configKey === 'system.learning_mode')?.configValue === 'SUPERVISED'
          ? 'SUPERVISED' : 'UNSUPERVISED');
        this.items.set(tuning);
      },
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

  private groupOrder(): string[] {
    return this.learningMode() === 'SUPERVISED'
      ? ['Evaluation Protocol', 'XGBoost', 'Random Forest', 'Logistic Regression']
      : ['Evaluation Protocol', 'Isolation Forest', 'Autoencoder', 'Local Outlier Factor'];
  }

  private extractMessage(error: unknown): string {
    const payload = (error as { error?: { message?: string; detail?: string } })?.error;
    return payload?.detail || payload?.message || 'Unable to save model tuning settings.';
  }
}
