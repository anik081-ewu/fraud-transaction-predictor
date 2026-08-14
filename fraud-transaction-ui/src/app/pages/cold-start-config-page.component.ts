import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ComparisonApiService } from '../core/comparison-api.service';
import { AlertService } from '../core/alert.service';
import { ColdStartConfigItem } from '../core/models';

@Component({
  selector: 'app-settings-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cold-start-config-page.component.html',
  styleUrl: './page.css'
})
export class SettingsPageComponent implements OnInit {
  private readonly api = inject(ComparisonApiService);
  private readonly alerts = inject(AlertService);

  readonly items = signal<ColdStartConfigItem[]>([]);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.listColdStartConfigs().subscribe({
      next: (items) => this.items.set(items),
      error: (error) => this.alerts.error(this.extractMessage(error), 'Settings unavailable'),
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
        this.alerts.success('Settings saved', 'The selected system type and cold start policy are now active.');
      },
      error: (error) => this.alerts.error(this.extractMessage(error), 'Settings not saved'),
    });
  }

  labelFor(configKey: string): string {
    const labels: Record<string, string> = {
      'ml.cold_start.enabled': 'Cold Start Handling',
      'ml.min_transaction_count_before_predict': 'Minimum Transaction History',
      'system.learning_mode': 'System Type',
    };
    return labels[configKey] ?? configKey;
  }

  isBoolean(item: ColdStartConfigItem): boolean {
    return item.configKey === 'ml.cold_start.enabled';
  }

  isSystemType(item: ColdStartConfigItem): boolean {
    return item.configKey === 'system.learning_mode';
  }

  systemTypeItem(): ColdStartConfigItem | undefined {
    return this.items().find((item) => this.isSystemType(item));
  }

  coldStartItems(): ColdStartConfigItem[] {
    return this.items().filter((item) => !this.isSystemType(item));
  }

  private extractMessage(error: unknown): string {
    const message = (error as { error?: { message?: string; detail?: string } })?.error;
    return message?.detail || message?.message || 'Something went wrong.';
  }
}
