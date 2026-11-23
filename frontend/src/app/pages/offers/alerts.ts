import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { apiError, frDate } from '../../core/labels';
import { Alert, AlertRequest, COUNTRIES, COUNTRY_FLAG, COUNTRY_LABEL, KINDS, LEVELS, OfferApi } from '../../core/offers';

@Component({
  selector: 'app-offer-alerts',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page">
      <div class="page-head">
        <div><span class="eyebrow">Stages et emploi</span><h1>Mes alertes</h1>
          <p>À chaque synchronisation (toutes les 6 heures), les nouvelles offres correspondantes vous sont envoyées dans l’application et par e-mail.</p></div>
        <button class="btn btn-primary" (click)="edit()">✚ Nouvelle alerte</button>
      </div>
      @if (error()) { <div class="alert error" style="margin-bottom:14px">{{ error() }}</div> }
      <div class="grid grid-2">
        @for (a of alerts(); track a.id) {
          <div class="card alert-card" [class.off]="!a.active">
            <div class="row between"><h3>🔔 {{ a.name }}</h3>
              <label class="switch"><input type="checkbox" [checked]="a.active" (change)="toggle(a)" /> {{ a.active ? 'Active' : 'En pause' }}</label></div>
            <div class="crit">
              <span class="chip">{{ a.keywords || 'tous mots-clés' }}</span>
              <span class="chip">{{ a.country ? flag[a.country] + ' ' + countryLabel[a.country] : 'tous pays' }}</span>
              <span class="chip">{{ a.kind ? kindLabel(a.kind) : 'tous types' }}</span>
              <span class="chip">{{ a.studyLevel ? 'Bac+' + a.studyLevel : 'tous niveaux' }}</span>
              @if (a.remoteOnly) { <span class="chip">télétravail</span> }
            </div>
            <p class="muted small mt">{{ a.matchesSent }} offre(s) envoyée(s)@if (a.lastNotifiedAt) { · dernière le {{ date(a.lastNotifiedAt) }} }</p>
            <div class="row mt">
              <a class="btn btn-ghost btn-sm" routerLink="/offres" [queryParams]="{ alerte: a.id }">Voir les offres</a>
              <button class="btn-link small" (click)="edit(a)">Modifier</button>
              <button class="btn-link small danger" (click)="remove(a)">Supprimer</button>
            </div>
          </div>
        } @empty {
          <div class="card empty"><div class="big">🔔</div>Aucune alerte. Créez-en une ici ou depuis la recherche d’offres.</div>
        }
      </div>

      @if (form(); as f) {
        <div class="modal-backdrop" (click)="form.set(null)">
          <form class="modal" (click)="$event.stopPropagation()" (ngSubmit)="save()">
            <h2>{{ editId ? 'Modifier l’alerte' : 'Nouvelle alerte' }}</h2>
            <div class="form-grid mt">
              <div class="field full"><label>Nom *</label><input name="name" [(ngModel)]="f.name" required /></div>
              <div class="field full"><label>Mots-clés (tous doivent apparaître)</label><input name="kw" [(ngModel)]="f.keywords" placeholder="spring angular" /></div>
              <div class="field"><label>Pays</label><select name="country" [(ngModel)]="f.country"><option [ngValue]="null">Tous</option>
                @for (c of countries; track c.code) { <option [ngValue]="c.code">{{ c.flag }} {{ c.label }}</option> }</select></div>
              <div class="field"><label>Type</label><select name="kind" [(ngModel)]="f.kind"><option [ngValue]="null">Tous</option>
                @for (k of kinds; track k.code) { <option [ngValue]="k.code">{{ k.label }}</option> }</select></div>
              <div class="field"><label>Niveau</label><select name="level" [(ngModel)]="f.studyLevel"><option [ngValue]="null">Tous</option>
                @for (l of levels; track l.years) { <option [ngValue]="l.years">{{ l.label }} — {{ l.diplomas }}</option> }</select></div>
              <div class="field"><label class="check" style="margin-top:28px"><input type="checkbox" name="remote" [(ngModel)]="f.remoteOnly" /> Télétravail uniquement</label></div>
            </div>
            <div class="actions"><button type="button" class="btn btn-ghost" (click)="form.set(null)">Annuler</button>
              <button class="btn btn-primary" [disabled]="!f.name.trim()">Enregistrer</button></div>
          </form>
        </div>
      }
    </div>
  `,
  styles: [`
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .alert-card.off { opacity: .65; }
    .crit { margin-top: 10px; }
    .switch { font-size: 13px; color: var(--text-2); display: flex; gap: 6px; align-items: center; }
    .danger { color: var(--red); }
  `]
})
export class OfferAlerts implements OnInit {
  private readonly api = inject(OfferApi);
  readonly countries = COUNTRIES;
  readonly countryLabel = COUNTRY_LABEL;
  readonly flag = COUNTRY_FLAG;
  readonly kinds = KINDS;
  readonly levels = LEVELS;
  readonly alerts = signal<Alert[]>([]);
  readonly error = signal('');
  readonly form = signal<AlertRequest | null>(null);
  editId: number | null = null;

  ngOnInit(): void { this.load(); }
  load(): void { this.api.alerts().subscribe({ next: a => this.alerts.set(a), error: err => this.error.set(apiError(err)) }); }

  edit(a?: Alert): void {
    this.editId = a?.id ?? null;
    this.form.set(a ? { name: a.name, keywords: a.keywords, country: a.country ?? null, kind: a.kind ?? null, studyLevel: a.studyLevel ?? null, remoteOnly: a.remoteOnly, active: a.active }
      : { name: '', keywords: '', country: null, kind: null, studyLevel: null, remoteOnly: false, active: true });
  }

  save(): void {
    const f = this.form()!;
    const call = this.editId ? this.api.updateAlert(this.editId, f) : this.api.createAlert(f);
    call.subscribe({ next: () => { this.form.set(null); this.load(); }, error: err => { this.form.set(null); this.error.set(apiError(err)); } });
  }

  toggle(a: Alert): void {
    this.api.updateAlert(a.id, { name: a.name, keywords: a.keywords, country: a.country ?? null, kind: a.kind ?? null,
      studyLevel: a.studyLevel ?? null, remoteOnly: a.remoteOnly, active: !a.active }).subscribe({ next: () => this.load() });
  }

  remove(a: Alert): void {
    if (!confirm(`Supprimer l’alerte « ${a.name} » ?`)) return;
    this.api.deleteAlert(a.id).subscribe({ next: () => this.load() });
  }

  kindLabel(k: string): string { return KINDS.find(x => x.code === k)?.label ?? k; }
  date = (d: string) => frDate(d);
}
