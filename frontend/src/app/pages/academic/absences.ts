import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Absence, AcademicApi, AttendanceSheet, AttendanceStatus, JUSTIF, Session, addDays, mondayOf } from '../../core/academic';
import { apiError, frDate, hhmm, today } from '../../core/labels';

/** Étudiant : ses absences et justificatifs. Enseignant : appel des séances. Chef : examen des justificatifs. */
@Component({
  selector: 'app-absences',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="page-head"><div><span class="eyebrow">Scolarité</span><h1>{{ auth.isStudent() ? 'Mes absences' : 'Absences et appel' }}</h1>
        <p>{{ auth.isStudent() ? 'Justifiez une absence en quelques mots : le chef de département examine votre justificatif.' : 'Faites l’appel de vos séances ; au-delà de 3 absences non justifiées dans un module, l’étudiant et le chef de département sont prévenus.' }}</p></div></div>
      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }
      @if (success()) { <div class="alert success" style="margin-bottom:12px">{{ success() }}</div> }

      @if (auth.isStudent()) {
        <div class="grid grid-3">
          <div class="card stat"><div class="icon bg-red">✗</div><div><div class="value">{{ unjustified() }}</div><div class="label">absence(s) non justifiée(s)</div></div></div>
          <div class="card stat"><div class="icon bg-amber">⏳</div><div><div class="value">{{ pendingMine() }}</div><div class="label">justificatif(s) en examen</div></div></div>
          <div class="card stat"><div class="icon bg-teal">✓</div><div><div class="value">{{ justified() }}</div><div class="label">justifiée(s)</div></div></div>
        </div>
        <div class="card mt">
          <table class="table"><thead><tr><th>Séance</th><th>Date</th><th>Statut</th><th>Justificatif</th><th></th></tr></thead>
            <tbody>@for (a of mine(); track a.id) {
              <tr><td><strong>{{ a.moduleCode }}</strong> {{ a.moduleName }}<br /><small class="muted">{{ a.teacherName }}</small></td>
                <td class="nowrap">{{ date(a.date) }} {{ time(a.startTime) }}</td>
                <td>{{ a.status === 'LATE' ? 'Retard' : 'Absent(e)' }}</td>
                <td><span class="badge" [class]="'badge ' + justif[a.justificationStatus][1]">{{ justif[a.justificationStatus][0] }}</span>
                  @if (a.reviewComment) { <br /><small class="muted">{{ a.reviewComment }}</small> }</td>
                <td>@if (a.justificationStatus === 'NONE' || a.justificationStatus === 'REJECTED') { <button class="btn btn-ghost btn-sm" (click)="justifying.set(a)">Justifier</button> }</td></tr>
            } @empty { <tr><td colspan="5" class="empty">Aucune absence enregistrée 👏</td></tr> }</tbody></table>
        </div>
      } @else {
        @if (auth.isManager() && pending().length) {
          <div class="card">
            <div class="card-head"><h3>Justificatifs à examiner ({{ pending().length }})</h3></div>
            @for (a of pending(); track a.id) {
              <div class="pending">
                <div class="grow"><strong>{{ a.student.fullName }}</strong> — {{ a.moduleCode }} du {{ date(a.date) }}<p class="small text-2">« {{ a.justification }} »</p></div>
                <button class="btn btn-success btn-sm" (click)="review(a, true)">Accepter</button>
                <button class="btn btn-danger btn-sm" (click)="review(a, false)">Refuser</button>
              </div>
            }
          </div>
        }
        <div class="card mt">
          <div class="card-head"><h3>Mes séances (deux dernières semaines)</h3></div>
          <table class="table"><thead><tr><th>Séance</th><th>Date</th><th>Filière</th><th>Appel</th><th></th></tr></thead>
            <tbody>@for (s of sessions(); track s.id) {
              <tr><td><strong>{{ s.moduleCode }}</strong> {{ s.type }}</td><td class="nowrap">{{ date(s.date) }} {{ time(s.startTime) }}</td><td>{{ s.filiere.code }}</td>
                <td>@if (s.attendanceTaken) { <span class="badge teal">Fait</span> } @else { <span class="badge amber">À faire</span> }</td>
                <td><button class="btn btn-ghost btn-sm" (click)="openSheet(s.id)">{{ s.attendanceTaken ? 'Modifier' : 'Faire l’appel' }}</button></td></tr>
            } @empty { <tr><td colspan="5" class="empty">Aucune séance passée récente.</td></tr> }</tbody></table>
        </div>
      }

      @if (justifying(); as a) {
        <div class="modal-backdrop" (click)="justifying.set(null)">
          <form class="modal" (click)="$event.stopPropagation()" (ngSubmit)="justify(a)">
            <h2>Justifier une absence</h2><p class="text-2 small">{{ a.moduleCode }} du {{ date(a.date) }}</p>
            <div class="field mt"><label>Motif *</label><textarea name="r" [(ngModel)]="reason" required placeholder="Certificat médical déposé au secrétariat…"></textarea></div>
            <div class="actions"><button type="button" class="btn btn-ghost" (click)="justifying.set(null)">Annuler</button><button class="btn btn-primary" [disabled]="!reason.trim()">Envoyer</button></div>
          </form>
        </div>
      }

      @if (sheet(); as sh) {
        <div class="modal-backdrop" (click)="sheet.set(null)">
          <div class="modal wide" (click)="$event.stopPropagation()">
            <h2>Appel — {{ sh.session.moduleCode }} {{ sh.session.type }}</h2>
            <p class="text-2 small">{{ date(sh.session.date) }} · {{ time(sh.session.startTime) }}–{{ time(sh.session.endTime) }} · {{ sh.session.room.code }} · {{ sh.rows.length }} étudiant(s)</p>
            <div class="row mt"><button class="btn-link small" (click)="all('PRESENT')">Tous présents</button></div>
            <div class="roll">
              @for (r of sh.rows; track r.studentId) {
                <div class="roll-row">
                  <span class="grow"><strong>{{ r.fullName }}</strong> <small class="muted">{{ r.studentNumber }}</small></span>
                  <div class="seg">
                    <button [class.on]="marks[r.studentId] === 'PRESENT'" (click)="marks[r.studentId] = 'PRESENT'">Présent</button>
                    <button [class.on-amber]="marks[r.studentId] === 'LATE'" (click)="marks[r.studentId] = 'LATE'">Retard</button>
                    <button [class.on-red]="marks[r.studentId] === 'ABSENT'" (click)="marks[r.studentId] = 'ABSENT'">Absent</button>
                  </div>
                </div>
              }
            </div>
            <div class="actions"><button class="btn btn-ghost" (click)="sheet.set(null)">Fermer</button><button class="btn btn-primary" (click)="saveSheet(sh)">Enregistrer l’appel</button></div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .pending { display: flex; gap: 10px; align-items: center; padding: 10px 0; border-bottom: 1px solid #eef1f6; }
    .grow { flex: 1; min-width: 0; }
    .wide { width: min(680px, 100%); }
    .roll { max-height: 55vh; overflow-y: auto; margin-top: 8px; }
    .roll-row { display: flex; gap: 10px; align-items: center; padding: 8px 0; border-bottom: 1px solid #eef1f6; }
    .seg { display: inline-flex; border: 1px solid var(--border); border-radius: 9px; overflow: hidden; }
    .seg button { border: 0; background: #fff; padding: 6px 10px; font: 600 12.5px var(--font); cursor: pointer; color: var(--text-2); }
    .seg button.on { background: var(--teal-soft); color: var(--teal); }
    .seg button.on-amber { background: var(--amber-soft); color: var(--amber); }
    .seg button.on-red { background: var(--red-soft); color: var(--red); }
  `]
})
export class Absences implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(AcademicApi);
  private readonly route = inject(ActivatedRoute);
  readonly justif = JUSTIF;
  readonly mine = signal<Absence[]>([]);
  readonly pending = signal<Absence[]>([]);
  readonly sessions = signal<Session[]>([]);
  readonly sheet = signal<AttendanceSheet | null>(null);
  readonly justifying = signal<Absence | null>(null);
  readonly error = signal('');
  readonly success = signal('');
  readonly unjustified = computed(() => this.mine().filter(a => a.status === 'ABSENT' && a.justificationStatus !== 'ACCEPTED' && a.justificationStatus !== 'PENDING').length);
  readonly pendingMine = computed(() => this.mine().filter(a => a.justificationStatus === 'PENDING').length);
  readonly justified = computed(() => this.mine().filter(a => a.justificationStatus === 'ACCEPTED').length);
  marks: Record<number, AttendanceStatus> = {};
  reason = '';

  ngOnInit(): void {
    if (this.auth.isStudent()) {
      this.api.myAbsences().subscribe({ next: a => this.mine.set(a), error: err => this.error.set(apiError(err)) });
      return;
    }
    if (this.auth.isManager()) this.api.pendingJustifications().subscribe(p => this.pending.set(p));
    this.loadSessions();
    const s = this.route.snapshot.queryParamMap.get('seance');
    if (s) this.openSheet(Number(s));
  }

  loadSessions(): void {
    const t = today();
    const thisWeek = mondayOf(t);
    const load = (week: string) => this.auth.isManager() ? this.api.sessions({ week }) : this.api.mySessions(week);
    load(addDays(thisWeek, -7)).subscribe(prev => load(thisWeek).subscribe(cur => this.sessions.set(
      [...cur, ...prev].filter(s => s.status === 'PLANNED' && s.date <= t).sort((a, b) => (b.date + b.startTime).localeCompare(a.date + a.startTime)))));
  }

  openSheet(id: number): void {
    this.api.attendance(id).subscribe({
      next: sh => { this.marks = Object.fromEntries(sh.rows.map(r => [r.studentId, r.status])); this.sheet.set(sh); },
      error: err => this.error.set(apiError(err))
    });
  }

  all(s: AttendanceStatus): void { Object.keys(this.marks).forEach(k => this.marks[Number(k)] = s); }

  saveSheet(sh: AttendanceSheet): void {
    const entries = Object.entries(this.marks).map(([id, status]) => ({ studentId: Number(id), status }));
    this.api.saveAttendance(sh.session.id, entries).subscribe({
      next: () => { this.sheet.set(null); this.success.set('Appel enregistré.'); this.loadSessions(); },
      error: err => this.error.set(apiError(err))
    });
  }

  justify(a: Absence): void {
    this.api.justify(a.id, this.reason.trim()).subscribe({
      next: u => { this.mine.update(l => l.map(x => x.id === u.id ? u : x)); this.justifying.set(null); this.reason = ''; this.success.set('Justificatif envoyé au chef de département.'); },
      error: err => { this.justifying.set(null); this.error.set(apiError(err)); }
    });
  }

  review(a: Absence, accept: boolean): void {
    const comment = accept ? '' : prompt('Motif du refus (envoyé à l’étudiant) :', '');
    if (comment === null) return;
    this.api.review(a.id, accept, comment).subscribe({
      next: () => this.pending.update(l => l.filter(x => x.id !== a.id)),
      error: err => this.error.set(apiError(err))
    });
  }

  date = (d: string) => frDate(d);
  time = hhmm;
}
