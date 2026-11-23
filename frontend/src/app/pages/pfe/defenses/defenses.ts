import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { AuthService } from '../../../core/auth.service';
import { Defense, DefenseRequest, JurySuggestion, Project, UserSummary } from '../../../core/models';
import { DEFENSE_STATUS, apiError, frDate, hhmm, today } from '../../../core/labels';
import { Badge } from '../../../shared/badge';

interface DayGroup { date: string; items: Defense[]; }

@Component({
  selector: 'app-defenses',
  imports: [FormsModule, RouterLink, Badge],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <span class="eyebrow">Projets de fin d’études</span>
          <h1>Soutenances</h1>
          <p>{{ auth.isManager() ? 'Planification, composition des jurys et saisie des notes.' : 'Le calendrier des soutenances qui vous concernent.' }}</p>
        </div>
        @if (auth.isManager()) {
          <button class="btn btn-primary" (click)="openSchedule()" [disabled]="!readyProjects().length">✚ Programmer une soutenance</button>
        }
      </div>

      @if (error()) { <div class="alert error" style="margin-bottom:16px">{{ error() }}</div> }
      @if (success()) { <div class="alert success" style="margin-bottom:16px">{{ success() }}</div> }
      @if (auth.isManager() && readyProjects().length) {
        <div class="alert info" style="margin-bottom:16px">{{ readyProjects().length }} projet(s) prêt(s) attendent une date de soutenance.</div>
      }

      <div class="toolbar">
        <div class="seg">
          <button [class.on]="period === 'upcoming'" (click)="period = 'upcoming'; load()">À venir</button>
          <button [class.on]="period === 'past'" (click)="period = 'past'; load()">Passées</button>
          <button [class.on]="period === 'all'" (click)="period = 'all'; load()">Toutes</button>
        </div>
        @if (!auth.isStudent()) {
          <label class="check"><input type="checkbox" [(ngModel)]="mine" (change)="load()" /> Seulement mes jurys et mes étudiants</label>
        }
      </div>

      @if (loading()) { <div class="spinner"></div> } @else {
        @for (g of groups(); track g.date) {
          <h3 class="day">{{ longDate(g.date) }}</h3>
          <div class="stack">
            @for (d of g.items; track d.id) {
              <div class="card defense" [id]="'soutenance-' + d.id" [class.cancelled]="d.status === 'CANCELLED'">
                <div class="time"><strong>{{ time(d.startTime) }}</strong><small>{{ time(d.endTime) }}</small><span class="room">{{ d.room }}</span></div>
                <div class="body">
                  <div class="row between">
                    <a [routerLink]="['/pfe/projets', d.projectId]"><strong>{{ d.subjectTitle }}</strong></a>
                    <app-badge [label]="status[d.status]" />
                  </div>
                  <p class="text-2 small">{{ names(d.students) }} · encadrant {{ d.supervisor.fullName }}</p>
                  <p class="small mt-xs"><span class="muted">Jury :</span>
                    @for (j of d.jury; track j.id; let last = $last) { {{ j.fullName }}@if (j.id === d.president.id) { <span class="badge violet">président</span> }@if (!last) {, } }</p>
                  @if (d.grade != null) {
                    <p class="mt-xs"><strong>{{ d.grade }} / 20</strong> · mention {{ d.mention }}@if (d.juryComment) { <span class="muted small"> — {{ d.juryComment }}</span> }</p>
                  }
                  <div class="row mt-xs">
                    @if (canGrade(d)) { <button class="btn btn-primary btn-sm" (click)="openGrade(d)">Saisir la note</button> }
                    @if (auth.isManager() && d.status === 'SCHEDULED') {
                      <button class="btn btn-ghost btn-sm" (click)="openEdit(d)">Modifier</button>
                      <button class="btn btn-danger btn-sm" (click)="cancel(d)">Annuler</button>
                    }
                  </div>
                </div>
              </div>
            }
          </div>
        } @empty {
          <div class="card empty"><div class="big">🎓</div>Aucune soutenance sur cette période.</div>
        }
      }

      <!-- programmer / modifier -->
      @if (editing()) {
        <div class="modal-backdrop" (click)="editing.set(false)">
          <form class="modal wide" (click)="$event.stopPropagation()" (ngSubmit)="save()">
            <h2>{{ editId ? 'Modifier la soutenance' : 'Programmer une soutenance' }}</h2>
            @if (formError()) { <div class="alert error mt">{{ formError() }}</div> }
            <div class="form-grid mt">
              <div class="field full"><label>Projet *</label>
                <select name="projectId" [(ngModel)]="req.projectId" [disabled]="!!editId" (change)="loadSuggestions()" required>
                  @for (p of projectChoices(); track p.id) { <option [ngValue]="p.id">{{ p.subjectTitle }} — {{ names(p.students) }}</option> }
                </select></div>
              <div class="field"><label>Date *</label><input type="date" name="date" [min]="todayIso" [(ngModel)]="req.defenseDate" (change)="loadSuggestions()" required /></div>
              <div class="field"><label>Salle *</label><input name="room" [(ngModel)]="req.room" placeholder="Amphi B" required /></div>
              <div class="field"><label>Début *</label><input type="time" name="start" [(ngModel)]="req.startTime" (change)="loadSuggestions()" required /></div>
              <div class="field"><label>Fin *</label><input type="time" name="end" [(ngModel)]="req.endTime" (change)="loadSuggestions()" required /></div>
            </div>

            <div class="card-head mt-lg"><h3>Jury <small class="muted">(au moins 2 enseignants)</small></h3><span class="badge violet">✦ classé par l’IA</span></div>
            @if (suggesting()) { <div class="spinner"></div> }
            <div class="jury">
              @for (s of suggestions(); track s.teacher.id) {
                <label class="jury-row" [class.busy]="!s.available">
                  <input type="checkbox" [checked]="req.juryIds.includes(s.teacher.id)" (change)="toggleJury(s.teacher.id)" />
                  <span class="grow"><strong>{{ s.teacher.fullName }}</strong>
                    @if (!s.available) { <span class="badge red">indisponible</span> }
                    <br /><small class="muted">{{ s.reason }}</small></span>
                  <span class="badge blue">{{ (s.score * 100).toFixed(0) }}</span>
                </label>
              }
            </div>
            <div class="field mt"><label>Président du jury *</label>
              <select name="president" [(ngModel)]="req.presidentId" required>
                <option [ngValue]="null" disabled>Choisir parmi le jury…</option>
                @for (j of juryMembers(); track j.id) { <option [ngValue]="j.id">{{ j.fullName }}</option> }
              </select>
              <span class="hint">L’encadrant ne peut pas présider le jury de son étudiant.</span></div>
            <div class="actions">
              <button type="button" class="btn btn-ghost" (click)="editing.set(false)">Fermer</button>
              <button class="btn btn-primary" [disabled]="busy() || req.juryIds.length < 2 || !req.presidentId">Enregistrer et notifier</button>
            </div>
          </form>
        </div>
      }

      <!-- note -->
      @if (grading(); as d) {
        <div class="modal-backdrop" (click)="grading.set(null)">
          <form class="modal" (click)="$event.stopPropagation()" (ngSubmit)="saveGrade(d)">
            <h2>Note de soutenance</h2>
            <p class="text-2 mt">{{ d.subjectTitle }} — {{ names(d.students) }}</p>
            <div class="field mt"><label>Note sur 20 *</label><input type="number" name="grade" min="0" max="20" step="0.25" [(ngModel)]="grade" required /></div>
            <div class="field mt"><label>Appréciation du jury</label><textarea name="comment" [(ngModel)]="gradeComment"></textarea></div>
            <div class="actions">
              <button type="button" class="btn btn-ghost" (click)="grading.set(null)">Annuler</button>
              <button class="btn btn-primary" [disabled]="grade == null || grade < 0 || grade > 20">Enregistrer</button>
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
    .day { margin: 22px 0 10px; text-transform: capitalize; color: var(--text-2); }
    .defense { display: flex; gap: 18px; scroll-margin-top: 90px; }
    .defense:target { box-shadow: 0 0 0 3px #c7d7ff; }
    .defense.cancelled { opacity: .6; }
    .time { width: 74px; flex: none; text-align: center; border-right: 1px solid var(--border); padding-right: 14px; display: flex; flex-direction: column; }
    .time strong { font-size: 19px; } .time small { color: var(--muted); }
    .room { margin-top: 8px; font-size: 12px; font-weight: 700; color: var(--violet); }
    .body { flex: 1; min-width: 0; }
    .mt-xs { margin-top: 8px; }
    .wide { width: min(760px, 100%); }
    .jury { border: 1px solid var(--border); border-radius: 10px; max-height: 260px; overflow-y: auto; }
    .jury-row { display: flex; gap: 12px; align-items: center; padding: 10px 12px; border-bottom: 1px solid #eef1f6; cursor: pointer; }
    .jury-row:last-child { border-bottom: 0; }
    .jury-row.busy { background: #fff8f7; }
    .grow { flex: 1; }
  `]
})
export class Defenses implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  readonly status = DEFENSE_STATUS;
  readonly todayIso = today();
  readonly defenses = signal<Defense[]>([]);
  readonly projects = signal<Project[]>([]);
  readonly suggestions = signal<JurySuggestion[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly suggesting = signal(false);
  readonly error = signal('');
  readonly formError = signal('');
  readonly success = signal('');
  readonly editing = signal(false);
  readonly grading = signal<Defense | null>(null);
  readonly juryVersion = signal(0);

  period: 'upcoming' | 'past' | 'all' = 'upcoming';
  mine = false;
  editId: number | null = null;
  req: DefenseRequest = this.emptyRequest();
  grade: number | null = null;
  gradeComment = '';

  readonly readyProjects = computed(() => this.projects().filter(p => p.status === 'READY_FOR_DEFENSE' && (!p.defense || p.defense.status === 'CANCELLED')));
  readonly groups = computed<DayGroup[]>(() => {
    const map = new Map<string, Defense[]>();
    for (const d of this.defenses()) map.set(d.defenseDate, [...(map.get(d.defenseDate) ?? []), d]);
    const groups = [...map.entries()].map(([date, items]) => ({ date, items }));
    return this.period === 'past' ? groups.reverse() : groups;
  });
  readonly juryMembers = computed<UserSummary[]>(() => {
    this.juryVersion();
    return this.suggestions().filter(s => this.req.juryIds.includes(s.teacher.id)).map(s => s.teacher);
  });

  ngOnInit(): void {
    this.mine = this.auth.role() === 'ENSEIGNANT';
    if (this.route.snapshot.fragment) this.period = 'all';
    this.load();
    if (this.auth.isManager()) this.api.projects().subscribe({ next: p => this.projects.set(p), error: () => {} });
  }

  load(): void {
    this.loading.set(true);
    const f = this.period === 'upcoming' ? { from: today() } : this.period === 'past' ? { to: today() } : {};
    this.api.defenses({ ...f, mine: this.mine || undefined }).subscribe({
      next: d => {
        this.defenses.set(d);
        this.loading.set(false);
        const frag = this.route.snapshot.fragment;
        if (frag) setTimeout(() => document.getElementById(frag)?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 50);
      },
      error: err => { this.error.set(apiError(err)); this.loading.set(false); }
    });
  }

  canGrade(d: Defense): boolean {
    return d.status === 'SCHEDULED' && d.defenseDate <= today()
      && (this.auth.isManager() || d.president.id === this.auth.user()?.id);
  }

  private emptyRequest(): DefenseRequest {
    return { projectId: 0, defenseDate: '', startTime: '09:00', endTime: '10:00', room: '', juryIds: [], presidentId: null };
  }

  projectChoices(): Project[] {
    if (!this.editId) return this.readyProjects();
    return this.projects().filter(p => p.id === this.req.projectId);
  }

  openSchedule(): void {
    this.editId = null;
    this.req = this.emptyRequest();
    this.req.projectId = this.readyProjects()[0]?.id ?? 0;
    this.formError.set('');
    this.suggestions.set([]);
    this.editing.set(true);
    this.loadSuggestions();
  }

  openEdit(d: Defense): void {
    this.editId = d.id;
    this.req = {
      projectId: d.projectId, defenseDate: d.defenseDate, startTime: hhmm(d.startTime), endTime: hhmm(d.endTime), room: d.room,
      juryIds: d.jury.map(j => j.id), presidentId: d.president.id
    };
    this.formError.set('');
    this.editing.set(true);
    this.loadSuggestions();
  }

  loadSuggestions(): void {
    if (!this.req.projectId) return;
    this.suggesting.set(true);
    this.api.jurySuggestions(this.req.projectId, this.req.defenseDate || undefined, this.req.startTime || undefined, this.req.endTime || undefined).subscribe({
      next: s => { this.suggestions.set(s); this.suggesting.set(false); this.juryVersion.update(v => v + 1); },
      error: err => { this.formError.set(apiError(err)); this.suggesting.set(false); }
    });
  }

  toggleJury(id: number): void {
    this.req.juryIds = this.req.juryIds.includes(id) ? this.req.juryIds.filter(x => x !== id) : [...this.req.juryIds, id];
    if (this.req.presidentId && !this.req.juryIds.includes(this.req.presidentId)) this.req.presidentId = null;
    this.juryVersion.update(v => v + 1);
  }

  save(): void {
    this.busy.set(true);
    this.formError.set('');
    const call = this.editId ? this.api.updateDefense(this.editId, this.req) : this.api.scheduleDefense(this.req);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.editing.set(false);
        this.success.set('Soutenance enregistrée : étudiants, encadrant et jury sont notifiés par e-mail.');
        this.load();
        this.api.projects().subscribe(p => this.projects.set(p));
      },
      error: err => { this.busy.set(false); this.formError.set(apiError(err)); }
    });
  }

  cancel(d: Defense): void {
    if (!confirm(`Annuler la soutenance du ${frDate(d.defenseDate)} ? Les participants seront prévenus.`)) return;
    this.api.cancelDefense(d.id).subscribe({
      next: () => { this.success.set('Soutenance annulée.'); this.load(); this.api.projects().subscribe(p => this.projects.set(p)); },
      error: err => this.error.set(apiError(err))
    });
  }

  openGrade(d: Defense): void {
    this.grade = null;
    this.gradeComment = '';
    this.grading.set(d);
  }

  saveGrade(d: Defense): void {
    this.api.gradeDefense(d.id, this.grade!, this.gradeComment.trim()).subscribe({
      next: r => { this.grading.set(null); this.success.set(`Note enregistrée : ${r.grade} / 20, mention ${r.mention}.`); this.load(); },
      error: err => { this.grading.set(null); this.error.set(apiError(err)); }
    });
  }

  names(list: UserSummary[]): string { return list.map(s => s.fullName).join(' & '); }
  longDate = (d: string) => frDate(d, true);
  time = hhmm;
}
