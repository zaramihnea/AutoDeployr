import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { map, take } from 'rxjs';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.isAuthenticated$.pipe(
    take(1),
    map(isAuthenticated => {
      // If authenticated, allow access
      if (isAuthenticated) {
        return true;
      }

      // If not authenticated, redirect to login with return URL
      return router.createUrlTree(['/login'], {
        queryParams: { returnUrl: state.url }
      });
    })
  );
};

export const adminGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.currentUser$.pipe(
    take(1),
    map(user => {
      // Check if user is admin
      if (user && user.roles.includes('ROLE_ADMIN')) {
        return true;
      }

      // If logged in but not admin, redirect to dashboard
      if (user) {
        return router.createUrlTree(['/dashboard']);
      }

      // If not logged in, redirect to login
      return router.createUrlTree(['/login'], {
        queryParams: { returnUrl: state.url }
      });
    })
  );
};