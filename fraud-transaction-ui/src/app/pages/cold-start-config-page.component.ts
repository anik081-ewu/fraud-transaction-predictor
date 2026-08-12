import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ComparisonApiService } from '../core/comparison-api.service';
import { AlertService } from '../core/alert.service';
import { ColdStartConfigItem } from '../core/models';

@Component({
  selector: 'app-cold-start-config-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cold-start-config-page.component.html',
  styleUrl: './page.css'
})
export class ColdStartConfigPageComponent implements OnInit {
  private readonly api = inject(ComparisonApiService);
  private readonly alerts = inject(AlertService);

  readonly items = signal<ColdStartConfigItem[]>([]);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.listColdStartConfigs().subscribe({
      next: (items) => this.items.set(items),
      error: (error) => this.alerts.error(this.extractMessage(error), 'Cold start settings unavailable'),
    });
  }

  save(): void {
    const values = this.items().reduce<Record<string, string>>((acc, item) => {
      acc[item.configKey] = item.configValue;
      return acc;
    }, {});
    this.api.updateColdStartConfigs(values).subscribe({
      next: (items) => {
        this.items.set(items);
        this.alerts.success('Cold start saved', 'Updated settings are now active.');
      },
      error: (error) => this.alerts.error(this.extractMessage(error), 'Cold start not saved'),
    });
  }

  labelFor(configKey: string): string {
    const labels: Record<string, string> = {
      'ml.cold_start.enabled': 'Cold Start Handling',
      'ml.min_transaction_count_before_predict': 'Minimum Transaction History',
    };
    return labels[configKey] ?? configKey;
  }

  isBoolean(item: ColdStartConfigItem): boolean {
    return item.configKey === 'ml.cold_start.enabled';
  }

  private extractMessage(error: unknown): string {
    const message = (error as { error?: { message?: string; detail?: string } })?.error;
    return message?.detail || message?.message || 'Something went wrong.';
  }
}
