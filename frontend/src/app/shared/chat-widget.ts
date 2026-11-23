import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { apiError } from '../core/labels';

interface Message { from: 'me' | 'bot'; text: string; source?: string; }

/** Assistant SmartDept : bulle flottante en bas à droite, disponible sur toutes les pages. */
@Component({
  selector: 'app-chat-widget',
  imports: [FormsModule],
  template: `
    @if (open()) {
      <section class="chat-panel" aria-label="Assistant SmartDept">
        <header>
          <div>
            <strong>Assistant SmartDept</strong>
            <small>{{ provider() }}</small>
          </div>
          <button class="btn-link close" (click)="open.set(false)" aria-label="Fermer">✕</button>
        </header>
        <div class="messages" #scroller>
          @for (m of messages(); track $index) {
            <div class="msg" [class.me]="m.from === 'me'">
              <div class="bubble pre">{{ m.text }}</div>
              @if (m.source) { <small class="muted">{{ m.source === 'ia' ? 'Réponse du modèle IA' : 'Réponse de l’assistant intégré' }}</small> }
            </div>
          }
          @if (thinking()) { <div class="msg"><div class="bubble typing"><span></span><span></span><span></span></div></div> }
        </div>
        @if (suggestions().length) {
          <div class="suggestions">
            @for (s of suggestions(); track s) { <button class="chip" (click)="send(s)">{{ s }}</button> }
          </div>
        }
        <form (ngSubmit)="send(draft)" class="composer">
          <input class="input" name="draft" [(ngModel)]="draft" placeholder="Posez votre question…" autocomplete="off" />
          <button class="btn btn-primary btn-sm" [disabled]="!draft.trim() || thinking()">Envoyer</button>
        </form>
      </section>
    }
    <button class="chat-fab" (click)="toggle()" [attr.aria-expanded]="open()" title="Assistant IA">
      {{ open() ? '✕' : '✦' }}
    </button>
  `,
  styles: [`
    .chat-fab { position: fixed; right: 22px; bottom: 22px; width: 56px; height: 56px; border-radius: 50%; border: 0; cursor: pointer; z-index: 40;
      color: #fff; font-size: 22px; background: linear-gradient(135deg, #6d3fc0, #2563eb); box-shadow: 0 10px 30px rgba(37, 99, 235, .35); }
    .chat-panel { position: fixed; right: 22px; bottom: 90px; width: min(390px, calc(100vw - 32px)); height: min(560px, calc(100vh - 120px)); z-index: 40;
      background: #fff; border: 1px solid var(--border); border-radius: 16px; box-shadow: 0 20px 60px rgba(15, 23, 42, .2); display: flex; flex-direction: column; overflow: hidden; }
    header { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; color: #fff; background: linear-gradient(135deg, #6d3fc0, #2563eb); }
    header small { display: block; opacity: .85; font-size: 12px; }
    .close { color: #fff; font-size: 16px; }
    .messages { flex: 1; overflow-y: auto; padding: 14px; display: flex; flex-direction: column; gap: 10px; background: var(--surface-2); }
    .msg { display: flex; flex-direction: column; align-items: flex-start; gap: 3px; max-width: 88%; }
    .msg.me { align-self: flex-end; align-items: flex-end; }
    .bubble { padding: 9px 12px; border-radius: 12px; background: #fff; border: 1px solid var(--border); font-size: 14px; }
    .msg.me .bubble { background: var(--primary-2); color: #fff; border-color: var(--primary-2); }
    .suggestions { padding: 8px 12px 0; border-top: 1px solid var(--border); }
    .suggestions .chip { border: 0; cursor: pointer; }
    .composer { display: flex; gap: 8px; padding: 10px 12px 12px; }
    .typing span { display: inline-block; width: 6px; height: 6px; margin: 0 2px; border-radius: 50%; background: var(--muted); animation: blink 1.2s infinite; }
    .typing span:nth-child(2) { animation-delay: .2s; } .typing span:nth-child(3) { animation-delay: .4s; }
    @keyframes blink { 0%, 80%, 100% { opacity: .25; } 40% { opacity: 1; } }
  `]
})
export class ChatWidget {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  readonly open = signal(false);
  readonly thinking = signal(false);
  readonly provider = signal('');
  readonly messages = signal<Message[]>([]);
  readonly suggestions = signal<string[]>([]);
  draft = '';

  toggle(): void {
    this.open.update(v => !v);
    if (this.open() && !this.messages().length) {
      const name = this.auth.user()?.firstName ?? '';
      this.messages.set([{ from: 'bot', text: `Bonjour ${name} ! Je connais vos sujets, candidatures, jalons et soutenances. Que voulez-vous savoir ?` }]);
      this.api.aiStatus().subscribe({
        next: s => this.provider.set(s.enabled ? `Modèle : ${s.model ?? s.provider}` : 'Mode local (sans clé IA)'),
        error: () => this.provider.set('')
      });
      this.send('', true);
    }
  }

  send(text: string, silent = false): void {
    const message = text.trim() || (silent ? 'aide' : '');
    if (!message) return;
    if (!silent) this.messages.update(list => [...list, { from: 'me', text: message }]);
    this.draft = '';
    this.thinking.set(true);
    this.scroll();
    this.api.chat(message).subscribe({
      next: r => {
        if (!silent) this.messages.update(list => [...list, { from: 'bot', text: r.answer, source: r.source }]);
        this.suggestions.set(r.suggestions ?? []);
        this.thinking.set(false);
        this.scroll();
      },
      error: err => {
        this.messages.update(list => [...list, { from: 'bot', text: apiError(err, 'L’assistant ne répond pas pour le moment.') }]);
        this.thinking.set(false);
      }
    });
  }

  private scroll(): void {
    setTimeout(() => {
      const el = this.scroller()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }
}
