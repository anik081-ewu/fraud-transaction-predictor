import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/login']);
};

export const administratorGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const role = authService.currentUser()?.roleName?.toUpperCase();

  if (authService.isAuthenticated() && (role === 'ADMIN' || role === 'AML_ADMIN')) {
    return true;
  }

  return router.createUrlTree(['/datasets']);
};
