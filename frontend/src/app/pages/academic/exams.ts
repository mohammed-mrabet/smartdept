import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { ApiService } from '../../core/api.service';
import { AcademicApi, CourseModule, EXAM_LABEL, Exam, ExamPlanPreview, ExamRequest, ExamType, Room, addDays } from '../../core/academic';
import { Filiere, User } from '../../core/models';
import { apiError, daysUntil, frDate, hhmm, today } from '../../core/labels';

@Component({
  selector: 'app-exams',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="page-head">
        <div><span class="eyebrow">Scolarité</span><h1>Examens</h1>
          <p>{{ auth.isStudent() ? 'Vos prochaines épreuves : date, salle et durée.' : auth.isManager() ? 'Calendrier, salles, surveillants et planification automatique.' : 'Les examens de vos modules et vos surveillances.' }}</p></div>
        @if (auth.isManager()) {
          <div class="row"><button class="btn btn-ai" (click)="openPlanner()">✦ Planifier une session</button><button class="btn btn-primary" (click)="openForm()">✚ Examen</button></div>
        }
      </div>
      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }
      @if (success()) { <div class="alert success" style="margin-bottom:12px">{{ success() }}</div> }
      @if (auth.isManager()) {
        <div class="toolbar"><select class="input slim" [(ngModel)]="filiereId" (change)="load()"><option [ngValue]="null">Toutes les filières</option>
          @for (f of filieres(); track f.id) { <option [ngValue]="f.id">{{ f.code }}</option> }</select></div>
      }
      @for (g of groups(); track g.date) {
        <h3 class="day">{{ longDate(g.date) }} @if (until(g.date) >= 0) { <small class="muted">· {{ until(g.date) === 0 ? 'aujourd’hui' : 'dans ' + until(g.date) + ' j' }}</small> }</h3>
        @for (e of g.items; track e.id) {
          <div class="card exam">
            <div class="time"><strong>{{ time(e.startTime) }}</strong><small>{{ time(e.endTime) }}</small></div>
            <div class="grow">
              <div class="row between"><h3>{{ e.moduleCode }} — {{ e.moduleName }}</h3><span class="badge amber">{{ examLabel[e.type] }}</span></div>
              <p class="text-2 small">{{ e.filiere.code }} · salle(s) <strong>{{ roomCodes(e) }}</strong> · {{ e.capacity }} places pour {{ e.students }} étudiants</p>
              @if (!auth.isStudent()) { <p class="small mt-xs"><span class="muted">Surveillants :</span> {{ supervisors(e) || '—' }}</p> }
              @if (e.notes) { <p class="small mt-xs">📌 {{ e.notes }}</p> }
            </div>
            @if (auth.isManager()) { <button class="btn-link small danger" (click)="remove(e)">Supprimer</button> }
          </div>
        }
      } @empty { <div class="card empty"><div class="big">📝</div>Aucun examen programmé.</div> }

      @if (form(); as f) {
        <div class="modal-backdrop" (click)="form.set(null)">
          <form class="modal wide" (click)="$event.stopPropagation()" (ngSubmit)="save()">
            <h2>Nouvel examen</h2>
            @if (formError()) { <div class="alert error mt">{{ formError() }}</div> }
            <div class="form-grid mt">
              <div class="field full"><label>Module *</label><select name="m" [(ngModel)]="f.moduleId">@for (m of modules(); track m.id) { <option [ngValue]="m.id">{{ m.code }} — {{ m.name }} ({{ m.filiere.code }})</option> }</select></div>
              <div class="field"><label>Type</label><select name="t" [(ngModel)]="f.type">@for (t of types; track t) { <option [value]="t">{{ examLabel[t] }}</option> }</select></div>
              <div class="field"><label>Date *</label><input type="date" name="d" [(ngModel)]="f.date" required /></div>
              <div class="field"><label>Début</label><input type="time" name="s" [(ngModel)]="f.startTime" /></div>
              <div class="field"><label>Fin</label><input type="time" name="e" [(ngModel)]="f.endTime" /></div>
            </div>
            <label class="mt">Salles</label>
            <div class="checks">@for (r of rooms(); track r.id) { <label class="check"><input type="checkbox" [checked]="f.roomIds.includes(r.id)" (change)="toggle(f.roomIds, r.id)" /> {{ r.code }} ({{ r.capacity }})</label> }</div>
            <label class="mt">Surveillants</label>
            <div class="checks">@for (t of teachers(); track t.id) { <label class="check"><input type="checkbox" [checked]="f.supervisorIds.includes(t.id)" (change)="toggle(f.supervisorIds, t.id)" /> {{ t.fullName }}</label> }</div>
            <div class="field mt"><label>Consignes</label><input name="n" [(ngModel)]="f.notes" /></div>
            <div class="actions"><button type="button" class="btn btn-ghost" (click)="form.set(null)">Annuler</button><button class="btn btn-primary">Programmer et notifier</button></div>
          </form>
        </div>
      }

      @if (plannerOpen()) {
        <div class="modal-backdrop" (click)="plannerOpen.set(false)">
          <div class="modal wide" (click)="$event.stopPropagation()">
            <h2>✦ Planifier une session d’examens</h2>
            <p class="text-2 small mt-xs">Un examen par jour pour la filière, salles cumulées selon l’effectif, deux surveillants par salle (l’enseignant du module d’abord).</p>
            <div class="form-grid mt">
              <div class="field"><label>Filière</label><select [(ngModel)]="plan.filiereId">@for (f of filieres(); track f.id) { <option [ngValue]="f.id">{{ f.code }}</option> }</select></div>
              <div class="field"><label>Type</label><select [(ngModel)]="plan.type">@for (t of types; track t) { <option [value]="t">{{ examLabel[t] }}</option> }</select></div>
              <div class="field"><label>Du</label><input type="date" [(ngModel)]="plan.from" /></div>
              <div class="field"><label>Au</label><input type="date" [(ngModel)]="plan.to" /></div>
              <div class="field"><label>Semestre</label><input [(ngModel)]="plan.semester" placeholder="S3 (facultatif)" /></div>
              <div class="field"><label>Durée</label><select [(ngModel)]="plan.durationMinutes"><option [ngValue]="90">1 h 30</option><option [ngValue]="120">2 h</option><option [ngValue]="180">3 h</option></select></div>
            </div>
            <button class="btn btn-ai mt" (click)="previewPlan()" [disabled]="busy()">Proposer</button>
            @if (preview(); as p) {
              <table class="table mt"><thead><tr><th>Date</th><th>Module</th><th>Salles</th><th>Surveillants</th></tr></thead>
                <tbody>@for (s of p.slots; track s.moduleId) {
                  <tr><td class="nowrap">{{ shortDate(s.date) }} {{ time(s.startTime) }}</td><td>{{ s.moduleCode }}</td><td>{{ s.roomCodes.join(', ') }}</td><td class="small">{{ s.supervisorNames.join(', ') }}</td></tr>
                } @empty { <tr><td colspan="4" class="empty">Aucun module à planifier (déjà programmés ?).</td></tr> }</tbody></table>
              @if (p.unplaced.length) { <div class="alert warn">@for (u of p.unplaced; track $index) { <div>{{ u }}</div> }</div> }
            }
            <div class="actions"><button class="btn btn-ghost" (click)="plannerOpen.set(false)">Fermer</button>
              <button class="btn btn-primary" [disabled]="!preview()?.slots?.length || busy()" (click)="applyPlan()">Enregistrer et notifier</button></div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .slim { width: auto; }
    .day { margin: 20px 0 10px; text-transform: capitalize; color: var(--text-2); }
    .exam { display: flex; gap: 18px; align-items: flex-start; margin-bottom: 10px; }
    .time { width: 64px; flex: none; text-align: center; border-right: 1px solid var(--border); padding-right: 14px; display: flex; flex-direction: column; }
    .time strong { font-size: 19px; } .time small { color: var(--muted); }
    .grow { flex: 1; min-width: 0; } .mt-xs { margin-top: 6px; }
    .danger { color: var(--red); }
    .wide { width: min(820px, 100%); }
    .checks { display: flex; flex-wrap: wrap; gap: 6px 16px; margin-top: 6px; font-size: 13.5px; }
  `]
})
export class Exams implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(AcademicApi);
  private readonly core = inject(ApiService);
  readonly examLabel = EXAM_LABEL;
  readonly types: ExamType[] = ['FINAL', 'CC', 'TP', 'RATTRAPAGE'];
  readonly exams = signal<Exam[]>([]);
  readonly filieres = signal<Filiere[]>([]);
  readonly modules = signal<CourseModule[]>([]);
  readonly rooms = signal<Room[]>([]);
  readonly teachers = signal<User[]>([]);
  readonly form = signal<ExamRequest | null>(null);
  readonly plannerOpen = signal(false);
  readonly preview = signal<ExamPlanPreview | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly formError = signal('');
  readonly success = signal('');
  readonly groups = computed(() => {
    const map = new Map<string, Exam[]>();
    this.exams().forEach(e => map.set(e.date, [...(map.get(e.date) ?? []), e]));
    return [...map.entries()].map(([date, items]) => ({ date, items }));
  });
  filiereId: number | null = null;
  plan = { filiereId: 0, type: 'FINAL' as ExamType, from: addDays(today(), 21), to: addDays(today(), 33), semester: '', durationMinutes: 120 };

  ngOnInit(): void {
    this.load();
    if (this.auth.isManager()) {
      this.core.filieres().subscribe(f => { this.filieres.set(f); this.plan.filiereId = f[0]?.id ?? 0; });
      this.api.modules().subscribe(m => this.modules.set(m));
      this.api.rooms().subscribe(r => this.rooms.set(r));
      this.core.users('ENSEIGNANT').subscribe(t => this.teachers.set(t));
    }
  }

  load(): void {
    const call = this.auth.isManager() ? this.api.exams({ from: addDays(today(), -30), filiereId: this.filiereId }) : this.api.myExams();
    call.subscribe({ next: e => this.exams.set(e), error: err => this.error.set(apiError(err)) });
  }

  openForm(): void {
    this.formError.set('');
    this.form.set({ moduleId: this.modules()[0]?.id ?? null, date: addDays(today(), 14), startTime: '09:00', endTime: '11:00', type: 'FINAL', roomIds: [], supervisorIds: [] });
  }

  toggle(list: number[], id: number): void {
    const i = list.indexOf(id);
    if (i >= 0) list.splice(i, 1); else list.push(id);
  }

  save(): void {
    this.api.createExam(this.form()!).subscribe({
      next: () => { this.form.set(null); this.success.set('Examen programmé : les étudiants sont prévenus.'); this.load(); },
      error: err => this.formError.set(apiError(err))
    });
  }

  remove(e: Exam): void {
    if (!confirm(`Supprimer l’examen ${e.moduleCode} du ${frDate(e.date)} ?`)) return;
    this.api.deleteExam(e.id).subscribe({ next: () => this.load() });
  }

  openPlanner(): void { this.preview.set(null); this.plannerOpen.set(true); }

  previewPlan(): void {
    this.busy.set(true);
    this.api.previewExams({ ...this.plan, semester: this.plan.semester || null }).subscribe({
      next: p => { this.preview.set(p); this.busy.set(false); },
      error: err => { this.error.set(apiError(err)); this.busy.set(false); this.plannerOpen.set(false); }
    });
  }

  applyPlan(): void {
    const p = this.preview()!;
    this.busy.set(true);
    this.api.applyExams(p.filiereId, p.type, p.slots).subscribe({
      next: r => { this.busy.set(false); this.plannerOpen.set(false); this.success.set(`${r.length} examen(s) programmé(s) ; étudiants et surveillants notifiés.`); this.load(); },
      error: err => { this.busy.set(false); this.error.set(apiError(err)); this.plannerOpen.set(false); }
    });
  }

  roomCodes(e: Exam): string { return e.rooms.map(r => r.code).join(', '); }
  supervisors(e: Exam): string { return e.supervisors.map(s => s.fullName).join(', '); }
  until = daysUntil;
  longDate = (d: string) => frDate(d, true);
  shortDate = (d: string) => frDate(d);
  time = hhmm;
}
