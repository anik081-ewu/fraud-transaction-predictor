import { CommonModule } from '@angular/common';
import { Component, HostListener, inject } from '@angular/core';

import { AlertService } from '../core/alert.service';

@Component({
  selector: 'app-alert',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app-alert.component.html',
  styleUrl: './app-alert.component.css',
})
export class AppAlertComponent {
  readonly alerts = inject(AlertService);

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    if (this.alerts.current()) {
      this.alerts.close();
    }
  }
}
