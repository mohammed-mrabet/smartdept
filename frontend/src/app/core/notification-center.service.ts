import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { ApiService } from './api.service';
import { AppNotification } from './models';

/** Badge de la cloche : interroge l'API toutes les 30 secondes tant qu'un utilisateur est connecté. */
@Injectable({ providedIn: 'root' })
export class NotificationCenter implements OnDestroy {
  private readonly api = inject(ApiService);
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly unread = signal(0);
  readonly latest = signal<AppNotification[]>([]);

  start(): void {
    this.refresh();
    if (!this.timer) this.timer = setInterval(() => this.refresh(), 30000);
  }

  stop(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    this.unread.set(0);
    this.latest.set([]);
  }

  refresh(): void {
    this.api.unreadCount().subscribe({ next: r => this.unread.set(r.count), error: () => {} });
  }

  loadLatest(): void {
    this.api.notifications(false, 8).subscribe({ next: list => this.latest.set(list), error: () => {} });
  }

  ngOnDestroy(): void {
    this.stop();
  }
}
