import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval, switchMap } from 'rxjs';
import { takeWhile } from 'rxjs/operators';

import { AuthService } from '../core/auth.service';
import { AlertService } from '../core/alert.service';
import { ComparisonApiService } from '../core/comparison-api.service';
import { BatchSummaryResponse } from '../core/models';

@Component({
  selector: 'app-upload-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './upload-page.component.html',
  styleUrls: ['./page.css', './upload-page.component.css']
})
export class UploadPageComponent implements OnInit {
  private readonly api = inject(ComparisonApiService);
  private readonly authService = inject(AuthService);
  private readonly alerts = inject(AlertService);
  private readonly destroyRef = inject(DestroyRef);

  readonly uploading = signal(false);
  readonly polling = signal(false);
  readonly batchNo = signal<string | null>(null);
  readonly batchResult = signal<BatchSummaryResponse | null>(null);
  readonly learningMode = signal<'UNSUPERVISED' | 'SUPERVISED'>('UNSUPERVISED');

  selectedFile: File | null = null;

  ngOnInit(): void {
    this.api.listColdStartConfigs().subscribe({
      next: (settings) => this.learningMode.set(
        settings.find((item) => item.configKey === 'system.learning_mode')?.configValue === 'SUPERVISED'
          ? 'SUPERVISED'
          : 'UNSUPERVISED'
      ),
      error: () => this.alerts.error('Could not load the active system type from Settings.', 'Settings unavailable'),
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
    this.batchNo.set(null);
    this.batchResult.set(null);
  }

  upload(): void {
    if (!this.selectedFile) {
      this.alerts.error('Select an Excel or CSV file first.', 'No file selected');
      return;
    }

    this.uploading.set(true);
    this.batchResult.set(null);
    this.batchNo.set(null);
    const uploadedBy = this.authService.currentUser()?.username || 'comparison-ui';

    this.api.uploadDataset(this.selectedFile, uploadedBy).subscribe({
      next: (response) => {
        this.batchNo.set(response.batchNo);
        this.uploading.set(false);
        this.startPolling(response.batchNo);
      },
      error: (error) => {
        const payload = (error as { error?: { message?: string; detail?: string } })?.error;
        const message = payload?.detail || payload?.message || 'Upload failed.';
        this.alerts.error(message, 'Upload failed');
        this.uploading.set(false);
      },
    });
  }

  private startPolling(batchNo: string): void {
    this.polling.set(true);
    interval(2000).pipe(
      switchMap(() => this.api.getBatchStatus(batchNo)),
      takeWhile(r => r.status === 'QUEUED' || r.status === 'PROCESSING', true),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (result) => {
        this.batchResult.set(result);
        if (result.status !== 'QUEUED' && result.status !== 'PROCESSING') {
          this.polling.set(false);
          if (result.status === 'COMPLETED') {
            this.alerts.success(
              'Upload complete',
              `${(result.successRows ?? 0).toLocaleString()} rows imported successfully.`
            );
          } else {
            this.alerts.error(`Batch ended with status: ${result.status}`, 'Upload failed');
          }
        }
      },
      error: () => {
        this.polling.set(false);
        this.alerts.error('Could not read batch status — check the uploads list manually.', 'Status check failed');
      },
    });
  }
}
