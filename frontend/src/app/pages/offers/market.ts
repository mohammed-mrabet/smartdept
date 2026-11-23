import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { apiError, frDate, timeAgo } from '../../core/labels';
import { COUNTRY_FLAG, COUNTRY_LABEL, KIND_LABEL, Offer, OfferApi, OfferSource, OfferStats, SourceStatus, SyncSummary, TRACKING } from '../../core/offers';

/** Marché des stages (statistiques), état des API gratuites et offres publiées par le département. */
@Component({
  selector: 'app-offer-market',
  imports: [RouterLink],
  template: `
    <div class="page">
      <div class="page-head">
        <div><span class="eyebrow">Stages et emploi</span><h1>Sources et marché des stages</h1>
          <p>Ce que recherchent les entreprises, d’où viennent les offres, et l’état de chaque API gratuite.</p></div>
        <div class="row">
          <a class="btn btn-ghost" routerLink="/offres/publier">✚ Publier une offre</a>
          @if (auth.isManager()) { <button class="btn btn-primary" (click)="sync()" [disabled]="busy()">{{ busy() ? 'Synchronisation…' : '⟳ Synchroniser toutes les API' }}</button> }
        </div>
      </div>
      @if (error()) { <div class="alert error" style="margin-bottom:14px">{{ error() }}</div> }
      @if (summary(); as s) {
        <div class="alert success" style="margin-bottom:14px">Synchronisation terminée : {{ s.created }} nouvelle(s) offre(s), {{ s.updated }} mise(s) à jour, {{ s.alertsSent }} alerte(s) envoyée(s).
          @for (r of s.results; track r.source) { <br /><small>{{ r.success ? '✓' : '✗' }} {{ r.label }} : {{ r.fetched }} reçue(s) — {{ r.message }}</small> }</div>
      }

      <div class="tabs">
        <button [class.on]="tab() === 'market'" (click)="tab.set('market')">Marché</button>
        <button [class.on]="tab() === 'sources'" (click)="tab.set('sources')">API ({{ configuredCount() }}/{{ sources().length - 1 }} actives)</button>
        <button [class.on]="tab() === 'mine'" (click)="tab.set('mine')">Offres du département</button>
      </div>

      @if (tab() === 'market' && stats(); as st) {
        <div class="grid grid-4">
          <div class="card stat"><div class="icon bg-blue">💼</div><div><div class="value">{{ st.total }}</div><div class="label">offres actives</div></div></div>
          <div class="card stat"><div class="icon bg-teal">✚</div><div><div class="value">{{ st.newThisWeek }}</div><div class="label">nouvelles cette semaine</div></div></div>
          <div class="card stat"><div class="icon bg-violet">📋</div><div><div class="value">{{ trackedTotal() }}</div><div class="label">offres suivies par les étudiants</div></div></div>
          <div class="card stat"><div class="icon bg-amber">🎤</div><div><div class="value">{{ st.tracking['INTERVIEW'] ?? 0 }}</div><div class="label">entretiens en cours</div></div></div>
        </div>
        <div class="grid grid-2 mt">
          <div class="card"><h3>Par pays</h3>
            @for (e of entries(st.byCountry); track e[0]) {
              <div class="bar-row"><span>{{ flag[e[0]] }} {{ countryLabel[e[0]] }}</span><div class="bar"><i [style.width.%]="pct(e[1], st.byCountry)"></i></div><b>{{ e[1] }}</b></div>
            }
          </div>
          <div class="card"><h3>Par niveau</h3>
            @for (e of entries(st.byLevel); track e[0]) {
              <div class="bar-row"><span>{{ e[0] === 'UNKNOWN' ? 'Non précisé' : e[0].replace('BAC_', 'Bac+') }}</span><div class="bar violet"><i [style.width.%]="pct(e[1], st.byLevel)"></i></div><b>{{ e[1] }}</b></div>
            }
          </div>
          <div class="card"><h3>Compétences les plus demandées</h3>
            @for (s of st.topSkills; track s.name) {
              <div class="bar-row"><span>{{ s.name }}</span><div class="bar teal"><i [style.width.%]="100 * s.count / (st.topSkills[0]?.count || 1)"></i></div><b>{{ s.count }}</b></div>
            } @empty { <div class="empty">Pas encore de données.</div> }
          </div>
          <div class="card"><h3>Types et entreprises</h3>
            @for (e of entries(st.byKind); track e[0]) {
              <div class="bar-row"><span>{{ kindLabel[e[0]] }}</span><div class="bar amber"><i [style.width.%]="pct(e[1], st.byKind)"></i></div><b>{{ e[1] }}</b></div>
            }
            <h4 class="mt">Entreprises qui publient le plus</h4>
            <ol class="companies">@for (c of st.topCompanies; track c.name) { <li>{{ c.name }} <span class="muted">({{ c.count }})</span></li> }</ol>
          </div>
          <div class="card span2"><h3>Candidatures suivies par les étudiants</h3>
            <div class="funnel">
              @for (t of tracking; track t.code) {
                <div class="step"><strong>{{ st.tracking[t.code] ?? 0 }}</strong><small>{{ t.label }}</small></div>
              }
            </div>
          </div>
        </div>
      }

      @if (tab() === 'sources') {
        <div class="grid grid-3">
          @for (s of sources(); track s.source) {
            <div class="card src" [class.off]="!s.configured">
              <div class="row between">
                <h3>{{ s.label }}</h3>
                @if (s.source === 'INTERNAL') { <span class="badge teal">Toujours active</span> }
                @else if (s.configured) { <span class="badge teal">{{ s.keyless ? 'Sans clé' : 'Configurée' }}</span> }
                @else { <span class="badge gray">Clé manquante</span> }
              </div>
              <div class="flags">@for (c of s.countries; track c) { <span [title]="countryLabel[c]">{{ flag[c] }}</span> }</div>
              <p class="small text-2">{{ s.configHint }}</p>
              <div class="row between mt">
                <span><strong>{{ s.activeOffers }}</strong>&nbsp;<small class="muted">offres actives</small></span>
                @if (s.lastRun; as r) {
                  <small [class.err]="!r.success" [title]="r.message ?? ''">{{ r.success ? '✓' : '✗' }} {{ ago(r.startedAt) }} · +{{ r.created }}</small>
                } @else if (s.source !== 'INTERNAL') { <small class="muted">jamais synchronisée</small> }
              </div>
              @if (auth.isManager() && s.configured && s.source !== 'INTERNAL') {
                <button class="btn btn-ghost btn-sm mt" (click)="sync(s.source)" [disabled]="busy()">Synchroniser</button>
              }
              @if (s.minIntervalMinutes) { <small class="muted block">Au plus un appel toutes les {{ s.minIntervalMinutes }} min (conditions de l’API)</small> }
            </div>
          }
        </div>
        <div class="card mt help">
          <h3>Activer une API</h3>
          <p class="small text-2">Chaque clé gratuite se renseigne dans le fichier <code>.env</code> (Docker) ou comme variable d’environnement du backend,
            puis on redémarre le backend. Le détail est dans le README (section « Offres de stage »).</p>
        </div>
      }

      @if (tab() === 'mine') {
        <div class="card">
          <div class="card-head"><h3>Offres publiées par le département</h3><a class="btn btn-primary btn-sm" routerLink="/offres/publier">✚ Publier</a></div>
          <table class="table">
            <thead><tr><th>Offre</th><th>Pays</th><th>Niveau</th><th>Publiée</th><th>État</th><th></th></tr></thead>
            <tbody>
              @for (o of mine(); track o.id) {
                <tr>
                  <td><a routerLink="/offres" [queryParams]="{ offre: o.id }"><strong>{{ o.title }}</strong></a><br /><small class="muted">{{ o.company }}</small></td>
                  <td>{{ o.flag }} {{ o.countryLabel }}</td><td>{{ o.levelLabel }}</td><td>{{ date(o.publishedAt) }}</td>
                  <td>@if (isActive(o)) { <span class="badge teal">En ligne</span> } @else { <span class="badge gray">Clôturée</span> }</td>
                  <td class="nowrap"><a class="btn-link small" [routerLink]="['/offres', o.id, 'modifier']">Modifier</a>
                    <button class="btn-link small" (click)="closeOffer(o)">Clôturer</button></td>
                </tr>
              } @empty { <tr><td colspan="6" class="empty">Vous n’avez publié aucune offre.</td></tr> }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .tabs { display: inline-flex; border: 1px solid var(--border); border-radius: 10px; overflow: hidden; background: #fff; margin-bottom: 16px; }
    .tabs button { border: 0; background: none; padding: 9px 16px; font: 600 13.5px var(--font); cursor: pointer; color: var(--text-2); }
    .tabs button.on { background: var(--primary-soft); color: var(--primary-2); }
    .bar-row { display: grid; grid-template-columns: 130px 1fr 40px; gap: 10px; align-items: center; margin-top: 10px; font-size: 13.5px; }
    .bar { height: 10px; background: #eef2f8; border-radius: 99px; overflow: hidden; }
    .bar i { display: block; height: 100%; background: var(--primary-2); border-radius: 99px; }
    .bar.violet i { background: var(--violet); } .bar.teal i { background: var(--teal); } .bar.amber i { background: #e0a100; }
    .bar-row b { text-align: right; }
    .companies { margin: 8px 0 0; padding-left: 20px; font-size: 13.5px; }
    .span2 { grid-column: 1 / -1; }
    .funnel { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin-top: 12px; }
    .funnel .step { background: var(--surface-2); border: 1px solid var(--border); border-radius: 12px; padding: 14px; text-align: center; }
    .funnel strong { display: block; font-size: 24px; } .funnel small { color: var(--muted); }
    .src.off { background: #fbfcfe; }
    .flags { font-size: 20px; letter-spacing: 4px; margin: 6px 0; }
    .err { color: var(--red); }
    .block { display: block; margin-top: 6px; }
    code { background: var(--surface-2); padding: 1px 6px; border-radius: 5px; }
    @media (max-width: 700px) { .funnel { grid-template-columns: repeat(2, 1fr); } .bar-row { grid-template-columns: 100px 1fr 32px; } }
  `]
})
export class OfferMarket implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(OfferApi);
  readonly flag = COUNTRY_FLAG;
  readonly countryLabel = COUNTRY_LABEL;
  readonly kindLabel = KIND_LABEL;
  readonly tracking = TRACKING;
  readonly tab = signal<'market' | 'sources' | 'mine'>('market');
  readonly stats = signal<OfferStats | null>(null);
  readonly sources = signal<SourceStatus[]>([]);
  readonly mine = signal<Offer[]>([]);
  readonly summary = signal<SyncSummary | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly configuredCount = computed(() => this.sources().filter(s => s.configured && s.source !== 'INTERNAL').length);
  readonly trackedTotal = computed(() => Object.values(this.stats()?.tracking ?? {}).reduce((a, b) => a + b, 0));

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.stats().subscribe({ next: s => this.stats.set(s), error: err => this.error.set(apiError(err)) });
    this.api.sources().subscribe({ next: s => this.sources.set(s) });
    this.api.mine().subscribe({ next: m => this.mine.set(m) });
  }

  sync(source?: OfferSource): void {
    this.busy.set(true);
    this.api.sync(source).subscribe({
      next: s => { this.summary.set(s); this.busy.set(false); this.load(); },
      error: err => { this.error.set(apiError(err)); this.busy.set(false); }
    });
  }

  closeOffer(o: Offer): void {
    if (!confirm(`Clôturer l’offre « ${o.title} » ? Elle ne sera plus visible.`)) return;
    this.api.close(o.id).subscribe({ next: () => this.load() });
  }

  entries(m: Record<string, number>): [string, number][] { return Object.entries(m).sort((a, b) => b[1] - a[1]); }
  pct(v: number, m: Record<string, number>): number { const max = Math.max(...Object.values(m), 1); return 100 * v / max; }
  isActive(o: Offer): boolean { return !o.expiresAt || o.expiresAt >= new Date().toISOString().substring(0, 10); }
  ago(d: string): string { return timeAgo(d); }
  date(d?: string): string { return frDate(d); }
}
