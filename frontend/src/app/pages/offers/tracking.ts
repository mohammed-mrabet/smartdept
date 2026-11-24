import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { apiError, frDate } from '../../core/labels';
import { OfferApi, TRACKING, Tracking, TrackingStatus, companyColor, initials } from '../../core/offers';

/** Tableau de suivi des candidatures externes (glisser-déposer entre colonnes). */
@Component({
  selector: 'app-offer-tracking',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page wide-page">
      <div class="page-head">
        <div><span class="eyebrow">Stages et emploi</span><h1>Mon suivi de candidatures</h1>
          <p>Glissez une carte d’une colonne à l’autre. Un rappel de relance vous est envoyé 14 jours après une candidature sans réponse.</p></div>
        <a class="btn btn-primary" routerLink="/offres">Chercher des offres</a>
      </div>
      @if (error()) { <div class="alert error" style="margin-bottom:14px">{{ error() }}</div> }
      <div class="stats">
        @for (c of columns; track c.code) {
          <div class="stat-mini"><span class="dot" [class]="'dot ' + c.color"></span>{{ c.label }} <strong>{{ count(c.code) }}</strong></div>
        }
      </div>
      @if (loading()) { <div class="spinner"></div> } @else {
        <div class="board">
          @for (c of columns; track c.code) {
            <section class="col" [class.over]="over() === c.code" (dragover)="$event.preventDefault(); over.set(c.code)"
                     (dragleave)="over.set(null)" (drop)="drop(c.code)">
              <header><span class="dot" [class]="'dot ' + c.color"></span>{{ c.label }}<em>{{ count(c.code) }}</em></header>
              @for (t of byStatus()[c.code]; track t.id) {
                <article class="tcard" draggable="true" (dragstart)="dragged = t">
                  <div class="row">
                    <span class="avatar" [style.background]="color(t.offer.company)">{{ init(t.offer.company) }}</span>
                    <div class="grow"><strong>{{ t.offer.title }}</strong><small>{{ t.offer.flag }} {{ t.offer.company }}</small></div>
                  </div>
                  <div class="tags"><span class="badge">{{ t.offer.kindLabel }}</span><span class="badge violet">{{ t.offer.levelLabel }}</span></div>
                  @if (t.appliedAt) { <small class="muted">Postulé le {{ date(t.appliedAt) }}</small> }
                  @if (editing() === t.id) {
                    <textarea [(ngModel)]="notes" rows="3" placeholder="Contact, date d’entretien, documents envoyés…"></textarea>
                    <div class="row"><button class="btn btn-primary btn-sm" (click)="saveNotes(t)">Enregistrer</button>
                      <button class="btn-link small" (click)="editing.set(null)">Annuler</button></div>
                  } @else {
                    @if (t.notes) { <p class="notes">{{ t.notes }}</p> }
                    <div class="row actions">
                      <button class="btn-link small" (click)="edit(t)">Notes</button>
                      <select class="mini" [ngModel]="t.status" (ngModelChange)="move(t, $event)" aria-label="Déplacer">
                        @for (o of columns; track o.code) { <option [value]="o.code">{{ o.label }}</option> }
                      </select>
                      @if (t.offer.url) { <a class="small" [href]="t.offer.url" target="_blank" rel="noopener">Offre ↗</a> }
                      <button class="btn-link small danger" (click)="remove(t)" aria-label="Retirer">✕</button>
                    </div>
                  }
                </article>
              } @empty { <div class="drop-hint">Déposez une carte ici</div> }
            </section>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .wide-page { max-width: 1500px; }
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .stats { display: flex; flex-wrap: wrap; gap: 14px; margin-bottom: 14px; font-size: 13.5px; color: var(--text-2); }
    .stat-mini { display: flex; gap: 6px; align-items: center; }
    .dot { width: 9px; height: 9px; border-radius: 50%; display: inline-block; }
    .dot.blue { background: var(--primary-2); } .dot.violet { background: var(--violet); } .dot.amber { background: #e0a100; }
    .dot.teal { background: var(--teal); } .dot.red { background: var(--red); }
    .board { display: grid; grid-template-columns: repeat(5, minmax(220px, 1fr)); gap: 12px; overflow-x: auto; padding-bottom: 8px; }
    .col { background: var(--surface-sunken); border-radius: 14px; padding: 10px; min-height: 360px; border: 2px dashed transparent; transition: .15s; }
    .col.over { border-color: var(--primary-2); background: var(--highlight); }
    .col header { display: flex; gap: 8px; align-items: center; font-weight: 700; font-size: 13.5px; padding: 4px 4px 10px; }
    .col header em { margin-left: auto; font-style: normal; color: var(--muted); }
    .tcard { background: var(--surface); border: 1px solid var(--border); border-radius: 12px; padding: 12px; margin-bottom: 10px; cursor: grab; display: grid; gap: 8px; }
    .tcard:active { cursor: grabbing; }
    .tcard strong { font-size: 13.5px; display: block; line-height: 1.3; } .tcard small { color: var(--muted); font-size: 12px; }
    .avatar { width: 32px; height: 32px; border-radius: 9px; display: grid; place-items: center; color: #fff; font-weight: 800; font-size: 12px; flex: none; }
    .grow { flex: 1; min-width: 0; }
    .tags { display: flex; gap: 4px; flex-wrap: wrap; }
    .notes { font-size: 12.5px; color: var(--text-2); background: var(--surface-2); border-radius: 8px; padding: 6px 8px; white-space: pre-wrap; }
    .actions { gap: 10px; flex-wrap: wrap; }
    .mini { border: 1px solid var(--border); border-radius: 7px; font: 12px var(--font); padding: 2px 4px; }
    textarea { width: 100%; border: 1px solid var(--border); border-radius: 8px; padding: 6px 8px; font: 13px var(--font); }
    .danger { color: var(--red); margin-left: auto; }
    .drop-hint { text-align: center; color: var(--muted); font-size: 12.5px; padding: 28px 8px; border: 1px dashed var(--border-strong); border-radius: 10px; }
  `]
})
export class OfferTracking implements OnInit {
  private readonly api = inject(OfferApi);
  readonly columns = TRACKING;
  readonly items = signal<Tracking[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly over = signal<TrackingStatus | null>(null);
  readonly editing = signal<number | null>(null);
  readonly byStatus = computed(() => {
    const map = Object.fromEntries(TRACKING.map(t => [t.code, [] as Tracking[]])) as Record<TrackingStatus, Tracking[]>;
    this.items().forEach(t => map[t.status].push(t));
    return map;
  });
  dragged: Tracking | null = null;
  notes = '';

  ngOnInit(): void {
    this.api.tracking().subscribe({
      next: t => { this.items.set(t); this.loading.set(false); },
      error: err => { this.error.set(apiError(err)); this.loading.set(false); }
    });
  }

  count(s: TrackingStatus): number { return this.byStatus()[s].length; }

  drop(status: TrackingStatus): void {
    this.over.set(null);
    if (this.dragged && this.dragged.status !== status) this.move(this.dragged, status);
    this.dragged = null;
  }

  move(t: Tracking, status: TrackingStatus): void {
    this.api.track(t.offer.id, status, t.notes ?? null).subscribe({
      next: u => this.items.update(list => list.map(x => x.id === t.id ? u : x)),
      error: err => this.error.set(apiError(err))
    });
  }

  edit(t: Tracking): void { this.notes = t.notes ?? ''; this.editing.set(t.id); }

  saveNotes(t: Tracking): void {
    this.api.track(t.offer.id, t.status, this.notes).subscribe({
      next: u => { this.items.update(list => list.map(x => x.id === t.id ? u : x)); this.editing.set(null); },
      error: err => this.error.set(apiError(err))
    });
  }

  remove(t: Tracking): void {
    if (!confirm('Retirer cette offre de votre suivi ?')) return;
    this.api.untrack(t.offer.id).subscribe({ next: () => this.items.update(l => l.filter(x => x.id !== t.id)) });
  }

  color = companyColor;
  init = initials;
  date = (d: string) => frDate(d);
}
