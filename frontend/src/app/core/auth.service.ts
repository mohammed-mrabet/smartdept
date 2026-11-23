import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { LoginResponse, Role, User } from './models';

const TOKEN_KEY = 'smartdept_token';
const USER_KEY = 'smartdept_user';

/** Session : jeton JWT et utilisateur connecté (conservés dans le navigateur). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly user = signal<User | null>(this.readUser());
  readonly role = computed<Role | null>(() => this.user()?.role ?? null);
  readonly isManager = computed(() => this.role() === 'ADMIN' || this.role() === 'CHEF_DEPARTEMENT');
  readonly isTeacher = computed(() => this.role() === 'ENSEIGNANT' || this.role() === 'CHEF_DEPARTEMENT');
  readonly isStudent = computed(() => this.role() === 'ETUDIANT');

  get token(): string | null {
    try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
  }

  isLoggedIn(): boolean {
    return !!this.token && !!this.user();
  }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { email, password }).pipe(tap(res => {
      localStorage.setItem(TOKEN_KEY, res.token);
      this.setUser(res.user);
    }));
  }

  refreshMe(): Observable<User> {
    return this.http.get<User>('/api/auth/me').pipe(tap(u => this.setUser(u)));
  }

  setUser(u: User): void {
    localStorage.setItem(USER_KEY, JSON.stringify(u));
    this.user.set(u);
  }

  logout(redirect = true): void {
    try {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    } catch { /* stockage indisponible */ }
    this.user.set(null);
    if (redirect) this.router.navigate(['/connexion']);
  }

  hasRole(...roles: Role[]): boolean {
    const r = this.role();
    return !!r && roles.includes(r);
  }

  private readUser(): User | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) as User : null;
    } catch { return null; }
  }
}
