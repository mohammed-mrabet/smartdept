import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { NotificationCenter } from '../../core/notification-center.service';
import { AppNotification, Role } from '../../core/models';
import { ROLE, apiError, timeAgo } from '../../core/labels';

const EMAIL: Record<string, string> = { SENT: '✉ envoyé par e-mail', FAILED: '✉ échec de l’e-mail', PENDING: '✉ e-mail en cours', NOT_REQUESTED: '' };

@Component({
  selector: 'app-notifications',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <span class="eyebrow">Général</span>
          <h1>Notifications</h1>
          <p>Chaque événement important est notifié ici et, si vous l’avez activé dans votre profil, par e-mail.</p>
        </div>
        <div class="row">
          <button class="btn btn-ghost" (click)="readAll()" [disabled]="!center.unread()">Tout marquer comme lu</button>
          @if (auth.isManager()) { <button class="btn btn-primary" (click)="broadcastOpen.set(true)">📣 Annonce</button> }
        </div>
      </div>
      @if (error()) { <div class="alert error" style="margin-bottom:16px">{{ error() }}</div> }
      @if (success()) { <div class="alert success" style="margin-bottom:16px">{{ success() }}</div> }

      <div class="toolbar">
        <div class="seg">
          <button [class.on]="!unreadOnly" (click)="unreadOnly = false; load()">Toutes</button>
          <button [class.on]="unreadOnly" (click)="unreadOnly = true; load()">Non lues ({{ center.unread() }})</button>
        </div>
      </div>

      @if (loading()) { <div class="spinner"></div> } @else {
        <div class="card list">
          @for (n of items(); track n.id) {
            <div class="item" [class.unread]="!n.read">
              <span class="ico">{{ icon(n.type) }}</span>
              <div class="grow" (click)="open(n)">
                <div class="row between"><strong>{{ n.title }}</strong><small class="muted nowrap">{{ ago(n.createdAt) }}</small></div>
                <p class="text-2">{{ n.message }}</p>
                @if (email[n.emailStatus]) { <small class="muted">{{ email[n.emailStatus] }}</small> }
              </div>
              <div class="acts">
                @if (!n.read) { <button class="btn-link small" (click)="markRead(n)">Lu</button> }
                <button class="btn-link small muted" (click)="remove(n)" aria-label="Supprimer">✕</button>
              </div>
            </div>
          } @empty { <div class="empty"><div class="big">🔔</div>Aucune notification.</div> }
        </div>
      }

      @if (broadcastOpen()) {
        <div class="modal-backdrop" (click)="broadcastOpen.set(false)">
          <form class="modal" (click)="$event.stopPropagation()" (ngSubmit)="broadcast()">
            <h2>Envoyer une annonce</h2>
            <div class="field mt"><label>Destinataires</label>
              <select name="role" [(ngModel)]="bRole">
                <option value="">Tous les utilisateurs</option>
                @for (r of roles; track r) { <option [value]="r">{{ roleLabel[r] }}s</option> }
              </select></div>
            <div class="field mt"><label>Titre *</label><input name="title" [(ngModel)]="bTitle" required maxlength="150" /></div>
            <div class="field mt"><label>Message *</label><textarea name="message" [(ngModel)]="bMessage" required></textarea></div>
            <p class="muted small mt">L’annonce apparaît dans l’application et part par e-mail aux utilisateurs qui l’acceptent.</p>
            <div class="actions">
              <button type="button" class="btn btn-ghost" (click)="broadcastOpen.set(false)">Annuler</button>
              <button class="btn btn-primary" [disabled]="!bTitle.trim() || !bMessage.trim()">Envoyer</button>
            </div>
          </form>
        </div>
      }
    </div>
  `,
  styles: [`
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .seg { display: inline-flex; border: 1px solid var(--border); border-radius: 9px; overflow: hidden; background: #fff; }
    .seg button { border: 0; background: none; padding: 8px 14px; font: 600 13.5px var(--font); cursor: pointer; color: var(--text-2); }
    .seg button.on { background: var(--primary-soft); color: var(--primary-2); }
    .list { padding: 0; overflow: hidden; }
    .item { display: flex; gap: 14px; padding: 16px 18px; border-bottom: 1px solid #eef1f6; }
    .item:last-child { border-bottom: 0; }
    .item.unread { background: #f6f9ff; box-shadow: inset 3px 0 0 var(--primary-2); }
    .item .grow { flex: 1; min-width: 0; cursor: pointer; }
    .item p { margin-top: 3px; font-size: 14px; }
    .ico { width: 38px; height: 38px; border-radius: 10px; background: var(--surface-2); display: grid; place-items: center; flex: none; font-size: 17px; }
    .acts { display: flex; flex-direction: column; gap: 6px; align-items: flex-end; }
  `]
})
export class Notifications implements OnInit {
  readonly auth = inject(AuthService);
  readonly center = inject(NotificationCenter);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly email = EMAIL;
  readonly roleLabel = ROLE;
  readonly roles = Object.keys(ROLE) as Role[];
  readonly items = signal<AppNotification[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly success = signal('');
  readonly broadcastOpen = signal(false);
  unreadOnly = false;
  bRole: Role | '' = '';
  bTitle = '';
  bMessage = '';

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.api.notifications(this.unreadOnly, 100).subscribe({
      next: n => { this.items.set(n); this.loading.set(false); this.center.refresh(); },
      error: err => { this.error.set(apiError(err)); this.loading.set(false); }
    });
  }

  open(n: AppNotification): void {
    if (!n.read) this.markRead(n);
    if (n.link) this.router.navigateByUrl(n.link);
  }

  markRead(n: AppNotification): void {
    this.api.markRead(n.id).subscribe(u => { this.items.update(l => l.map(x => x.id === u.id ? u : x)); this.center.refresh(); });
  }

  readAll(): void {
    this.api.markAllRead().subscribe(() => this.load());
  }

  remove(n: AppNotification): void {
    this.api.deleteNotification(n.id).subscribe(() => { this.items.update(l => l.filter(x => x.id !== n.id)); this.center.refresh(); });
  }

  broadcast(): void {
    this.api.broadcast(this.bRole, this.bTitle.trim(), this.bMessage.trim()).subscribe({
      next: r => { this.broadcastOpen.set(false); this.bTitle = ''; this.bMessage = ''; this.success.set(`Annonce envoyée à ${r.recipients} utilisateur(s).`); this.load(); },
      error: err => { this.broadcastOpen.set(false); this.error.set(apiError(err)); }
    });
  }

  icon(type: string): string {
    if (type.startsWith('SUBJECT')) return '📚';
    if (type.startsWith('APPLICATION')) return '✉';
    if (type.startsWith('MILESTONE')) return '📝';
    if (type.startsWith('DEFENSE')) return '🎓';
    if (type.startsWith('CONVENTION')) return '📄';
    if (type.startsWith('PROJECT')) return '🎯';
    if (type === 'ANNOUNCEMENT') return '📣';
    return '🔔';
  }

  ago = timeAgo;
}
