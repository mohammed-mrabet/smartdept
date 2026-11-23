import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AcademicApi, PlanApplyResult, PlanPreview, addDays, mondayOf } from '../../core/academic';
import { Filiere } from '../../core/models';
import { apiError, frDate, today } from '../../core/labels';
import { GridItem, WeekGrid } from '../../shared/week-grid';

/** Planification automatique : proposition d'une semaine type, vérifiée puis enregistrée sur plusieurs semaines. */
@Component({
  selector: 'app-planner',
  imports: [FormsModule, RouterLink, WeekGrid],
  template: `
    <div class="page wide">
      <a routerLink="/emploi-du-temps" class="small">← Emploi du temps</a>
      <div class="page-head mt"><div><span class="eyebrow">Scolarité · IA</span><h1>Planification automatique</h1>
        <p>L’algorithme place chaque CM, TD et TP en respectant les disponibilités des enseignants, des salles et des autres filières,
          puis répartit les cours sur la semaine (CM le matin, TP en laboratoire, peu de fins d’après-midi).</p></div></div>
      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }

      <div class="card params">
        <div class="field"><label>Filière</label><select [(ngModel)]="filiereId">@for (f of filieres(); track f.id) { <option [ngValue]="f.id">{{ f.code }} — {{ f.name }}</option> }</select></div>
        <div class="field"><label>Semaine du</label><input type="date" [(ngModel)]="weekStart" /></div>
        <div class="field"><label>Semestre</label><select [(ngModel)]="semester"><option value="">Tous</option>@for (s of semesters; track s) { <option [value]="s">{{ s }}</option> }</select></div>
        <button class="btn btn-ai" (click)="preview()" [disabled]="busy() || !filiereId">✦ Proposer un emploi du temps</button>
      </div>

      @if (plan(); as p) {
        <div class="grid grid-4 mt">
          <div class="card stat"><div class="icon bg-blue">📚</div><div><div class="value">{{ p.slots.length }}/{{ p.required }}</div><div class="label">séances placées</div></div></div>
          <div class="card stat"><div class="icon bg-teal">👥</div><div><div class="value">{{ p.students }}</div><div class="label">étudiants</div></div></div>
          <div class="card stat"><div class="icon bg-violet">🗓</div><div><div class="value">{{ busiestDay() }}</div><div class="label">séances max. par jour</div></div></div>
          <div class="card stat"><div class="icon" [class]="'icon ' + (p.unplaced.length ? 'bg-red' : 'bg-teal')">{{ p.unplaced.length ? '⚠' : '✓' }}</div><div><div class="value">{{ p.unplaced.length }}</div><div class="label">non placée(s)</div></div></div>
        </div>
        @if (p.unplaced.length) { <div class="alert warn mt"><strong>À traiter à la main :</strong><ul>@for (u of p.unplaced; track $index) { <li>{{ u }}</li> }</ul></div> }
        <div class="mt"><app-week-grid [weekStart]="p.weekStart" [items]="items()" /></div>
        <div class="card mt apply">
          <div class="field"><label>Répéter sur</label><select [(ngModel)]="weeks">@for (n of [1, 2, 4, 8, 12, 14]; track n) { <option [ngValue]="n">{{ n }} semaine(s)</option> }</select></div>
          <p class="text-2 small">Les séances seront créées à partir du {{ date(p.weekStart) }} ; un conflit apparu entre-temps est signalé et la séance ignorée. Les étudiants sont prévenus.</p>
          <button class="btn btn-primary" (click)="apply()" [disabled]="busy() || !p.slots.length">Enregistrer l’emploi du temps</button>
        </div>
      }
      @if (result(); as r) {
        <div class="alert success mt">{{ r.created }} séance(s) créée(s), {{ r.skipped }} ignorée(s).
          <a routerLink="/emploi-du-temps" [queryParams]="{ semaine: plan()?.weekStart }">Voir l’emploi du temps →</a>
          @if (r.conflicts.length) { <ul>@for (c of r.conflicts; track $index) { <li class="small">{{ c }}</li> }</ul> }</div>
      }
    </div>
  `,
  styles: [`
    .wide { max-width: 1400px; }
    .eyebrow { display: block; margin-bottom: 6px; color: var(--violet); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .params, .apply { display: flex; flex-wrap: wrap; gap: 14px; align-items: flex-end; }
    .params .field { min-width: 180px; } .apply p { flex: 1; min-width: 240px; margin: 0; }
  `]
})
export class Planner implements OnInit {
  private readonly api = inject(AcademicApi);
  private readonly core = inject(ApiService);
  readonly filieres = signal<Filiere[]>([]);
  readonly plan = signal<PlanPreview | null>(null);
  readonly result = signal<PlanApplyResult | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly semesters = ['S1', 'S2', 'S3', 'S4', 'S5', 'S6'];
  readonly items = computed<GridItem[]>(() => (this.plan()?.slots ?? []).map((s, i) => ({
    key: 'p' + i, date: s.date, startTime: s.startTime, endTime: s.endTime, type: s.type, title: s.moduleCode, line2: s.roomCode, line3: s.teacherName
  })));
  readonly busiestDay = computed(() => Math.max(0, ...Object.values(this.plan()?.sessionsPerDay ?? {})));
  filiereId: number | null = null;
  weekStart = addDays(mondayOf(today()), 7);
  semester = '';
  weeks = 4;

  ngOnInit(): void { this.core.filieres().subscribe(f => { this.filieres.set(f); this.filiereId = f[0]?.id ?? null; }); }

  preview(): void {
    this.busy.set(true);
    this.result.set(null);
    this.api.previewTimetable(this.filiereId!, this.weekStart, this.semester).subscribe({
      next: p => { this.plan.set(p); this.busy.set(false); this.error.set(''); },
      error: err => { this.error.set(apiError(err)); this.busy.set(false); }
    });
  }

  apply(): void {
    const p = this.plan()!;
    if (!confirm(`Créer ${p.slots.length * this.weeks} séance(s) sur ${this.weeks} semaine(s) ?`)) return;
    this.busy.set(true);
    this.api.applyTimetable(p.filiereId, this.weeks, p.slots).subscribe({
      next: r => { this.result.set(r); this.busy.set(false); },
      error: err => { this.error.set(apiError(err)); this.busy.set(false); }
    });
  }

  date = (d: string) => frDate(d);
}
