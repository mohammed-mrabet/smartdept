import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ApiService } from '../../core/api.service';
import { AcademicApi, CourseModule, Room, Session, SessionRequest, SessionType, addDays, mondayOf, minutes } from '../../core/academic';
import { Filiere, User } from '../../core/models';
import { apiError, frDate, hhmm, today } from '../../core/labels';
import { GridItem, WeekGrid } from '../../shared/week-grid';

type View = 'me' | 'filiere' | 'teacher' | 'room';

@Component({
  selector: 'app-timetable',
  imports: [FormsModule, RouterLink, WeekGrid],
  template: `
    <div class="page wide">
      <div class="page-head">
        <div><span class="eyebrow">Scolarité</span><h1>Emploi du temps</h1>
          <p>{{ auth.isManager() ? 'Cliquez dans la grille pour ajouter une séance ; les conflits de salle, d’enseignant et de filière sont refusés.' : 'Vos cours de la semaine ; les changements vous sont notifiés.' }}</p></div>
        @if (auth.isManager()) {
          <div class="row"><a class="btn btn-ai" routerLink="/emploi-du-temps/planification">✦ Planification automatique</a>
            <button class="btn btn-primary" (click)="openForm()">✚ Séance</button></div>
        }
      </div>
      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }
      @if (success()) { <div class="alert success" style="margin-bottom:12px">{{ success() }}</div> }

      <div class="toolbar">
        <div class="seg">
          @if (!auth.isManager()) { <button [class.on]="view === 'me'" (click)="setView('me')">Mon emploi du temps</button> }
          @if (!auth.isStudent()) {
            <button [class.on]="view === 'filiere'" (click)="setView('filiere')">Filière</button>
            <button [class.on]="view === 'teacher'" (click)="setView('teacher')">Enseignant</button>
            <button [class.on]="view === 'room'" (click)="setView('room')">Salle</button>
          }
        </div>
        @if (view === 'filiere') { <select class="input slim" [(ngModel)]="filiereId" (change)="load()">@for (f of filieres(); track f.id) { <option [ngValue]="f.id">{{ f.code }}</option> }</select> }
        @if (view === 'teacher') { <select class="input slim" [(ngModel)]="teacherId" (change)="load()">@for (t of teachers(); track t.id) { <option [ngValue]="t.id">{{ t.fullName }}</option> }</select> }
        @if (view === 'room') { <select class="input slim" [(ngModel)]="roomId" (change)="load()">@for (r of rooms(); track r.id) { <option [ngValue]="r.id">{{ r.code }} ({{ r.capacity }})</option> }</select> }
        <div class="week">
          <button class="btn btn-ghost btn-sm" (click)="move(-1)">←</button>
          <button class="btn btn-ghost btn-sm" (click)="move(0)">Cette semaine</button>
          <button class="btn btn-ghost btn-sm" (click)="move(1)">→</button>
          <strong>{{ weekLabel() }}</strong>
        </div>
      </div>

      <div class="summary">
        <span><strong>{{ active().length }}</strong> séance(s)</span><span><strong>{{ hours() }}</strong> h de cours</span>
        @if (cancelled()) { <span class="muted">{{ cancelled() }} annulée(s)</span> }
        <span class="legend"><i class="cm"></i>CM <i class="td"></i>TD <i class="tp"></i>TP</span>
      </div>

      <app-week-grid [weekStart]="week()" [items]="gridItems()" (pick)="select($event)" (emptyClick)="newAt($event)" />

      @if (selected(); as s) {
        <div class="modal-backdrop" (click)="selected.set(null)">
          <div class="modal" (click)="$event.stopPropagation()">
            <span class="badge" [class]="'badge ' + (s.type === 'CM' ? 'blue' : s.type === 'TD' ? 'teal' : 'violet')">{{ s.type }}</span>
            @if (s.status === 'CANCELLED') { <span class="badge red">Annulée</span> }
            <h2 class="mt-xs">{{ s.moduleCode }} — {{ s.moduleName }}</h2>
            <dl>
              <dt>Quand</dt><dd>{{ longDate(s.date) }}, {{ time(s.startTime) }}–{{ time(s.endTime) }}</dd>
              <dt>Salle</dt><dd>{{ s.room.code }} — {{ s.room.name }} ({{ s.room.capacity }} places)</dd>
              <dt>Enseignant</dt><dd>{{ s.teacher.fullName }}</dd>
              <dt>Filière</dt><dd>{{ s.filiere.code }}</dd>
              @if (s.notes) { <dt>Note</dt><dd>{{ s.notes }}</dd> }
            </dl>
            <div class="actions">
              @if (canCallRoll(s)) { <a class="btn btn-primary" routerLink="/absences" [queryParams]="{ seance: s.id }">{{ s.attendanceTaken ? 'Modifier l’appel' : 'Faire l’appel' }}</a> }
              @if (auth.isManager() && s.status === 'PLANNED') {
                <button class="btn btn-ghost" (click)="openForm(s)">Modifier</button>
                <button class="btn btn-danger" (click)="cancel(s)">Annuler la séance</button>
              }
              @if (auth.isManager()) { <button class="btn-link small danger" (click)="remove(s)">Supprimer</button> }
            </div>
          </div>
        </div>
      }

      @if (form(); as f) {
        <div class="modal-backdrop" (click)="form.set(null)">
          <form class="modal wide-modal" (click)="$event.stopPropagation()" (ngSubmit)="save()">
            <h2>{{ editId ? 'Modifier la séance' : 'Nouvelle séance' }}</h2>
            @if (formError()) { <div class="alert error mt">{{ formError() }}</div> }
            <div class="form-grid mt">
              <div class="field full"><label>Module *</label><select name="module" [(ngModel)]="f.moduleId" required>
                @for (m of modules(); track m.id) { <option [ngValue]="m.id">{{ m.code }} — {{ m.name }} ({{ m.filiere.code }}, {{ m.teacher?.fullName || 'sans enseignant' }})</option> }</select></div>
              <div class="field"><label>Type *</label><select name="type" [(ngModel)]="f.type"><option value="CM">CM</option><option value="TD">TD</option><option value="TP">TP</option></select></div>
              <div class="field"><label>Salle *</label><select name="room" [(ngModel)]="f.roomId" required>
                @for (r of rooms(); track r.id) { <option [ngValue]="r.id">{{ r.code }} — {{ r.type }} ({{ r.capacity }})</option> }</select></div>
              <div class="field"><label>Date *</label><input type="date" name="date" [(ngModel)]="f.date" required /></div>
              <div class="field"><label>Répéter (semaines)</label><input type="number" min="1" max="16" name="weeks" [(ngModel)]="f.weeks" [disabled]="!!editId" /></div>
              <div class="field"><label>Début *</label><input type="time" name="start" [(ngModel)]="f.startTime" required /></div>
              <div class="field"><label>Fin *</label><input type="time" name="end" [(ngModel)]="f.endTime" required /></div>
              <div class="field full"><label>Note</label><input name="notes" [(ngModel)]="f.notes" /></div>
            </div>
            <div class="actions"><button type="button" class="btn btn-ghost" (click)="form.set(null)">Annuler</button>
              <button class="btn btn-primary" [disabled]="!f.moduleId || !f.roomId">Enregistrer</button></div>
          </form>
        </div>
      }
    </div>
  `,
  styles: [`
    .wide { max-width: 1400px; }
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .seg { display: inline-flex; border: 1px solid var(--border); border-radius: 9px; overflow: hidden; background: var(--surface); }
    .seg button { border: 0; background: none; padding: 8px 14px; font: 600 13.5px var(--font); cursor: pointer; color: var(--text-2); }
    .seg button.on { background: var(--primary-soft); color: var(--primary-2); }
    .slim { width: auto; height: 36px; }
    .week { display: flex; gap: 6px; align-items: center; margin-left: auto; }
    .summary { display: flex; gap: 18px; align-items: center; font-size: 13.5px; color: var(--text-2); margin-bottom: 10px; flex-wrap: wrap; }
    .legend { margin-left: auto; display: flex; gap: 6px; align-items: center; }
    .legend i { width: 12px; height: 12px; border-radius: 3px; display: inline-block; margin-left: 8px; }
    .legend .cm { background: #2563eb; } .legend .td { background: #0f8a7a; } .legend .tp { background: #6d3fc0; }
    dl { display: grid; grid-template-columns: auto 1fr; gap: 6px 14px; margin: 14px 0 0; font-size: 14px; } dt { color: var(--muted); } dd { margin: 0; }
    .mt-xs { margin-top: 8px; }
    .danger { color: var(--red); }
    .wide-modal { width: min(720px, 100%); }
  `]
})
export class Timetable implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(AcademicApi);
  private readonly core = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly week = signal(mondayOf(today()));
  readonly sessions = signal<Session[]>([]);
  readonly filieres = signal<Filiere[]>([]);
  readonly teachers = signal<User[]>([]);
  readonly rooms = signal<Room[]>([]);
  readonly modules = signal<CourseModule[]>([]);
  readonly selected = signal<Session | null>(null);
  readonly form = signal<SessionRequest | null>(null);
  readonly error = signal('');
  readonly formError = signal('');
  readonly success = signal('');

  readonly active = computed(() => this.sessions().filter(s => s.status === 'PLANNED'));
  readonly cancelled = computed(() => this.sessions().length - this.active().length);
  readonly hours = computed(() => this.active().reduce((h, s) => h + (minutes(s.endTime) - minutes(s.startTime)) / 60, 0));
  readonly weekLabel = computed(() => `Semaine du ${frDate(this.week())} au ${frDate(addDays(this.week(), 5))}`);
  readonly gridItems = computed<GridItem[]>(() => this.sessions().map(s => ({
    key: 's' + s.id, date: s.date, startTime: s.startTime, endTime: s.endTime, type: s.type, cancelled: s.status === 'CANCELLED',
    title: s.moduleCode, line2: s.room.code + (this.view === 'filiere' || this.view === 'room' || this.auth.isStudent() ? ' · ' + s.teacher.fullName : ''),
    line3: this.view === 'teacher' || this.view === 'room' || (this.view === 'me' && !this.auth.isStudent()) ? s.filiere.code : undefined, data: s
  })));

  view: View = 'me';
  filiereId: number | null = null;
  teacherId: number | null = null;
  roomId: number | null = null;
  editId: number | null = null;

  ngOnInit(): void {
    const qs = this.route.snapshot.queryParamMap.get('semaine');
    if (qs) this.week.set(mondayOf(qs));
    this.view = this.auth.isManager() ? 'filiere' : 'me';
    this.core.filieres().subscribe(f => { this.filieres.set(f); this.filiereId ??= f[0]?.id ?? null; if (this.view === 'filiere') this.load(); });
    this.api.rooms().subscribe(r => { this.rooms.set(r); this.roomId ??= r[0]?.id ?? null; });
    if (!this.auth.isStudent()) {
      this.core.users('ENSEIGNANT').subscribe({ next: t => { this.teachers.set(t); this.teacherId ??= t[0]?.id ?? null; }, error: () => {} });
      this.api.modules().subscribe(m => this.modules.set(m));
    }
    if (this.view === 'me') this.load();
  }

  setView(v: View): void { this.view = v; this.load(); }

  move(n: number): void {
    this.week.set(n === 0 ? mondayOf(today()) : addDays(this.week(), 7 * n));
    this.router.navigate([], { relativeTo: this.route, queryParams: { semaine: this.week() }, replaceUrl: true });
    this.load();
  }

  load(): void {
    const call = this.view === 'me' ? this.api.mySessions(this.week())
      : this.api.sessions({ week: this.week(), filiereId: this.view === 'filiere' ? this.filiereId : null,
        teacherId: this.view === 'teacher' ? this.teacherId : null, roomId: this.view === 'room' ? this.roomId : null });
    call.subscribe({ next: s => { this.sessions.set(s); this.error.set(''); }, error: err => this.error.set(apiError(err)) });
  }

  select(item: GridItem): void { this.selected.set(item.data as Session); }

  canCallRoll(s: Session): boolean {
    return s.status === 'PLANNED' && s.date <= today() && (this.auth.isManager() || s.teacher.id === this.auth.user()?.id);
  }

  newAt(e: { date: string; time: string }): void {
    if (!this.auth.isManager()) return;
    const end = minutes(e.time) + 120;
    this.openForm();
    this.form.update(f => f && { ...f, date: e.date, startTime: e.time, endTime: `${String(Math.floor(end / 60)).padStart(2, '0')}:${String(end % 60).padStart(2, '0')}`,
      roomId: this.view === 'room' ? this.roomId : f.roomId,
      moduleId: this.view === 'filiere' ? (this.modules().find(m => m.filiere.id === this.filiereId)?.id ?? f.moduleId) : f.moduleId });
  }

  openForm(s?: Session): void {
    this.selected.set(null);
    this.formError.set('');
    this.editId = s?.id ?? null;
    this.form.set(s ? { moduleId: s.moduleId, teacherId: s.teacher.id, roomId: s.room.id, date: s.date, startTime: hhmm(s.startTime), endTime: hhmm(s.endTime), type: s.type, notes: s.notes, weeks: 1 }
      : { moduleId: this.modules()[0]?.id ?? null, roomId: this.rooms()[0]?.id ?? null, date: this.week(), startTime: '08:30', endTime: '10:30', type: 'CM' as SessionType, weeks: 1 });
  }

  save(): void {
    const f = this.form()!;
    const call: Observable<unknown> = this.editId ? this.api.updateSession(this.editId, f) : this.api.createSession(f);
    call.subscribe({
      next: () => { this.form.set(null); this.success.set(this.editId ? 'Séance modifiée.' : 'Séance(s) ajoutée(s).'); this.load(); },
      error: err => this.formError.set(apiError(err))
    });
  }

  cancel(s: Session): void {
    const reason = prompt('Motif de l’annulation (envoyé aux étudiants) :', '');
    if (reason === null) return;
    this.api.cancelSession(s.id, reason).subscribe({ next: () => { this.selected.set(null); this.success.set('Séance annulée : les étudiants sont prévenus.'); this.load(); },
      error: err => this.error.set(apiError(err)) });
  }

  remove(s: Session): void {
    if (!confirm('Supprimer définitivement cette séance ?')) return;
    this.api.deleteSession(s.id).subscribe({ next: () => { this.selected.set(null); this.load(); } });
  }

  longDate = (d: string) => frDate(d, true);
  time = hhmm;
}
