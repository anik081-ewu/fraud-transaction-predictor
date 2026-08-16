import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { AlertService } from '../core/alert.service';
import { CaseApiService } from '../core/case-api.service';
import { TransactionRecord } from '../core/models';

@Component({
  selector: 'app-manual-case-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './manual-case-page.component.html',
  styleUrls: ['./page.css', './manual-case-page.component.css']
})
export class ManualCasePageComponent implements OnInit {
  private readonly caseApi = inject(CaseApiService);
  private readonly authService = inject(AuthService);
  private readonly alerts = inject(AlertService);
  private readonly router = inject(Router);

  readonly transactions = signal<TransactionRecord[]>([]);
  readonly selectedTransaction = signal<TransactionRecord | null>(null);
  readonly message = signal('');
  readonly loading = signal(false);
  readonly creating = signal(false);

  query = '';
  title = '';
  priority = 'MEDIUM';
  assignedTo = '';

  ngOnInit(): void {
    this.search();
  }

  search(): void {
    this.loading.set(true);
    this.message.set('');
    this.caseApi.searchTransactions(this.query.trim(), 0, 25).subscribe({
      next: (response) => this.transactions.set(response.content),
      error: (error) => {
        this.alerts.error(this.extractMessage(error), 'Transaction search failed');
        this.loading.set(false);
      },
      complete: () => this.loading.set(false),
    });
  }

  selectTransaction(transaction: TransactionRecord): void {
    this.selectedTransaction.set(transaction);
    this.title = `Manual review for transaction ${transaction.transactionId}`;
  }

  createCase(): void {
    const transaction = this.selectedTransaction();
    if (!transaction) {
      this.alerts.error('Select a transaction first.', 'No transaction selected');
      return;
    }
    this.creating.set(true);
    this.message.set('');
    this.caseApi.createCase({
      fraudAlertId: null,
      transactionId: transaction.transactionId,
      accountId: transaction.accountId,
      title: this.title.trim() || `Manual review for transaction ${transaction.transactionId}`,
      priority: this.priority,
      assignedTo: this.assignedTo.trim() || null,
      createdBy: this.authService.currentUser()?.username ?? 'system',
    }).subscribe({
      next: (caseRecord) => {
        this.creating.set(false);
        this.alerts.success(
          'Case created',
          `${caseRecord.caseNo} was added to the Case Management review queue.`
        );
        this.router.navigate(['/cases'], { queryParams: { caseId: caseRecord.id } });
      },
      error: (error) => {
        this.alerts.error(this.extractMessage(error), 'Case not created');
        this.creating.set(false);
      },
    });
  }

  private extractMessage(error: unknown): string {
    const payload = (error as { error?: { message?: string; detail?: string } })?.error;
    return payload?.detail || payload?.message || 'Something went wrong.';
  }
}
