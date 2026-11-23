import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { AcademicApi, Exam, Overview, Session, mondayOf } from '../../core/academic';
import { Offer, OfferApi } from '../../core/offers';
import { frDate, hhmm, today } from '../../core/labels';

/** Bloc « Aujourd'hui » du tableau de bord : cours du jour, prochain examen, offres recommandées ou indicateurs de scolarité. */
@Component({
  selector: 'app-dashboard-extras',
  imports: [RouterLink],
  template: `
    <div class="grid grid-3 extras">
      <div class="card">
        <div class="card-head"><h2>🗓 Aujourd’hui</h2><a routerLink="/emploi-du-temps" class="small">Semaine</a></div>
        @for (s of todaySessions(); track s.id) {
          <div class="line" [class.cancel]="s.status === 'CANCELLED'">
            <span class="t">{{ time(s.startTime) }}</span>
            <span class="grow"><strong>{{ s.moduleCode }}</strong> {{ s.type }}<br /><small class="muted">{{ s.room.code }} · {{ auth.isStudent() ? s.teacher.fullName : s.filiere.code }}</small></span>
            @if (s.status === 'CANCELLED') { <span class="badge red">Annulée</span> }
          </div>
        } @empty { <div class="empty small">Pas de cours aujourd’hui.</div> }
      </div>
      <div class="card">
        <div class="card-head"><h2>📝 Prochain examen</h2><a routerLink="/examens" class="small">Calendrier</a></div>
        @if (nextExam(); as e) {
          <h3>{{ e.moduleCode }} — {{ e.moduleName }}</h3>
          <p class="text-2 mt-xs">{{ date(e.date) }}, {{ time(e.startTime) }}–{{ time(e.endTime) }} · salle(s) {{ rooms(e) }}</p>
        } @else { <div class="empty small">Aucun examen programmé.</div> }
        @if (overview(); as o) {
          <div class="kpis">
            <a routerLink="/suivi-etudiants"><strong>{{ o.studentsAtRisk }}</strong><small>à risque élevé</small></a>
            <a routerLink="/absences"><strong>{{ o.pendingJustifications }}</strong><small>justificatifs</small></a>
            <a routerLink="/emploi-du-temps"><strong>{{ o.sessionsThisWeek }}</strong><small>séances / semaine</small></a>
          </div>
        }
      </div>
      <div class="card offers">
        <div class="card-head"><h2>💼 {{ auth.isStudent() ? 'Offres pour vous' : 'Offres de stage' }}</h2><a routerLink="/offres" class="small">Toutes</a></div>
        @for (o of offers(); track o.id) {
          <a class="line" routerLink="/offres" [queryParams]="{ offre: o.id }">
            <span class="grow"><strong>{{ o.title }}</strong><br /><small class="muted">{{ o.flag }} {{ o.company }} · {{ o.levelLabel }}</small></span>
            @if (o.match != null) { <span class="badge teal">{{ o.match }} %</span> }
          </a>
        } @empty { <div class="empty small">Aucune offre pour le moment.</div> }
      </div>
    </div>
  `,
  styles: [`
    .extras { margin-bottom: 18px; }
    .line { display: flex; gap: 10px; align-items: center; padding: 9px 0; border-bottom: 1px solid #eef1f6; color: inherit; text-decoration: none !important; }
    .line:last-child { border-bottom: 0; }
    .line.cancel { opacity: .6; }
    .t { width: 44px; font-weight: 800; color: var(--primary-2); }
    .grow { flex: 1; min-width: 0; }
    .offers strong { font-size: 13.5px; }
    .mt-xs { margin-top: 6px; }
    .kpis { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 14px; }
    .kpis a { background: var(--surface-2); border: 1px solid var(--border); border-radius: 10px; padding: 8px; text-align: center; color: inherit; text-decoration: none; }
    .kpis strong { display: block; font-size: 20px; } .kpis small { color: var(--muted); font-size: 11.5px; }
  `]
})
export class DashboardExtras implements OnInit {
  readonly auth = inject(AuthService);
  private readonly academic = inject(AcademicApi);
  private readonly offerApi = inject(OfferApi);
  readonly todaySessions = signal<Session[]>([]);
  readonly nextExam = signal<Exam | null>(null);
  readonly offers = signal<Offer[]>([]);
  readonly overview = signal<Overview | null>(null);

  ngOnInit(): void {
    const t = today();
    if (!this.auth.isManager()) {
      this.academic.mySessions(mondayOf(t)).subscribe({ next: s => this.todaySessions.set(s.filter(x => x.date === t)), error: () => {} });
      this.academic.myExams().subscribe({ next: e => this.nextExam.set(e.find(x => x.date >= t) ?? null), error: () => {} });
    } else {
      this.academic.sessions({ week: mondayOf(t) }).subscribe({ next: s => this.todaySessions.set(s.filter(x => x.date === t).slice(0, 6)), error: () => {} });
      this.academic.exams({ from: t }).subscribe({ next: e => this.nextExam.set(e[0] ?? null), error: () => {} });
    }
    if (!this.auth.isStudent()) this.academic.overview().subscribe({ next: o => this.overview.set(o), error: () => {} });
    if (this.auth.isStudent()) this.offerApi.recommended(4).subscribe({ next: o => this.offers.set(o), error: () => {} });
    else this.offerApi.search({ size: 4 }).subscribe({ next: p => this.offers.set(p.items), error: () => {} });
  }

  rooms(e: Exam): string { return e.rooms.map(r => r.code).join(', '); }
  date = (d: string) => frDate(d, true);
  time = hhmm;
}
