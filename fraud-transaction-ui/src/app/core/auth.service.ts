import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs/operators';

import { AuthResponse, AuthUser, LoginRequest, RegisterRequest } from './auth.models';

const TOKEN_KEY = 'ftd.auth.token';
const USER_KEY = 'ftd.auth.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly backendBaseUrl = 'http://localhost:8080';

  readonly token = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  readonly currentUser = signal<AuthUser | null>(this.readStoredUser());
  readonly isAuthenticated = computed(() => this.isTokenUsable(this.token()));

  constructor() {
    if (!this.isTokenUsable(this.token())) {
      this.logout();
    }
  }

  login(request: LoginRequest) {
    return this.http.post<AuthResponse>(`${this.backendBaseUrl}/api/auth/login`, request).pipe(
      tap((response) => this.storeSession(response))
    );
  }

  register(request: RegisterRequest) {
    return this.http.post<AuthUser>(`${this.backendBaseUrl}/api/auth/register`, request);
  }

  fetchCurrentUser() {
    return this.http.get<AuthUser>(`${this.backendBaseUrl}/api/auth/me`).pipe(
      tap((user) => {
        this.currentUser.set(user);
        localStorage.setItem(USER_KEY, JSON.stringify(user));
      })
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.token.set(null);
    this.currentUser.set(null);
  }

  private storeSession(response: AuthResponse): void {
    this.token.set(response.token);
    this.currentUser.set(response.user);
    localStorage.setItem(TOKEN_KEY, response.token);
    localStorage.setItem(USER_KEY, JSON.stringify(response.user));
  }

  private readStoredUser(): AuthUser | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as AuthUser;
    } catch {
      return null;
    }
  }

  private isTokenUsable(token: string | null): boolean {
    if (!token) return false;
    try {
      const payload = JSON.parse(this.decodeBase64Url(token.split('.')[1])) as { exp?: number };
      return typeof payload.exp === 'number' && payload.exp > Math.floor(Date.now() / 1000);
    } catch {
      return false;
    }
  }

  private decodeBase64Url(value: string | undefined): string {
    if (!value) throw new Error('Token payload is missing');
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    const padding = '='.repeat((4 - normalized.length % 4) % 4);
    return decodeURIComponent(Array.from(atob(normalized + padding))
      .map((character) => `%${character.charCodeAt(0).toString(16).padStart(2, '0')}`)
      .join(''));
  }
}
