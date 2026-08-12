import { Injectable, signal } from '@angular/core';

export type AlertKind = 'success' | 'error';

export interface AppAlert {
  kind: AlertKind;
  title: string;
  message: string;
  actionLabel: string;
  afterClose?: () => void;
}

@Injectable({ providedIn: 'root' })
export class AlertService {
  readonly current = signal<AppAlert | null>(null);

  success(title: string, message: string, afterClose?: () => void): void {
    this.current.set({ kind: 'success', title, message, actionLabel: 'Continue', afterClose });
  }

  error(message: string, title = 'Something went wrong'): void {
    this.current.set({ kind: 'error', title, message, actionLabel: 'Close' });
  }

  close(): void {
    const afterClose = this.current()?.afterClose;
    this.current.set(null);
    afterClose?.();
  }
}
