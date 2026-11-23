import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : inject(Router).createUrlTree(['/connexion']);
};

/** Route réservée à certains rôles : data: { roles: ['CHEF_DEPARTEMENT', 'ADMIN'] } */
export const roleGuard: CanActivateFn = route => {
  const auth = inject(AuthService);
  const roles = (route.data['roles'] ?? []) as Role[];
  return auth.hasRole(...roles) ? true : inject(Router).createUrlTree(['/tableau-de-bord']);
};
