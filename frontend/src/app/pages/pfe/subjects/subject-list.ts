import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { AuthService } from '../../../core/auth.service';
import { AutoAssignResult, Filiere, Subject, SubjectStatus, SubjectType } from '../../../core/models';
import { SUBJECT_STATUS, SUBJECT_TYPE, apiError } from '../../../core/labels';
import { Badge } from '../../../shared/badge';

@Component({
  selector: 'app-subject-list',
  imports: [FormsModule, RouterLink, Badge],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <span class="eyebrow">Projets de fin d’études</span>
          <h1>Sujets de PFE</h1>
          <p>{{ auth.isStudent() ? 'Choisissez jusqu’à 3 sujets et classez-les par ordre de préférence.' : 'Proposition, validation et suivi des sujets.' }}</p>
        </div>
        <div class="row">
          @if (auth.isManager()) {
            <button class="btn btn-ai" (click)="autoAssign()" [disabled]="busy()">⚡ Affectation automatique</button>
          }
          @if (!auth.isStudent()) { <a class="btn btn-primary" routerLink="/pfe/sujets/nouveau">✚ Proposer un sujet</a> }
        </div>
      </div>

      @if (error()) { <div class="alert error" style="margin-bottom:16px">{{ error() }}</div> }

      <div class="toolbar">
        <input class="input search" placeholder="Rechercher un titre, un mot-clé, un encadrant…" [(ngModel)]="q" (keyup.enter)="load()" />
        @if (!auth.isStudent()) {
          <select class="input" [(ngModel)]="status" (change)="load()">
            <option value="">Tous les statuts</option>
            @for (s of statuses; track s) { <option [value]="s">{{ statusLabel[s][0] }}</option> }
          </select>
        }
        <select class="input" [(ngModel)]="type" (change)="load()">
          <option value="">Tous les types</option>
          @for (t of types; track t) { <option [value]="t">{{ typeLabel[t] }}</option> }
        </select>
        <select class="input" [(ngModel)]="filiereId" (change)="load()">
          <option [ngValue]="null">Toutes les filières</option>
          @for (f of filieres(); track f.id) { <option [ngValue]="f.id">{{ f.code }}</option> }
        </select>
        @if (auth.isTeacher()) {
          <label class="check"><input type="checkbox" [(ngModel)]="mine" (change)="load()" /> Mes sujets</label>
        }
        <button class="btn btn-ghost" (click)="load()">Rechercher</button>
      </div>

      @if (loading()) { <div class="spinner"></div> }
      @else {
        <p class="muted small" style="margin-bottom:12px">{{ subjects().length }} sujet(s)</p>
        <div class="grid grid-3">
          @for (s of subjects(); track s.id) {
            <a class="card link-card subject" [routerLink]="['/pfe/sujets', s.id]">
              <div class="row between">
                <span class="badge violet">{{ typeLabel[s.type] }}</span>
                <app-badge [label]="statusLabel[s.status]" />
              </div>
              <h3 class="mt">{{ s.title }}</h3>
              <p class="desc text-2">{{ s.description }}</p>
              <div class="kw">@for (k of s.keywords.slice(0, 4); track k) { <span class="chip">{{ k }}</span> }</div>
              <div class="foot row between">
                <small class="muted">{{ s.supervisor.fullName }} · {{ s.filiere.code }}</small>
                <small class="nowrap"><strong>{{ s.acceptedStudents }}/{{ s.maxStudents }}</strong> place(s)
                  @if (!auth.isStudent() && s.pendingApplications) { · <span class="badge amber">{{ s.pendingApplications }} candidat(s)</span> }</small>
              </div>
            </a>
          } @empty {
            <div class="card empty span-all"><div class="big">📚</div>Aucun sujet ne correspond à ces critères.</div>
          }
        </div>
      }

      @if (result(); as r) {
        <div class="modal-backdrop" (click)="result.set(null)">
          <div class="modal" (click)="$event.stopPropagation()">
            <h2>Affectation automatique</h2>
            <p class="mt text-2">Les candidatures en attente ont été traitées par ordre de préférence, puis par date de dépôt.</p>
            <div class="alert success mt">{{ r.assigned }} étudiant(s) affecté(s). Chacun a reçu une notification et un e-mail.</div>
            @if (r.details.length) {
              <ul class="mt">@for (d of r.details; track $index) { <li>{{ d }}</li> }</ul>
            }
            @if (r.studentsWithoutSubject.length) {
              <div class="alert warn mt"><strong>Toujours sans sujet :</strong>
                {{ studentNames(r) }}</div>
            }
            <div class="actions"><button class="btn btn-primary" (click)="result.set(null)">Fermer</button></div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .subject { display: flex; flex-direction: column; color: inherit; text-decoration: none !important; }
    .desc { margin-top: 8px; font-size: 13.5px; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
    .kw { margin-top: 12px; }
    .foot { margin-top: auto; padding-top: 12px; border-top: 1px solid #eef1f6; }
    .span-all { grid-column: 1 / -1; }
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
  `]
})
export class SubjectList implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);

  readonly statusLabel = SUBJECT_STATUS;
  readonly typeLabel = SUBJECT_TYPE;
  readonly statuses = Object.keys(SUBJECT_STATUS) as SubjectStatus[];
  readonly types = Object.keys(SUBJECT_TYPE) as SubjectType[];

  readonly subjects = signal<Subject[]>([]);
  readonly filieres = signal<Filiere[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly result = signal<AutoAssignResult | null>(null);
  readonly hasProposed = computed(() => this.subjects().some(s => s.status === 'PROPOSED'));

  q = '';
  status: SubjectStatus | '' = '';
  type: SubjectType | '' = '';
  filiereId: number | null = null;
  mine = false;

  ngOnInit(): void {
    this.mine = this.auth.role() === 'ENSEIGNANT';
    this.api.filieres().subscribe({ next: f => this.filieres.set(f), error: () => {} });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.subjects({ q: this.q.trim(), status: this.status, type: this.type, filiereId: this.filiereId, mine: this.mine || undefined }).subscribe({
      next: list => { this.subjects.set(list); this.loading.set(false); this.error.set(''); },
      error: err => { this.error.set(apiError(err)); this.loading.set(false); }
    });
  }

  autoAssign(): void {
    if (!confirm('Affecter automatiquement les étudiants à partir de leurs candidatures en attente ?')) return;
    this.busy.set(true);
    this.api.autoAssign().subscribe({
      next: r => { this.result.set(r); this.busy.set(false); this.load(); },
      error: err => { this.error.set(apiError(err)); this.busy.set(false); }
    });
  }

  studentNames(r: AutoAssignResult): string {
    return r.studentsWithoutSubject.map(s => s.fullName).join(', ');
  }
}
