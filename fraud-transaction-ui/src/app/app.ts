import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';

import { AuthService } from './core/auth.service';
import { AppAlertComponent } from './shared/app-alert.component';

@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, AppAlertComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly currentUser = this.authService.currentUser;
  readonly isAuthenticated = this.authService.isAuthenticated;
  readonly isAdministrator = computed(() => {
    const role = this.currentUser()?.roleName?.toUpperCase();
    return role === 'ADMIN' || role === 'AML_ADMIN';
  });
  readonly displayName = computed(() => this.currentUser()?.fullName || this.currentUser()?.username || 'Guest');
  readonly userInitials = computed(() => {
    const name = this.displayName().trim();
    return name
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('') || 'U';
  });
  readonly isAuthPage = signal(this.isAuthRoute(this.router.url));
  readonly sidebarCollapsed = signal(localStorage.getItem('ftd.sidebar.collapsed') === 'true');
  readonly mobileMenuOpen = signal(false);

  constructor() {
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.isAuthPage.set(this.isAuthRoute(event.urlAfterRedirects));
        this.mobileMenuOpen.set(false);
      });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  toggleSidebar(): void {
    const collapsed = !this.sidebarCollapsed();
    this.sidebarCollapsed.set(collapsed);
    localStorage.setItem('ftd.sidebar.collapsed', String(collapsed));
  }

  toggleMobileMenu(): void {
    this.mobileMenuOpen.update((open) => !open);
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen.set(false);
  }

  private isAuthRoute(url: string): boolean {
    return url === '/login' || url === '/register';
  }
}
