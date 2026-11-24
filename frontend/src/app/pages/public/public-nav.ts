import { Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ThemeToggle } from '../../shared/theme-toggle';

/** Barre de navigation des pages publiques (accueil, inscription, mot de passe). */
@Component({
  selector: 'app-public-nav',
  imports: [RouterLink, ThemeToggle],
  template: `
    <header class="pnav" [class.dark]="dark()">
      <div class="pnav-in">
        <a routerLink="/" class="brand"><span class="logo">S</span><span>SmartDept</span></a>
        @if (anchors()) {
          <nav class="links">
            <a href="#services">Services</a>
            <a href="#offres">Offres de stage</a>
            <a href="#parcours">Comment ça marche</a>
            <a href="#faq">FAQ</a>
            <a href="#contact">Contact</a>
          </nav>
        }
        <div class="cta">
          <app-theme-toggle [onDark]="dark()" />
          @if (auth.isLoggedIn()) {
            <a routerLink="/tableau-de-bord" class="btn btn-primary btn-sm">Mon espace →</a>
          } @else {
            <a routerLink="/connexion" class="btn btn-sm ghost">Connexion</a>
            <a routerLink="/inscription" class="btn btn-primary btn-sm">Créer un compte</a>
          }
        </div>
      </div>
    </header>
  `,
  styles: [`
    .pnav { position: sticky; top: 0; z-index: 40; background: var(--nav-bg); backdrop-filter: blur(10px); border-bottom: 1px solid var(--border); }
    .pnav.dark { background: rgba(11, 21, 48, .82); border-bottom-color: rgba(255,255,255,.08); }
    .pnav-in { max-width: 1200px; margin: 0 auto; padding: 12px 20px; display: flex; align-items: center; gap: 24px; }
    .brand { display: flex; align-items: center; gap: 10px; font-weight: 800; font-size: 18px; color: var(--text); text-decoration: none !important; }
    .dark .brand { color: #fff; }
    .logo { width: 34px; height: 34px; border-radius: 10px; display: grid; place-items: center; color: #fff; background: linear-gradient(135deg, #6d3fc0, #2563eb); }
    .links { display: flex; gap: 22px; flex: 1; justify-content: center; }
    .links a { color: var(--text-2); font-size: 14px; font-weight: 600; }
    .dark .links a { color: #c3cde6; }
    .links a:hover { color: var(--primary-2); text-decoration: none; }
    .dark .links a:hover { color: #fff; }
    .cta { display: flex; gap: 8px; margin-left: auto; align-items: center; }
    .ghost { background: transparent; color: var(--text); border-color: var(--border); }
    .dark .ghost { color: #fff; border-color: rgba(255,255,255,.25); }
    @media (max-width: 900px) { .links { display: none; } }
    @media (max-width: 420px) { .brand span:last-child { display: none; } }
  `]
})
export class PublicNav {
  readonly auth = inject(AuthService);
  readonly anchors = input(false);
  readonly dark = input(false);
}
