import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  it('conserve le jeton et le rôle après la connexion', () => {
    auth.login('chef@smartdept.ma', 'Demo@2026').subscribe();
    http.expectOne('/api/auth/login').flush({
      token: 'jwt', tokenType: 'Bearer', expiresInSeconds: 3600,
      user: { id: 2, email: 'chef@smartdept.ma', firstName: 'Karim', lastName: 'El Fassi', fullName: 'Karim El Fassi', role: 'CHEF_DEPARTEMENT', enabled: true, emailNotifications: true }
    });
    expect(auth.token).toBe('jwt');
    expect(auth.isLoggedIn()).toBe(true);
    expect(auth.isManager()).toBe(true);
    expect(auth.isTeacher()).toBe(true);
    expect(auth.isStudent()).toBe(false);
  });

  it('efface la session à la déconnexion', () => {
    localStorage.setItem('smartdept_token', 'x');
    auth.logout(false);
    expect(auth.token).toBeNull();
    expect(auth.user()).toBeNull();
  });
});
