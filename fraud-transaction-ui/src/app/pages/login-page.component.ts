import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { AlertService } from '../core/alert.service';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login-page.component.html',
  styleUrls: ['./page.css', './auth-page.css']
})
export class LoginPageComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly alerts = inject(AlertService);

  readonly message = signal('');
  readonly submitting = signal(false);

  username = '';
  password = '';

  login(): void {
    if (!this.username.trim() || !this.password) {
      this.alerts.error('Enter your username and password.', 'Login details required');
      return;
    }

    this.submitting.set(true);
    this.message.set('');
    this.authService.login({
      username: this.username.trim(),
      password: this.password,
    }).subscribe({
      next: () => this.alerts.success(
        'Login successful',
        'Welcome back. Your workspace is ready.',
        () => this.router.navigate(['/datasets'])
      ),
      error: (error) => {
        this.alerts.error(this.extractMessage(error), 'Login failed');
        this.submitting.set(false);
      },
    });
  }

  private extractMessage(error: unknown): string {
    const payload = (error as { error?: { message?: string; detail?: string } })?.error;
    return payload?.detail || payload?.message || 'Something went wrong.';
  }
}
