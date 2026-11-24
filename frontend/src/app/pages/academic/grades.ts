import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ASSESSMENTS, AcademicApi, Assessment, CourseModule, GRADE_STATUS, ModuleGrades, Transcript } from '../../core/academic';
import { apiError } from '../../core/labels';

/** Étudiant : relevé de notes. Enseignant / chef : saisie et publication des notes d'un module. */
@Component({
  selector: 'app-grades',
  imports: [FormsModule],
  template: `
    <div class="page">
      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }
      @if (success()) { <div class="alert success" style="margin-bottom:12px">{{ success() }}</div> }

      @if (transcript(); as t) {
        <div class="page-head no-print">
          <div><span class="eyebrow">Scolarité</span><h1>{{ auth.isStudent() ? 'Mon relevé de notes' : 'Relevé de ' + t.student.fullName }}</h1>
            <p>Note de module : 30 % CC, 50 % examen, 20 % TP ; validation à 10/20, compensation possible si la moyenne du semestre atteint 10 (note éliminatoire : 5).</p></div>
          <button class="btn btn-ghost" (click)="print()">🖨 Imprimer</button>
        </div>
        <div class="card transcript">
          <div class="row between">
            <div><h2>{{ t.student.fullName }}</h2><p class="text-2">{{ t.filiere?.name }} · n° {{ t.studentNumber }}</p></div>
            <div class="avg"><small>Moyenne générale</small><strong [class.bad]="(t.overallAverage ?? 20) < 10">{{ t.overallAverage != null ? t.overallAverage.toFixed(2) : '—' }}</strong>
              <span class="badge" [class]="'badge ' + decisionColor(t.decision)">{{ t.decision }}</span></div>
          </div>
          @for (s of t.semesters; track s.semester) {
            <h3 class="mt-lg">Semestre {{ s.semester }} <small class="muted">· moyenne {{ s.average != null ? s.average.toFixed(2) : '—' }} · {{ s.validated }}/{{ s.total }} module(s) validé(s)</small></h3>
            <table class="table mt-xs">
              <thead><tr><th>Module</th><th>Coef.</th><th>CC</th><th>Examen</th><th>TP</th><th>Ratt.</th><th>Note</th><th>Résultat</th></tr></thead>
              <tbody>@for (l of s.lines; track l.moduleId) {
                <tr><td><strong>{{ l.code }}</strong> {{ l.name }}</td><td>{{ l.coefficient }}</td><td>{{ n(l.cc) }}</td><td>{{ n(l.exam) }}</td><td>{{ n(l.tp) }}</td><td>{{ n(l.rattrapage) }}</td>
                  <td><strong [class.bad]="(l.average ?? 20) < 10">{{ n(l.average) }}</strong></td>
                  <td><span class="badge" [class]="'badge ' + status[l.status][1]">{{ status[l.status][0] }}</span></td></tr>
              }</tbody>
            </table>
          } @empty { <div class="empty">Aucun module pour votre filière.</div> }
        </div>
      }

      @if (!auth.isStudent() && !studentView) {
        <div class="page-head"><div><span class="eyebrow">Scolarité</span><h1>Saisie des notes</h1>
          <p>Saisissez une composante puis publiez-la : chaque étudiant reçoit une notification et un e-mail.</p></div></div>
        <div class="toolbar">
          <select class="input slim" [(ngModel)]="moduleId" (change)="loadModule()">
            @for (m of modules(); track m.id) { <option [ngValue]="m.id">{{ m.code }} — {{ m.name }} ({{ m.filiere.code }})</option> } </select>
          <div class="seg">@for (a of assessments; track a.code) {
            <button [class.on]="assessment === a.code" (click)="setAssessment(a.code)">{{ a.label }} @if (sheet()?.published?.[a.code]) { <span title="publiée">✓</span> }</button> }</div>
        </div>
        @if (sheet(); as s) {
          <div class="grid grid-4">
            <div class="card stat"><div class="icon bg-blue">∑</div><div><div class="value">{{ s.classAverage ?? '—' }}</div><div class="label">moyenne de la classe</div></div></div>
            <div class="card stat"><div class="icon bg-teal">✓</div><div><div class="value">{{ s.passRate != null ? s.passRate + ' %' : '—' }}</div><div class="label">de réussite</div></div></div>
            <div class="card stat"><div class="icon bg-amber">↓</div><div><div class="value">{{ s.min ?? '—' }}</div><div class="label">note la plus basse</div></div></div>
            <div class="card stat"><div class="icon bg-violet">↑</div><div><div class="value">{{ s.max ?? '—' }}</div><div class="label">note la plus haute</div></div></div>
          </div>
          <div class="card mt">
            <table class="table">
              <thead><tr><th>Étudiant</th>@for (a of assessments; track a.code) { <th [class.cur]="a.code === assessment">{{ a.code }}</th> }<th>Moyenne</th><th>Résultat</th></tr></thead>
              <tbody>@for (r of s.rows; track r.student.id) {
                <tr><td><strong>{{ r.student.fullName }}</strong><br /><small class="muted">{{ r.studentNumber }}</small></td>
                  @for (a of assessments; track a.code) {
                    <td [class.cur]="a.code === assessment">
                      @if (a.code === assessment) { <input class="grade" type="number" min="0" max="20" step="0.25" [(ngModel)]="draft[r.student.id]" [name]="'g' + r.student.id" /> }
                      @else { {{ n(r.grades[a.code]) }} }
                    </td>
                  }
                  <td><strong>{{ n(r.average) }}</strong></td>
                  <td><span class="badge" [class]="'badge ' + status[r.status][1]">{{ status[r.status][0] }}</span></td></tr>
              }</tbody>
            </table>
            <div class="row mt">
              <button class="btn btn-primary" (click)="save()" [disabled]="busy()">Enregistrer</button>
              <button class="btn btn-success" (click)="publish()" [disabled]="busy() || s.published[assessment]">{{ s.published[assessment] ? 'Publiée ✓' : 'Publier ' + label(assessment) }}</button>
              <span class="muted small">Une case vide supprime la note.</span>
            </div>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .transcript { margin-bottom: 24px; }
    .avg { text-align: right; display: grid; gap: 4px; justify-items: end; } .avg small { color: var(--muted); } .avg strong { font-size: 30px; }
    .bad { color: var(--red); }
    .mt-xs { margin-top: 6px; }
    .slim { width: auto; max-width: 420px; }
    .seg { display: inline-flex; border: 1px solid var(--border); border-radius: 9px; overflow: hidden; background: var(--surface); }
    .seg button { border: 0; background: none; padding: 8px 12px; font: 600 13px var(--font); cursor: pointer; color: var(--text-2); }
    .seg button.on { background: var(--primary-soft); color: var(--primary-2); }
    th.cur, td.cur { background: var(--highlight); }
    .grade { width: 80px; padding: 5px 8px; border: 1px solid var(--border); border-radius: 7px; font: inherit; }
  `]
})
export class Grades implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(AcademicApi);
  private readonly route = inject(ActivatedRoute);
  readonly assessments = ASSESSMENTS;
  readonly status = GRADE_STATUS;
  readonly transcript = signal<Transcript | null>(null);
  readonly modules = signal<CourseModule[]>([]);
  readonly sheet = signal<ModuleGrades | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  moduleId: number | null = null;
  assessment: Assessment = 'CC';
  draft: Record<number, number | null> = {};
  studentView = false;

  ngOnInit(): void {
    const studentId = this.route.snapshot.queryParamMap.get('etudiant');
    if (this.auth.isStudent()) {
      this.api.myTranscript().subscribe({ next: t => this.transcript.set(t), error: err => this.error.set(apiError(err)) });
      return;
    }
    if (studentId) {
      this.studentView = true;
      this.api.transcript(Number(studentId)).subscribe({ next: t => this.transcript.set(t), error: err => this.error.set(apiError(err)) });
      return;
    }
    this.api.modules({ mine: !this.auth.isManager() }).subscribe(m => {
      this.modules.set(m);
      this.moduleId = m[0]?.id ?? null;
      this.loadModule();
    });
  }

  loadModule(): void {
    if (!this.moduleId) return;
    this.api.moduleGrades(this.moduleId).subscribe({ next: s => this.setSheet(s), error: err => this.error.set(apiError(err)) });
  }

  private setSheet(s: ModuleGrades): void {
    this.sheet.set(s);
    this.draft = Object.fromEntries(s.rows.map(r => [r.student.id, r.grades[this.assessment] ?? null]));
  }

  setAssessment(a: Assessment): void { this.assessment = a; const s = this.sheet(); if (s) this.setSheet(s); }

  save(): void {
    const entries = Object.entries(this.draft).map(([id, v]) => ({ studentId: Number(id), value: v === null || (v as unknown) === '' ? null : Number(v) }));
    this.busy.set(true);
    this.api.saveGrades(this.moduleId!, this.assessment, entries).subscribe({
      next: s => { this.setSheet(s); this.busy.set(false); this.success.set('Notes enregistrées.'); this.error.set(''); },
      error: err => { this.busy.set(false); this.error.set(apiError(err)); }
    });
  }

  publish(): void {
    if (!confirm(`Publier les notes de ${this.label(this.assessment)} ? Les étudiants seront notifiés.`)) return;
    this.busy.set(true);
    this.api.publishGrades(this.moduleId!, this.assessment).subscribe({
      next: s => { this.setSheet(s); this.busy.set(false); this.success.set('Notes publiées : les étudiants sont notifiés.'); },
      error: err => { this.busy.set(false); this.error.set(apiError(err)); }
    });
  }

  label(a: Assessment): string { return ASSESSMENTS.find(x => x.code === a)?.label.toLowerCase() ?? a; }
  decisionColor(d: string): string { return d.startsWith('Admis') ? (d === 'Admis' ? 'teal' : 'blue') : d === 'Rattrapage' ? 'red' : 'gray'; }
  n(v?: number | null): string { return v == null ? '—' : v.toFixed(2).replace(/\.00$/, ''); }
  print(): void { window.print(); }
}
