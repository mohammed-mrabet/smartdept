import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { AuthService } from '../../../core/auth.service';
import { Filiere, SubjectRequest, SubjectSuggestion, SubjectType, SupervisorSuggestion, User } from '../../../core/models';
import { SUBJECT_TYPE, apiError } from '../../../core/labels';

@Component({
  selector: 'app-subject-form',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page">
      <a routerLink="/pfe/sujets" class="small">← Tous les sujets</a>
      <div class="page-head mt">
        <div>
          <span class="eyebrow">Projets de fin d’études</span>
          <h1>{{ id() ? 'Modifier le sujet' : 'Proposer un sujet' }}</h1>
          <p>{{ auth.isManager() ? 'Un sujet créé par le chef de département est publié directement.' : 'Le sujet sera validé par le chef de département avant d’être visible des étudiants.' }}</p>
        </div>
      </div>

      @if (error()) { <div class="alert error" style="margin-bottom:16px">{{ error() }}</div> }

      <div class="grid layout">
        <form class="card" (ngSubmit)="save()" #f="ngForm">
          <div class="form-grid">
            <div class="field full"><label>Titre *</label>
              <input name="title" [(ngModel)]="model.title" required maxlength="200" placeholder="Ex. Plateforme de détection de fraudes par apprentissage automatique" /></div>
            <div class="field full"><label>Description *</label>
              <textarea name="description" [(ngModel)]="model.description" required rows="9" placeholder="Contexte, problématique, travail demandé, technologies…"></textarea>
              <span class="hint">{{ model.description.length }} caractères · 80 au minimum conseillés</span></div>
            <div class="field"><label>Type *</label>
              <select name="type" [(ngModel)]="model.type">@for (t of types; track t) { <option [value]="t">{{ typeLabel[t] }}</option> }</select></div>
            <div class="field"><label>Filière *</label>
              <select name="filiereId" [(ngModel)]="model.filiereId" required>
                <option [ngValue]="null" disabled>Choisir…</option>
                @for (fi of filieres(); track fi.id) { <option [ngValue]="fi.id">{{ fi.code }} — {{ fi.name }}</option> }
              </select></div>
            @if (model.type === 'ENTREPRISE') {
              <div class="field"><label>Entreprise *</label><input name="companyName" [(ngModel)]="model.companyName" required /></div>
            }
            <div class="field"><label>Nombre d’étudiants</label>
              <select name="maxStudents" [(ngModel)]="model.maxStudents"><option [ngValue]="1">1 (monôme)</option><option [ngValue]="2">2 (binôme)</option></select></div>
            <div class="field full"><label>Mots-clés</label>
              <input name="keywords" [(ngModel)]="model.keywords" placeholder="séparés par des virgules : spring boot, angular, ia" />
              <div>@for (k of keywordList(); track k) { <span class="chip">{{ k }}</span> }</div></div>
            @if (auth.isManager()) {
              <div class="field full"><label>Encadrant</label>
                <select name="supervisorId" [(ngModel)]="model.supervisorId">
                  <option [ngValue]="null">Moi-même</option>
                  @for (t of teachers(); track t.id) { <option [ngValue]="t.id">{{ t.fullName }}@if (t.specialties) { — {{ t.specialties }} }</option> }
                </select></div>
            }
          </div>
          <div class="row mt-lg">
            <button class="btn btn-primary" [disabled]="busy() || !f.valid">{{ busy() ? 'Enregistrement…' : (id() ? 'Enregistrer' : 'Proposer le sujet') }}</button>
            <a class="btn btn-ghost" routerLink="/pfe/sujets">Annuler</a>
          </div>
        </form>

        <aside class="stack">
          <div class="card ai">
            <div class="card-head"><h2>✦ Assistant IA</h2></div>
            <p class="text-2 small">Rédigez un titre et une première description, puis laissez l’assistant les enrichir.</p>
            <div class="row mt">
              <button class="btn btn-ai btn-sm" (click)="improve()" [disabled]="aiBusy() || !model.title.trim()">Améliorer la description</button>
              <button class="btn btn-ghost btn-sm" (click)="suggest()" [disabled]="aiBusy() || !model.title.trim()">Suggérer un encadrant</button>
            </div>
            @if (aiBusy()) { <div class="spinner"></div> }

            @if (suggestion(); as s) {
              <div class="block">
                <div class="row between"><strong>Proposition</strong><small class="muted">{{ s.source === 'ia' ? 'modèle IA' : 'modèle local' }}</small></div>
                <p class="pre small mt">{{ s.improvedDescription }}</p>
                @if (s.objectives.length) { <strong class="small">Objectifs</strong><ul>@for (o of s.objectives; track o) { <li>{{ o }}</li> }</ul> }
                @if (s.deliverables.length) { <strong class="small">Livrables</strong><ul>@for (d of s.deliverables; track d) { <li>{{ d }}</li> }</ul> }
                <div>@for (k of s.keywords; track k) { <span class="chip">{{ k }}</span> }</div>
                <button class="btn btn-primary btn-sm mt" (click)="useSuggestion(s)">Utiliser cette proposition</button>
              </div>
            }

            @if (supervisors().length) {
              <div class="block">
                <strong>Encadrants recommandés</strong>
                @for (sv of supervisors(); track sv.teacher.id) {
                  <div class="sv">
                    <div class="row between"><strong>{{ sv.teacher.fullName }}</strong><span class="badge blue">{{ (sv.score * 100).toFixed(0) }} %</span></div>
                    <small class="muted">{{ sv.reason }}</small>
                    <div>@for (k of sv.matchedKeywords; track k) { <span class="chip">{{ k }}</span> }</div>
                    @if (auth.isManager()) { <button class="btn-link small" (click)="model.supervisorId = sv.teacher.id">Choisir comme encadrant</button> }
                  </div>
                }
              </div>
            }
          </div>
        </aside>
      </div>
    </div>
  `,
  styles: [`
    .layout { grid-template-columns: minmax(0, 1.7fr) minmax(280px, 1fr); align-items: start; }
    .ai { border-color: #dcd0f5; background: linear-gradient(180deg, #faf7ff, #fff 160px); }
    .block { margin-top: 16px; padding-top: 14px; border-top: 1px dashed #d9cdef; }
    .block ul { margin: 4px 0 10px; padding-left: 18px; font-size: 13.5px; }
    .sv { padding: 10px 0; border-bottom: 1px solid #f0edf7; }
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
  `]
})
export class SubjectForm implements OnInit {
  readonly id = input<string>();
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly typeLabel = SUBJECT_TYPE;
  readonly types = Object.keys(SUBJECT_TYPE) as SubjectType[];
  readonly filieres = signal<Filiere[]>([]);
  readonly teachers = signal<User[]>([]);
  readonly busy = signal(false);
  readonly aiBusy = signal(false);
  readonly error = signal('');
  readonly suggestion = signal<SubjectSuggestion | null>(null);
  readonly supervisors = signal<SupervisorSuggestion[]>([]);

  model: SubjectRequest & { keywords: string } = {
    title: '', description: '', keywords: '', type: 'ACADEMIQUE', companyName: '', maxStudents: 1, filiereId: null, supervisorId: null
  };

  ngOnInit(): void {
    this.api.filieres().subscribe(f => {
      this.filieres.set(f);
      if (!this.model.filiereId && f.length) this.model.filiereId = f[0].id;
    });
    if (this.auth.isManager()) this.api.users('ENSEIGNANT').subscribe({ next: t => this.teachers.set(t), error: () => {} });
    if (this.id()) {
      this.api.subject(+this.id()!).subscribe({
        next: s => this.model = {
          title: s.title, description: s.description, keywords: s.keywords.join(', '), type: s.type, companyName: s.companyName ?? '',
          maxStudents: s.maxStudents, filiereId: s.filiere.id, supervisorId: s.supervisor.id
        },
        error: err => this.error.set(apiError(err))
      });
    }
  }

  keywordList(): string[] {
    return this.model.keywords.split(',').map(k => k.trim()).filter(Boolean);
  }

  save(): void {
    this.busy.set(true);
    const body: SubjectRequest = { ...this.model, companyName: this.model.type === 'ENTREPRISE' ? this.model.companyName : '' };
    const call = this.id() ? this.api.updateSubject(+this.id()!, body) : this.api.createSubject(body);
    call.subscribe({
      next: s => this.router.navigate(['/pfe/sujets', s.id]),
      error: err => { this.error.set(apiError(err)); this.busy.set(false); }
    });
  }

  improve(): void {
    this.aiBusy.set(true);
    this.api.improveSubject(this.model.title, this.model.description).subscribe({
      next: s => { this.suggestion.set(s); this.aiBusy.set(false); },
      error: err => { this.error.set(apiError(err)); this.aiBusy.set(false); }
    });
  }

  suggest(): void {
    this.aiBusy.set(true);
    this.api.suggestSupervisors(this.model.title, this.model.description).subscribe({
      next: s => { this.supervisors.set(s); this.aiBusy.set(false); },
      error: err => { this.error.set(apiError(err)); this.aiBusy.set(false); }
    });
  }

  useSuggestion(s: SubjectSuggestion): void {
    const parts = [s.improvedDescription];
    if (s.objectives.length) parts.push('Objectifs :\n' + s.objectives.map(o => '- ' + o).join('\n'));
    if (s.deliverables.length) parts.push('Livrables attendus :\n' + s.deliverables.map(d => '- ' + d).join('\n'));
    this.model.description = parts.join('\n\n');
    const merged = new Set([...this.keywordList(), ...s.keywords]);
    this.model.keywords = [...merged].join(', ');
  }
}
