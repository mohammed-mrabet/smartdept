import { ThemeToggle } from '../shared/theme-toggle';
import { Component, HostListener, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { ApiService } from '../core/api.service';
import { NotificationCenter } from '../core/notification-center.service';
import { AppNotification, Role } from '../core/models';
import { ROLE, timeAgo } from '../core/labels';
import { ChatWidget } from '../shared/chat-widget';

interface NavItem { label: string; icon: string; link: string; roles?: Role[]; exact?: boolean; }

const NAV: { title: string; items: NavItem[] }[] = [
  { title: 'Général', items: [
    { label: 'Tableau de bord', icon: '◧', link: '/tableau-de-bord' },
    { label: 'Notifications', icon: '🔔', link: '/notifications' }
  ] },
  { title: 'Stages et emploi', items: [
    { label: 'Offres de stage', icon: '💼', link: '/offres', exact: true },
    { label: 'Mon suivi', icon: '📋', link: '/offres/suivi', roles: ['ETUDIANT'] },
    { label: 'Mes alertes', icon: '🔔', link: '/offres/alertes', roles: ['ETUDIANT'] },
    { label: 'Sources et marché', icon: '📊', link: '/offres/marche', roles: ['ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN'] }
  ] },
  { title: 'Scolarité', items: [
    { label: 'Emploi du temps', icon: '🗓', link: '/emploi-du-temps', exact: true },
    { label: 'Examens', icon: '📝', link: '/examens' },
    { label: 'Mes notes', icon: '📈', link: '/notes', roles: ['ETUDIANT'] },
    { label: 'Saisie des notes', icon: '📈', link: '/notes', roles: ['ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN'] },
    { label: 'Absences', icon: '🙋', link: '/absences' },
    { label: 'Suivi des étudiants', icon: '🧭', link: '/suivi-etudiants', roles: ['ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN'] }
  ] },
  { title: 'Projets de fin d’études', items: [
    { label: 'Sujets', icon: '📚', link: '/pfe/sujets', exact: true },
    { label: 'Proposer un sujet', icon: '✚', link: '/pfe/sujets/nouveau', roles: ['ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN'] },
    { label: 'Mes candidatures', icon: '✉', link: '/pfe/candidatures', roles: ['ETUDIANT'] },
    { label: 'Mon projet', icon: '🎯', link: '/pfe/mon-projet', roles: ['ETUDIANT'] },
    { label: 'Projets encadrés', icon: '🎯', link: '/pfe/projets', roles: ['ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN'] },
    { label: 'Soutenances', icon: '🎓', link: '/pfe/soutenances' },
    { label: 'Conventions de stage', icon: '📄', link: '/pfe/conventions' }
  ] },
  { title: 'Administration', items: [
    { label: 'Utilisateurs', icon: '👥', link: '/admin/utilisateurs', roles: ['ADMIN', 'CHEF_DEPARTEMENT'] },
    { label: 'Salles et modules', icon: '🏫', link: '/admin/scolarite', roles: ['ADMIN', 'CHEF_DEPARTEMENT'] },
    { label: 'Messagerie et e-mails', icon: '✉', link: '/admin/messagerie', roles: ['ADMIN', 'CHEF_DEPARTEMENT'] }
  ] }
];

@Component({
  selector: 'app-shell',
  imports: [ThemeToggle, RouterOutlet, RouterLink, RouterLinkActive, ChatWidget],
  template: `
    <aside class="sidebar" [class.open]="menuOpen()">
      <a class="brand" routerLink="/tableau-de-bord">
        <span class="logo">S</span>
        <span><strong>SmartDept</strong><small>Département informatique</small></span>
      </a>
      <nav>
        @for (section of nav(); track section.title) {
          <div class="nav-title">{{ section.title }}</div>
          @for (item of section.items; track item.link) {
            <a [routerLink]="item.link" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: !!item.exact }">
              <span class="ico">{{ item.icon }}</span>{{ item.label }}
              @if (item.link === '/notifications' && center.unread() > 0) { <span class="count">{{ center.unread() }}</span> }
            </a>
          }
        }
      </nav>
      <div class="sidebar-foot muted small">FSO Oujda · Master 2026</div>
    </aside>
    @if (menuOpen()) { <div class="scrim" (click)="menuOpen.set(false)"></div> }

    <div class="shell-main">
      <header class="topbar">
        <button class="btn-link burger" (click)="menuOpen.set(true)" aria-label="Menu">☰</button>
        <div class="spacer"></div>

        <app-theme-toggle />
        <div class="bell-wrap">
          <button class="bell" (click)="toggleBell($event)" aria-label="Notifications" [attr.aria-expanded]="bellOpen()">
            🔔 @if (center.unread() > 0) { <span class="dot">{{ center.unread() > 99 ? '99+' : center.unread() }}</span> }
          </button>
          @if (bellOpen()) {
            <div class="dropdown" (click)="$event.stopPropagation()">
              <div class="row between dd-head">
                <strong>Notifications</strong>
                @if (center.unread() > 0) { <button class="btn-link small" (click)="readAll()">Tout marquer comme lu</button> }
              </div>
              @for (n of center.latest(); track n.id) {
                <button class="notif" [class.unread]="!n.read" (click)="openNotif(n)">
                  <span class="t">{{ n.title }}</span>
                  <span class="m">{{ n.message }}</span>
                  <span class="muted small">{{ ago(n.createdAt) }}</span>
                </button>
              } @empty {
                <div class="empty">Aucune notification</div>
              }
              <a class="all" routerLink="/notifications" (click)="bellOpen.set(false)">Voir toutes les notifications →</a>
            </div>
          }
        </div>

        <div class="user-wrap">
          <button class="user" (click)="toggleUser($event)">
            <span class="avatar">{{ initials() }}</span>
            <span class="who"><strong>{{ auth.user()?.fullName }}</strong><small>{{ roleLabel() }}</small></span>
          </button>
          @if (userOpen()) {
            <div class="dropdown small-dd" (click)="$event.stopPropagation()">
              <a routerLink="/profil" (click)="userOpen.set(false)">Mon profil</a>
              <button class="btn-link danger" (click)="logout()">Se déconnecter</button>
            </div>
          }
        </div>
      </header>
      <main><router-outlet /></main>
    </div>
    <app-chat-widget />
  `,
  styles: [`
    :host { display: block; min-height: 100vh; }
    .sidebar { position: fixed; inset: 0 auto 0 0; width: 256px; background: #0f1b3d; color: #c9d3ea; display: flex; flex-direction: column; z-index: 30; transition: transform .2s; }
    .brand { display: flex; gap: 12px; align-items: center; padding: 22px 20px; color: #fff; text-decoration: none !important; }
    .brand small { display: block; color: #8ea0c8; font-size: 12px; }
    .logo { width: 38px; height: 38px; border-radius: 11px; display: grid; place-items: center; font-weight: 800; font-size: 19px; background: linear-gradient(135deg, #6d3fc0, #2563eb); }
    nav { flex: 1; overflow-y: auto; padding: 4px 12px; }
    .nav-title { margin: 18px 10px 6px; font-size: 11px; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; color: #6f80a8; }
    nav a { display: flex; align-items: center; gap: 10px; padding: 9px 10px; border-radius: 9px; color: #c9d3ea; font-size: 14px; text-decoration: none !important; }
    nav a:hover { background: rgba(255, 255, 255, .06); color: #fff; }
    nav a.active { background: rgba(96, 140, 255, .18); color: #fff; font-weight: 650; }
    .ico { width: 20px; text-align: center; }
    .count { margin-left: auto; background: #ef4444; color: #fff; border-radius: 999px; padding: 0 7px; font-size: 11.5px; font-weight: 700; }
    .sidebar-foot { padding: 16px 22px; color: #6f80a8; }
    .shell-main { margin-left: 256px; min-height: 100vh; }
    .topbar { position: sticky; top: 0; z-index: 20; height: 64px; display: flex; align-items: center; gap: 14px; padding: 0 24px; background: var(--nav-bg); backdrop-filter: blur(8px); border-bottom: 1px solid var(--border); }
    .spacer { flex: 1; }
    .burger { display: none; font-size: 22px; color: var(--text); }
    .bell-wrap, .user-wrap { position: relative; }
    .bell { position: relative; width: 40px; height: 40px; border-radius: 10px; border: 1px solid var(--border); background: var(--surface); cursor: pointer; font-size: 17px; }
    .dot { position: absolute; top: -6px; right: -6px; min-width: 20px; height: 20px; padding: 0 5px; border-radius: 999px; background: #ef4444; color: #fff; font-size: 11px; font-weight: 700; display: grid; place-items: center; border: 2px solid #fff; }
    .user { display: flex; gap: 10px; align-items: center; border: 0; background: none; cursor: pointer; text-align: left; font: inherit; color: inherit; }
    .avatar { width: 38px; height: 38px; border-radius: 50%; display: grid; place-items: center; background: var(--primary-soft); color: var(--primary-2); font-weight: 800; }
    .who small { display: block; color: var(--muted); font-size: 12px; }
    .dropdown { position: absolute; right: 0; top: 48px; width: 360px; max-height: 480px; overflow-y: auto; background: var(--surface); border: 1px solid var(--border); border-radius: 12px; box-shadow: 0 16px 40px rgba(15, 23, 42, .16); }
    .dd-head { padding: 12px 14px; border-bottom: 1px solid var(--border); }
    .notif { display: flex; flex-direction: column; gap: 2px; width: 100%; text-align: left; padding: 11px 14px; border: 0; border-bottom: 1px solid var(--border-soft); background: var(--surface); cursor: pointer; font: inherit; color: inherit; }
    .notif:hover { background: var(--surface-2); }
    .notif.unread { background: var(--highlight); box-shadow: inset 3px 0 0 var(--primary-2); }
    .notif .t { font-weight: 650; font-size: 14px; }
    .notif .m { color: var(--text-2); font-size: 13px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .all { display: block; padding: 11px 14px; text-align: center; font-weight: 600; font-size: 13.5px; }
    .small-dd { width: 200px; padding: 6px; }
    .small-dd a, .small-dd button { display: block; width: 100%; text-align: left; padding: 9px 10px; border-radius: 8px; font-size: 14px; color: var(--text); }
    .small-dd a:hover, .small-dd button:hover { background: var(--surface-2); text-decoration: none; }
    .danger { color: var(--red) !important; }
    .scrim { position: fixed; inset: 0; background: rgba(15, 23, 42, .4); z-index: 25; }
    @media (max-width: 900px) {
      .sidebar { transform: translateX(-100%); }
      .sidebar.open { transform: none; }
      .shell-main { margin-left: 0; }
      .burger { display: block; }
      .who { display: none; }
      .topbar { padding: 0 14px; }
      .dropdown { width: min(360px, calc(100vw - 28px)); }
    }
  `]
})
export class Shell implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  readonly center = inject(NotificationCenter);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly menuOpen = signal(false);
  readonly bellOpen = signal(false);
  readonly userOpen = signal(false);

  readonly nav = computed(() => {
    const role = this.auth.role();
    return NAV.map(s => ({ ...s, items: s.items.filter(i => !i.roles || (role && i.roles.includes(role))) }))
      .filter(s => s.items.length);
  });
  readonly roleLabel = computed(() => { const r = this.auth.role(); return r ? ROLE[r] : ''; });
  readonly initials = computed(() => {
    const u = this.auth.user();
    return u ? (u.firstName[0] + u.lastName[0]).toUpperCase() : '?';
  });

  ngOnInit(): void {
    this.center.start();
    this.auth.refreshMe().subscribe({ error: () => {} });
    this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => {
      this.menuOpen.set(false);
      this.center.refresh();
    });
  }

  ngOnDestroy(): void {
    this.center.stop();
  }

  @HostListener('document:click')
  closeMenus(): void {
    this.bellOpen.set(false);
    this.userOpen.set(false);
  }

  toggleBell(e: Event): void {
    e.stopPropagation();
    this.userOpen.set(false);
    this.bellOpen.update(v => !v);
    if (this.bellOpen()) this.center.loadLatest();
  }

  toggleUser(e: Event): void {
    e.stopPropagation();
    this.bellOpen.set(false);
    this.userOpen.update(v => !v);
  }

  openNotif(n: AppNotification): void {
    this.bellOpen.set(false);
    if (!n.read) this.api.markRead(n.id).subscribe({ next: () => this.center.refresh() });
    if (n.link) this.router.navigateByUrl(n.link);
  }

  readAll(): void {
    this.api.markAllRead().subscribe(() => {
      this.center.refresh();
      this.center.loadLatest();
    });
  }

  logout(): void {
    this.center.stop();
    this.auth.logout();
  }

  ago = timeAgo;
}
