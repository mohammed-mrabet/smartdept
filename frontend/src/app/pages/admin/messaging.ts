import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PublicApi } from '../../core/public-api.service';
import { ContactMessage, MailDelivery, MailRoute, MailStatus } from '../../core/models';
import { apiError } from '../../core/labels';

const TOPIC: Record<string, string> = {
  PARTENARIAT: 'Partenariat', OFFRE_STAGE: 'Offre de stage', INSCRIPTION: 'Inscription', AUTRE: 'Autre'
};
const CHANNEL: Record<string, { label: string; tone: string }> = {
  SMTP: { label: 'SMTP réel', tone: 'teal' }, MAILPIT: { label: 'Mailpit (test)', tone: 'amber' }, NONE: { label: 'Aucun envoi', tone: 'gray' }
};

@Component({
  selector: 'app-messaging',
  imports: [FormsModule, DatePipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <span class="eyebrow">Administration</span>
          <h1>Messagerie et e-mails</h1>
          <p>Configuration de l’envoi, test vers une vraie adresse et messages reçus depuis la page d’accueil.</p>
        </div>
      </div>

      <div class="grid grid-2">
        <section class="card">
          <div class="card-head"><h2>Configuration de l’envoi</h2>
            @if (status(); as s) { <span class="badge" [class]="s.smtpConfigured ? 'teal' : 'amber'">{{ s.smtpConfigured ? 'SMTP réel configuré' : 'Mailpit uniquement' }}</span> }
          </div>
          @if (status(); as s) {
            <table class="table kv">
              <tr><th>Envoi</th><td>{{ s.enabled ? 'activé' : 'désactivé' }} · mode <b>{{ s.mode }}</b></td></tr>
              <tr><th>Serveur SMTP</th><td>{{ s.smtpConfigured ? s.smtpHost + ':' + s.smtpPort : '—' }}</td></tr>
              <tr><th>Compte</th><td>{{ s.smtpUser || '—' }}</td></tr>
              <tr><th>Expéditeur</th><td>{{ s.from }}</td></tr>
              <tr><th>Mailpit</th><td>{{ s.mailpitHost }}:{{ s.mailpitPort }} · <a [href]="s.mailpitUiUrl" target="_blank" rel="noopener">ouvrir ↗</a></td></tr>
              <tr><th>Domaines de test</th><td>@for (d of s.sandboxDomains; track d) { <span class="chip">{{ d }}</span> }</td></tr>
              <tr><th>Vérification MX</th><td>{{ s.checkMx ? 'oui : un domaine sans serveur de messagerie part dans Mailpit' : 'non' }}</td></tr>
            </table>
            @if (!s.smtpConfigured) {
              <div class="alert info mt">Pour envoyer vers Gmail, Outlook ou @ump.ac.ma, renseignez <code>SMTP_HOST</code>, <code>SMTP_USERNAME</code>
                et <code>SMTP_PASSWORD</code> dans le fichier <code>.env</code> (voir le README : Gmail avec mot de passe d’application, ou Brevo).</div>
            }
          } @else if (statusError()) { <div class="alert error">{{ statusError() }}</div> } @else { <div class="spinner"></div> }
        </section>

        <section class="card">
          <div class="card-head"><h2>Tester un envoi</h2></div>
          <p class="text-2">Une adresse réelle part par le SMTP ; une adresse de démonstration ou invalide est déposée dans Mailpit.</p>
          <form class="row mt" (ngSubmit)="test()">
            <input class="input grow" name="to" [(ngModel)]="to" (ngModelChange)="preview()" placeholder="prenom.nom@gmail.com" />
            <button class="btn btn-primary" [disabled]="!to.trim() || testing()">{{ testing() ? 'Envoi…' : 'Envoyer un test' }}</button>
          </form>
          @if (route(); as r) {
            <p class="mt small">Canal prévu : <span class="badge" [class]="channel[r.channel].tone">{{ channel[r.channel].label }}</span>
              <span class="muted"> {{ r.reason }}</span></p>
          }
          @if (result(); as d) {
            <div class="alert mt" [class]="d.success ? 'success' : 'error'">
              <b>{{ d.success ? 'E-mail envoyé' : 'Échec de l’envoi' }}</b> via {{ channel[d.channel].label }} — {{ d.reason }}
              @if (d.error) { <br /><small>{{ d.error }}</small> }
              @if (d.channel === 'MAILPIT' && status()?.mailpitUiUrl) { <br /><a [href]="status()!.mailpitUiUrl" target="_blank" rel="noopener">Voir dans Mailpit ↗</a> }
            </div>
          }
          @if (testError()) { <div class="alert error mt">{{ testError() }}</div> }
        </section>
      </div>

      <section class="card mt-lg">
        <div class="card-head">
          <h2>Messages reçus <span class="muted">({{ pending() }} à traiter)</span></h2>
          <label class="check"><input type="checkbox" [(ngModel)]="onlyPending" /> À traiter uniquement</label>
        </div>
        @if (messages() === null) { <div class="spinner"></div> }
        @else if (!visible().length) { <div class="empty"><div class="big">📭</div>Aucun message.</div> }
        @else {
          <div class="stack">
            @for (m of visible(); track m.id) {
              <article class="msg" [class.handled]="m.handled">
                <div class="row between">
                  <div class="row">
                    <strong>{{ m.name }}</strong>
                    @if (m.organisation) { <span class="muted">· {{ m.organisation }}</span> }
                    <span class="badge blue">{{ topic[m.topic] }}</span>
                  </div>
                  <small class="muted">{{ m.createdAt | date:'dd/MM/yyyy HH:mm' }}</small>
                </div>
                <p class="pre mt">{{ m.message }}</p>
                <div class="row mt">
                  <a class="btn btn-ghost btn-sm" [href]="'mailto:' + m.email + '?subject=' + encode('Re : ' + topic[m.topic])">Répondre à {{ m.email }}</a>
                  <button class="btn btn-sm" [class]="m.handled ? 'btn-ghost' : 'btn-success'" (click)="toggle(m)">
                    {{ m.handled ? 'Marquer à traiter' : '✓ Marquer comme traité' }}</button>
                </div>
              </article>
            }
          </div>
        }
      </section>
    </div>
  `,
  styles: [`
    .kv th { width: 150px; text-transform: none; letter-spacing: 0; font-size: 13px; }
    .kv td { padding: 9px 12px; }
    .grow { flex: 1; min-width: 200px; }
    code { background: var(--surface-2); padding: 1px 5px; border-radius: 4px; }
    .msg { border: 1px solid var(--border); border-radius: 10px; padding: 14px 16px; }
    .msg.handled { opacity: .6; }
  `]
})
export class Messaging {
  private readonly api = inject(PublicApi);

  readonly topic = TOPIC;
  readonly channel = CHANNEL;
  readonly status = signal<MailStatus | null>(null);
  readonly statusError = signal('');
  readonly route = signal<MailRoute | null>(null);
  readonly result = signal<MailDelivery | null>(null);
  readonly testing = signal(false);
  readonly testError = signal('');
  readonly messages = signal<ContactMessage[] | null>(null);
  readonly filter = signal(false);
  readonly pending = computed(() => (this.messages() ?? []).filter(m => !m.handled).length);
  readonly visible = computed(() => (this.messages() ?? []).filter(m => !this.filter() || !m.handled));
  to = '';
  private timer?: ReturnType<typeof setTimeout>;

  get onlyPending(): boolean { return this.filter(); }
  set onlyPending(v: boolean) { this.filter.set(v); }

  constructor() {
    this.api.mailStatus().subscribe({ next: s => this.status.set(s), error: e => this.statusError.set(apiError(e)) });
    this.api.contactMessages().subscribe({ next: m => this.messages.set(m), error: () => this.messages.set([]) });
  }

  encode(s: string): string { return encodeURIComponent(s); }

  preview(): void {
    clearTimeout(this.timer);
    const email = this.to.trim();
    if (!email) { this.route.set(null); return; }
    this.timer = setTimeout(() => this.api.mailRoute(email).subscribe({ next: r => this.route.set(r) }), 400);
  }

  test(): void {
    this.testing.set(true);
    this.result.set(null);
    this.testError.set('');
    this.api.testMail(this.to.trim()).subscribe({
      next: d => { this.result.set(d); this.testing.set(false); },
      error: e => { this.testError.set(apiError(e)); this.testing.set(false); }
    });
  }

  toggle(m: ContactMessage): void {
    this.api.markHandled(m.id, !m.handled).subscribe({
      next: updated => this.messages.update(list => (list ?? []).map(x => x.id === updated.id ? updated : x))
    });
  }
}
