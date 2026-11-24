import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PublicApi } from '../../core/public-api.service';
import { ThemeToggle } from '../../shared/theme-toggle';
import { AuthService } from '../../core/auth.service';
import { apiError } from '../../core/labels';

const DEMO = [
  { label: 'Chef de département', email: 'chef@smartdept.ma' },
  { label: 'Enseignante', email: 'n.alami@smartdept.ma' },
  { label: 'Étudiant', email: 'omar.tazi@etu.smartdept.ma' },
  { label: 'Administrateur', email: 'admin@smartdept.ma' }
];

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink, ThemeToggle],
  template: `
    <div class="wrap">
      <section class="hero">
        <a class="brand" routerLink="/"><span class="logo">S</span> SmartDept</a>
        <h1>La plateforme du département informatique, de la proposition du sujet à la soutenance.</h1>
        <ul>
          <li><strong>PFE de bout en bout</strong> : sujets, candidatures, affectation automatique, jalons, soutenances, conventions.</li>
          <li><strong>Notifications</strong> dans l’application et par e-mail à chaque étape.</li>
          <li><strong>Assistant IA</strong> : amélioration des sujets, choix de l’encadrant et du jury, chatbot.</li>
        </ul>
        <small><a routerLink="/" class="back">← Découvrir tous les services</a> · Faculté des Sciences d’Oujda · Master 2026</small>
      </section>

      <section class="panel">
        <div class="panel-tools"><app-theme-toggle /></div>
        <form class="card" (ngSubmit)="submit()" #f="ngForm">
          <h2>Connexion</h2>
          <p class="muted">Utilisez votre adresse institutionnelle.</p>
          @if (error()) {
            <div class="alert error mt">{{ error() }}
              @if (unverified()) {
                <br /><button type="button" class="btn-link" (click)="resend()" [disabled]="resending()">{{ resending() ? 'Envoi…' : 'Renvoyer le lien de confirmation' }}</button>
              }
            </div>
          }
          @if (info()) { <div class="alert info mt">{{ info() }}</div> }
          <div class="field mt">
            <label for="email">Adresse e-mail</label>
            <input id="email" name="email" type="email" [(ngModel)]="email" required autocomplete="username" />
          </div>
          <div class="field mt">
            <div class="row between"><label for="password">Mot de passe</label><a routerLink="/mot-de-passe-oublie" class="small">Mot de passe oublié ?</a></div>
            <input id="password" name="password" type="password" [(ngModel)]="password" required autocomplete="current-password" />
          </div>
          <button class="btn btn-primary mt full" [disabled]="loading() || !f.valid">{{ loading() ? 'Connexion…' : 'Se connecter' }}</button>

          <p class="signup">Pas encore de compte ? <a routerLink="/inscription">Créer un compte étudiant</a></p>

          <div class="demo">
            <div class="muted small">Comptes de démonstration (mot de passe <code>Demo@2026</code>)</div>
            <div class="row mt">
              @for (d of demo; track d.email) {
                <button type="button" class="btn btn-ghost btn-sm" (click)="fill(d.email)">{{ d.label }}</button>
              }
            </div>
          </div>
        </form>
      </section>
    </div>
  `,
  styles: [`
    .wrap { min-height: 100vh; display: grid; grid-template-columns: 1.1fr 1fr; }
    .hero { padding: 56px; color: #dfe7fb; background: radial-gradient(circle at 20% 10%, #2a4bb8 0, transparent 50%), linear-gradient(160deg, #0f1b3d, #1b2c66);
      display: flex; flex-direction: column; justify-content: center; gap: 26px; }
    .brand { text-decoration: none !important; display: flex; gap: 12px; align-items: center; font-size: 22px; font-weight: 800; color: #fff; }
    .logo { width: 44px; height: 44px; border-radius: 12px; display: grid; place-items: center; background: linear-gradient(135deg, #6d3fc0, #2563eb); }
    .hero h1 { color: #fff; font-size: 32px; max-width: 560px; }
    .hero ul { margin: 0; padding-left: 18px; display: grid; gap: 10px; max-width: 540px; }
    .hero small { color: #8ea0c8; }
    .panel { position: relative; display: grid; place-items: center; padding: 32px 18px; }
    .panel-tools { position: absolute; top: 16px; right: 16px; }
    form { width: min(420px, 100%); padding: 30px; }
    .full { width: 100%; }
    .signup { margin-top: 16px; text-align: center; font-size: 14px; color: var(--text-2); }
    .back { color: #c3cde6; }
    .demo { margin-top: 22px; padding-top: 18px; border-top: 1px solid var(--border); }
    code { background: var(--surface-2); padding: 1px 6px; border-radius: 5px; }
    @media (max-width: 900px) { .wrap { grid-template-columns: 1fr; } .hero { padding: 32px 22px; } .hero h1 { font-size: 24px; } }
  `]
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly publicApi = inject(PublicApi);

  readonly demo = DEMO;
  readonly loading = signal(false);
  readonly error = signal('');
  readonly unverified = signal(false);
  readonly resending = signal(false);
  readonly info = signal('');
  email = '';
  password = '';

  fill(email: string): void {
    this.email = email;
    this.password = 'Demo@2026';
  }

  submit(): void {
    this.loading.set(true);
    this.error.set('');
    this.info.set('');
    this.unverified.set(false);
    this.auth.login(this.email.trim(), this.password).subscribe({
      next: () => this.router.navigate(['/tableau-de-bord']),
      error: err => {
        this.error.set(err.status === 401 ? 'Adresse e-mail ou mot de passe incorrect.' : apiError(err));
        this.unverified.set(err.status === 403);
        this.loading.set(false);
      }
    });
  }

  resend(): void {
    this.resending.set(true);
    this.publicApi.resendVerification(this.email.trim()).subscribe({
      next: r => { this.info.set(r.message); this.error.set(''); this.resending.set(false); },
      error: e => { this.error.set(apiError(e)); this.resending.set(false); }
    });
  }
}
