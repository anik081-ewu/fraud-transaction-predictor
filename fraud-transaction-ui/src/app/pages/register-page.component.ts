import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { AlertService } from '../core/alert.service';

@Component({
  selector: 'app-register-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './register-page.component.html',
  styleUrls: ['./page.css', './auth-page.css']
})
export class RegisterPageComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly alerts = inject(AlertService);

  readonly message = signal('');
  readonly submitting = signal(false);

  fullName = '';
  username = '';
  password = '';

  register(): void {
    if (!this.fullName.trim() || !this.username.trim() || !this.password) {
      this.alerts.error('Complete all required fields.', 'Registration details required');
      return;
    }

    this.submitting.set(true);
    this.message.set('');
    this.authService.register({
      fullName: this.fullName.trim(),
      username: this.username.trim(),
      password: this.password,
    }).subscribe({
      next: () => this.alerts.success(
        'Registration complete',
        'The user account was created successfully.',
        () => this.router.navigate(['/login'])
      ),
      error: (error) => {
        this.alerts.error(this.extractMessage(error), 'Registration failed');
        this.submitting.set(false);
      },
    });
  }

  private extractMessage(error: unknown): string {
    const payload = (error as { error?: { message?: string; detail?: string } })?.error;
    return payload?.detail || payload?.message || 'Registration failed. Please try again.';
  }
}
