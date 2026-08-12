import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { AlertService } from '../core/alert.service';
import { CaseApiService } from '../core/case-api.service';
import { CaseRecord } from '../core/models';

interface AnomalyExplanation {
  code: string;
  title: string;
  explanation: string;
}

@Component({
  selector: 'app-cases-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './cases-page.component.html',
  styleUrls: ['./page.css', './cases-page.component.css']
})
export class CasesPageComponent implements OnInit {
  private readonly caseApi = inject(CaseApiService);
  private readonly authService = inject(AuthService);
  private readonly alerts = inject(AlertService);
  private readonly route = inject(ActivatedRoute);

  readonly cases = signal<CaseRecord[]>([]);
  readonly selectedCaseId = signal<number | null>(null);
  readonly message = signal('');
  readonly loading = signal(false);
  readonly actionInProgress = signal('');

  readonly searchText = signal('');
  readonly statusFilter = signal('ACTIVE');
  noteText = '';

  readonly filteredCases = computed(() => {
    const query = this.searchText().trim().toLowerCase();
    return this.cases().filter((caseRecord) => {
      const statusFilter = this.statusFilter();
      const statusMatches = statusFilter === 'ALL'
        || (statusFilter === 'ACTIVE' && !['FALSE_POSITIVE', 'STR_GENERATED', 'CLOSED'].includes(caseRecord.status))
        || caseRecord.status === statusFilter;
      const queryMatches = !query || [
        caseRecord.caseNo,
        caseRecord.transactionId,
        caseRecord.accountId,
        caseRecord.title,
      ].some((value) => value.toLowerCase().includes(query));
      return statusMatches && queryMatches;
    });
  });

  readonly selectedCase = computed(() =>
    this.cases().find((item) => item.id === this.selectedCaseId()) ?? null
  );
  readonly activeCount = computed(() =>
    this.cases().filter((item) => !['FALSE_POSITIVE', 'STR_GENERATED', 'CLOSED'].includes(item.status)).length
  );
  readonly strCount = computed(() => this.cases().filter((item) => item.status === 'STR_GENERATED').length);
  readonly falsePositiveCount = computed(() =>
    this.cases().filter((item) => item.status === 'FALSE_POSITIVE').length
  );
  readonly anomalyExplanations = computed(() =>
    this.parseReasonCodes(this.selectedCase()?.predictionEvidence?.reasonCodes)
      .map((code) => this.explainReason(code))
  );
  readonly investigationSuggestion = computed(() => {
    const evidence = this.selectedCase()?.predictionEvidence;
    if (!evidence) return '';
    if (evidence.riskLevel === 'HIGH') {
      return 'Treat this as an urgent investigation. Confirm the transaction purpose, source of funds, beneficiary and related account activity. A draft STR is generated automatically for HIGH-risk cases.';
    }
    if (evidence.riskLevel === 'MEDIUM') {
      return 'Review the customer history and supporting transaction context. Confirm whether the amount, peer deviation and model agreement have a legitimate explanation before generating an STR or marking the case false positive.';
    }
    return 'Review the highlighted signals and monitor related activity. Escalate only when the transaction context cannot reasonably explain the detected deviation.';
  });

  ngOnInit(): void {
    const preferredCaseId = Number(this.route.snapshot.queryParamMap.get('caseId'));
    this.loadCases(Number.isFinite(preferredCaseId) && preferredCaseId > 0 ? preferredCaseId : undefined);
  }

  loadCases(preferredCaseId?: number): void {
    this.loading.set(true);
    this.caseApi.listCases().subscribe({
      next: (cases) => {
        this.cases.set(cases);
        const selectedId = preferredCaseId
          ?? this.selectedCaseId()
          ?? cases[0]?.id
          ?? null;
        this.selectedCaseId.set(selectedId);
        if (selectedId) {
          this.loadCaseDetail(selectedId);
        }
      },
      error: (error) => {
        this.alerts.error(this.extractMessage(error), 'Cases unavailable');
        this.loading.set(false);
      },
      complete: () => this.loading.set(false),
    });
  }

  selectCase(caseId: number): void {
    this.selectedCaseId.set(caseId);
    this.loadCaseDetail(caseId);
  }

  updateSearchText(value: string): void {
    this.searchText.set(value);
    this.selectFirstVisibleCaseIfNeeded();
  }

  updateStatusFilter(value: string): void {
    this.statusFilter.set(value);
    this.selectFirstVisibleCaseIfNeeded();
  }

  markFalsePositive(): void {
    const caseRecord = this.selectedCase();
    if (!caseRecord || this.actionInProgress()) {
      return;
    }
    if (!window.confirm(`Mark ${caseRecord.caseNo} as a false positive?`)) {
      return;
    }
    this.actionInProgress.set('FALSE_POSITIVE');
    this.message.set('');
    this.caseApi.markFalsePositive(caseRecord.id, this.currentUsername()).subscribe({
      next: (updated) => {
        this.replaceCase(updated);
        this.message.set(`${updated.caseNo} was marked as a false positive.`);
        this.alerts.success('Case updated', `${updated.caseNo} was marked as a false positive.`);
        this.actionInProgress.set('');
      },
      error: (error) => {
        this.alerts.error(this.extractMessage(error), 'Case not updated');
        this.actionInProgress.set('');
      },
    });
  }

  generateStrXml(): void {
    const caseRecord = this.selectedCase();
    if (!caseRecord || this.actionInProgress()) {
      return;
    }
    this.actionInProgress.set('STR');
    this.message.set('');
    this.caseApi.generateStrXml(caseRecord.id, this.currentUsername()).subscribe({
      next: (response) => {
        const fileName = this.downloadFileName(response.headers.get('content-disposition'), caseRecord.caseNo);
        const downloadUrl = URL.createObjectURL(response.body ?? new Blob([], { type: 'application/xml' }));
        const link = document.createElement('a');
        link.href = downloadUrl;
        link.download = fileName;
        link.click();
        URL.revokeObjectURL(downloadUrl);
        this.message.set(`Draft STR XML generated for ${caseRecord.caseNo}.`);
        this.alerts.success('STR XML generated', `The draft report for ${caseRecord.caseNo} was downloaded.`);
        this.actionInProgress.set('');
        this.loadCases(caseRecord.id);
      },
      error: (error) => {
        this.alerts.error(this.extractMessage(error), 'STR generation failed');
        this.actionInProgress.set('');
      },
    });
  }

  addNote(): void {
    const caseRecord = this.selectedCase();
    if (!caseRecord || !this.noteText.trim()) {
      return;
    }
    this.caseApi.addNote(caseRecord.id, {
      noteText: this.noteText.trim(),
      createdBy: this.currentUsername(),
    }).subscribe({
      next: (updated) => {
        this.noteText = '';
        this.replaceCase(updated);
        this.message.set(`Note added to ${updated.caseNo}.`);
        this.alerts.success('Note added', `The note was added to ${updated.caseNo}.`);
      },
      error: (error) => this.alerts.error(this.extractMessage(error), 'Note not added'),
    });
  }

  isResolved(caseRecord: CaseRecord): boolean {
    return ['FALSE_POSITIVE', 'STR_GENERATED', 'CLOSED'].includes(caseRecord.status);
  }

  private loadCaseDetail(caseId: number): void {
    this.caseApi.getCase(caseId).subscribe({
      next: (caseRecord) => this.replaceCase(caseRecord),
      error: (error) => this.alerts.error(this.extractMessage(error), 'Case details unavailable'),
    });
  }

  private replaceCase(caseRecord: CaseRecord): void {
    this.cases.set(
      this.cases().some((item) => item.id === caseRecord.id)
        ? this.cases().map((item) => item.id === caseRecord.id ? caseRecord : item)
        : [caseRecord, ...this.cases()]
    );
    this.selectedCaseId.set(caseRecord.id);
  }

  private selectFirstVisibleCaseIfNeeded(): void {
    const visibleCases = this.filteredCases();
    if (!visibleCases.some((item) => item.id === this.selectedCaseId())) {
      this.selectedCaseId.set(visibleCases[0]?.id ?? null);
    }
  }

  private parseReasonCodes(raw: string | null | undefined): string[] {
    if (!raw?.trim()) return [];
    try {
      const parsed: unknown = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        return parsed.filter((item): item is string => typeof item === 'string' && !!item.trim());
      }
    } catch {
      // Historical records may contain a delimited value instead of JSON.
    }
    return raw
      .split(/[;,]/)
      .map((item) => item.trim().replace(/^['"]|['"]$/g, ''))
      .filter(Boolean);
  }

  private explainReason(rawCode: string): AnomalyExplanation {
    const code = rawCode.trim().toUpperCase().replace(/[\s-]+/g, '_');
    const known: Record<string, Omit<AnomalyExplanation, 'code'>> = {
      AMOUNT_ABOVE_2X_RECENT_AVERAGE: {
        title: 'Amount is much higher than recent activity',
        explanation: 'The amount is more than twice the customer’s recent average and does not match their usual transaction pattern.',
      },
      AMOUNT_ABOVE_4X_RECENT_AVERAGE: {
        title: 'Amount is more than four times the recent average',
        explanation: 'The transaction is far larger than the customer’s recent trusted activity and requires purpose and source-of-funds verification.',
      },
      AMOUNT_ABOVE_8X_RECENT_AVERAGE: {
        title: 'Amount is extremely high for this customer',
        explanation: 'The transaction exceeds eight times the customer’s recent average, representing an extreme break from personal behaviour.',
      },
      AMOUNT_ABOVE_2X_PEER_AVERAGE: {
        title: 'Amount is high for comparable customers',
        explanation: 'The amount is more than twice the average for customers in the same occupation and age peer group.',
      },
      AMOUNT_ABOVE_4X_PEER_AVERAGE: {
        title: 'Amount is unusually high for comparable customers',
        explanation: 'The amount is more than four times the average for customers in the same occupation and age peer group.',
      },
      AMOUNT_ABOVE_8X_PEER_AVERAGE: {
        title: 'Amount is an extreme peer-group deviation',
        explanation: 'The amount exceeds eight times the peer-group average and is highly unusual for comparable customers.',
      },
      PEER_AMOUNT_ZSCORE_ABOVE_3: {
        title: 'Amount is a statistical peer-group outlier',
        explanation: 'The amount is more than three standard deviations from the peer baseline, making it rare among comparable customers.',
      },
      PEER_FREQUENCY_ABOVE_95TH_PERCENTILE: {
        title: 'Transaction frequency is unusually high',
        explanation: 'This account is transacting more frequently than at least 95% of comparable accounts.',
      },
      LOW_CUSTOMER_PROFILE_CONFIDENCE: {
        title: 'Limited trusted customer history',
        explanation: 'Too few trusted historical transactions exist to establish a highly reliable personal baseline, so additional contextual review is needed.',
      },
      NEW_LOCATION: {
        title: 'Transaction occurred in a new location',
        explanation: 'The location has not appeared in the customer’s trusted recent history and may require verification.',
      },
      ISOLATION_FOREST_HIGH_ANOMALY_SCORE: {
        title: 'Isolation Forest detected an unusual pattern',
        explanation: 'The transaction is isolated from patterns learned from historical transactions across the selected behavioural features.',
      },
      ONE_CLASS_SVM_HIGH_ANOMALY_SCORE: {
        title: 'One-Class SVM placed it outside normal behaviour',
        explanation: 'The transaction falls outside the model’s learned boundary for normal historical activity.',
      },
      AUTOENCODER_HIGH_ANOMALY_SCORE: {
        title: 'Autoencoder found an unfamiliar feature pattern',
        explanation: 'The feature combination differs substantially from learned patterns and produced a high reconstruction error.',
      },
      ML_ENSEMBLE_UNANIMOUS_ANOMALY: {
        title: 'All selected ML models agreed',
        explanation: 'Every participating production model classified this transaction as anomalous, giving the ML ensemble its maximum contribution.',
      },
      HIGH_TRANSACTION_TO_EXPECTED_TURNOVER: {
        title: 'Large amount relative to expected turnover',
        explanation: 'The transaction consumes an unusually large portion of the expected turnover for this customer or peer profile.',
      },
      TRANSACTION_ABOVE_50_PERCENT_EXPECTED_MONTHLY_TURNOVER: {
        title: 'Transaction exceeds half of expected monthly turnover',
        explanation: 'A single transaction represents more than 50% of expected monthly turnover and warrants source-of-funds and purpose verification.',
      },
      TRANSACTION_ABOVE_25_PERCENT_EXPECTED_MONTHLY_TURNOVER: {
        title: 'Transaction is large relative to monthly turnover',
        explanation: 'A single transaction represents more than 25% of expected monthly turnover and should be checked against the customer’s stated activity.',
      },
    };
    return known[code]
      ? { code, ...known[code] }
      : {
          code,
          title: this.humanizeReasonCode(code),
          explanation: 'This signal increased the anomaly assessment. Review the customer and transaction context to determine whether the deviation has a legitimate explanation.',
        };
  }

  private humanizeReasonCode(code: string): string {
    return code
      .toLowerCase()
      .split('_')
      .filter(Boolean)
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }

  private currentUsername(): string {
    return this.authService.currentUser()?.username ?? 'system';
  }

  private downloadFileName(disposition: string | null, caseNo: string): string {
    const match = disposition?.match(/filename="?([^";]+)"?/i);
    return match?.[1] || `STR-${caseNo}.xml`;
  }

  private extractMessage(error: unknown): string {
    const payload = (error as { error?: { message?: string; detail?: string } })?.error;
    return payload?.detail || payload?.message || 'Something went wrong.';
  }
}
