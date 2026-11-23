import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgTemplateOutlet } from '@angular/common';
import { forkJoin, of, catchError } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Application, Convention, Defense, Milestone, PfeStats, Project, Subject } from '../../core/models';
import { APPLICATION_STATUS, MILESTONE_STATUS, PROJECT_STATUS, SUBJECT_STATUS, apiError, daysUntil, frDate, hhmm, today } from '../../core/labels';
import { Badge } from '../../shared/badge';
import { DashboardExtras } from './extras';

interface ReviewItem { project: Project; milestone: Milestone; }

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, Badge, NgTemplateOutlet, DashboardExtras],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <span class="eyebrow">{{ todayLabel }}</span>
          <h1>Bonjour {{ auth.user()?.firstName }} 👋</h1>
          <p>{{ subtitle() }}</p>
        </div>
        @if (auth.isManager()) {
          <div class="row">
            <button class="btn btn-ghost" (click)="runReminders()" [disabled]="busy()">Envoyer les rappels</button>
            <a class="btn btn-primary" routerLink="/pfe/sujets">Gérer les sujets</a>
          </div>
        }
      </div>

      @if (message()) { <div class="alert success" style="margin-bottom:16px">{{ message() }}</div> }
      @if (error()) { <div class="alert error" style="margin-bottom:16px">{{ error() }}</div> }
      <app-dashboard-extras />
      @if (loading()) { <div class="spinner"></div> }

      <!-- ===================== chef / admin ===================== -->
      @if (!loading() && auth.isManager() && stats(); as s) {
        <div class="grid grid-4">
          <div class="card stat"><div class="icon bg-blue">📚</div><div><div class="value">{{ s.subjectsByStatus['PUBLISHED'] ?? 0 }}</div><div class="label">sujets publiés</div></div></div>
          <div class="card stat"><div class="icon bg-amber">⏳</div><div><div class="value">{{ s.subjectsByStatus['PROPOSED'] ?? 0 }}</div><div class="label">sujets à valider</div></div></div>
          <div class="card stat"><div class="icon bg-teal">🎯</div><div><div class="value">{{ s.studentsWithProject }}/{{ s.students }}</div><div class="label">étudiants affectés</div></div></div>
          <div class="card stat"><div class="icon bg-red">⚠</div><div><div class="value">{{ s.lateMilestones }}</div><div class="label">jalons en retard</div></div></div>
        </div>
        <div class="grid grid-3 mt">
          <div class="card">
            <div class="card-head"><h2>Affectation</h2></div>
            <div class="progress"><span [style.width.%]="s.students ? s.studentsWithProject * 100 / s.students : 0"></span></div>
            <p class="mt text-2">{{ s.studentsWithoutProject }} étudiant(s) sans sujet · {{ s.pendingApplications }} candidature(s) en attente.</p>
            <a class="btn btn-ghost btn-sm mt" routerLink="/pfe/sujets">Lancer l’affectation automatique</a>
          </div>
          <div class="card">
            <div class="card-head"><h2>Projets</h2></div>
            <div class="stack">
              @for (k of projectKeys; track k) {
                <div class="row between"><app-badge [label]="projectStatus[k]" /><strong>{{ s.projectsByStatus[k] ?? 0 }}</strong></div>
              }
              <div class="row between"><span class="text-2">Jalons à relire</span><strong>{{ s.submittedMilestones }}</strong></div>
            </div>
          </div>
          <div class="card">
            <div class="card-head"><h2>Soutenances</h2></div>
            <div class="stack">
              <div class="row between"><span class="text-2">Programmées</span><strong>{{ s.defensesScheduled }}</strong></div>
              <div class="row between"><span class="text-2">Terminées</span><strong>{{ s.defensesDone }}</strong></div>
              <div class="row between"><span class="text-2">Moyenne</span><strong>{{ s.averageGrade != null ? s.averageGrade.toFixed(2) + ' / 20' : '—' }}</strong></div>
              <div class="row between"><span class="text-2">Conventions à valider</span>
                <a routerLink="/pfe/conventions"><strong>{{ s.conventionsToReview }}</strong></a></div>
            </div>
          </div>
        </div>

        <div class="grid grid-2 mt">
          <div class="card">
            <div class="card-head"><h2>Sujets à valider</h2><a routerLink="/pfe/sujets" class="small">Tout voir</a></div>
            @for (sub of pendingSubjects(); track sub.id) {
              <a class="line" [routerLink]="['/pfe/sujets', sub.id]">
                <span><strong>{{ sub.title }}</strong><br /><small class="muted">{{ sub.supervisor.fullName }} · {{ sub.filiere.code }}</small></span>
                <app-badge [label]="subjectStatus[sub.status]" />
              </a>
            } @empty { <div class="empty">Aucun sujet en attente 🎉</div> }
          </div>
          <ng-container *ngTemplateOutlet="defenseCard" />
        </div>
      }

      <!-- ===================== enseignant ===================== -->
      @if (!loading() && auth.role() === 'ENSEIGNANT') {
        <div class="grid grid-4">
          <div class="card stat"><div class="icon bg-blue">📚</div><div><div class="value">{{ subjects().length }}</div><div class="label">sujets proposés</div></div></div>
          <div class="card stat"><div class="icon bg-teal">🎯</div><div><div class="value">{{ projects().length }}</div><div class="label">projets encadrés</div></div></div>
          <div class="card stat"><div class="icon bg-violet">📝</div><div><div class="value">{{ toReview().length }}</div><div class="label">livrables à relire</div></div></div>
          <div class="card stat"><div class="icon bg-amber">✉</div><div><div class="value">{{ pendingApplicationsCount() }}</div><div class="label">candidatures en attente</div></div></div>
        </div>
        <div class="grid grid-2 mt">
          <div class="card">
            <div class="card-head"><h2>Livrables à relire</h2></div>
            @for (r of toReview(); track r.milestone.id) {
              <a class="line" [routerLink]="['/pfe/projets', r.project.id]" [fragment]="'jalon-' + r.milestone.id">
                <span><strong>{{ r.milestone.title }}</strong><br /><small class="muted">{{ r.project.subjectTitle }} · {{ names(r.project) }}</small></span>
                <app-badge [label]="milestoneStatus[r.milestone.status]" />
              </a>
            } @empty { <div class="empty">Rien à relire pour le moment.</div> }
          </div>
          <div class="card">
            <div class="card-head"><h2>Mes projets</h2><a routerLink="/pfe/projets" class="small">Tout voir</a></div>
            @for (p of projects(); track p.id) {
              <a class="line" [routerLink]="['/pfe/projets', p.id]">
                <span class="grow"><strong>{{ p.subjectTitle }}</strong><br /><small class="muted">{{ names(p) }}</small>
                  <div class="progress mt-sm"><span [style.width.%]="p.progress"></span></div></span>
                <span class="small nowrap">{{ p.progress }} %</span>
              </a>
            } @empty { <div class="empty">Aucun projet encadré.</div> }
          </div>
        </div>
        <div class="grid grid-2 mt">
          <div class="card">
            <div class="card-head"><h2>Mes sujets</h2><a routerLink="/pfe/sujets/nouveau" class="btn btn-primary btn-sm">Proposer un sujet</a></div>
            @for (sub of subjects(); track sub.id) {
              <a class="line" [routerLink]="['/pfe/sujets', sub.id]">
                <span><strong>{{ sub.title }}</strong><br /><small class="muted">{{ sub.pendingApplications }} candidature(s) · {{ sub.acceptedStudents }}/{{ sub.maxStudents }} place(s)</small></span>
                <app-badge [label]="subjectStatus[sub.status]" />
              </a>
            } @empty { <div class="empty">Vous n’avez pas encore proposé de sujet.</div> }
          </div>
          <ng-container *ngTemplateOutlet="defenseCard" />
        </div>
      }

      <!-- ===================== étudiant ===================== -->
      @if (!loading() && auth.isStudent()) {
        @if (myProject(); as p) {
          <div class="card hero-card">
            <div class="row between">
              <div>
                <span class="eyebrow">Mon projet de fin d’études</span>
                <h2>{{ p.subjectTitle }}</h2>
                <p class="text-2">Encadrant : {{ p.supervisor.fullName }}@if (p.companyName) { · {{ p.companyName }} }</p>
              </div>
              <app-badge [label]="projectStatus[p.status]" />
            </div>
            <div class="row mt"><div class="progress grow"><span [style.width.%]="p.progress"></span></div><strong>{{ p.progress }} %</strong></div>
            <a class="btn btn-primary btn-sm mt" routerLink="/pfe/mon-projet">Ouvrir mon projet</a>
          </div>
          <div class="grid grid-2 mt">
            <div class="card">
              <div class="card-head"><h2>Prochaine échéance</h2></div>
              @if (nextMilestone(); as m) {
                <h3>{{ m.title }}</h3>
                <p class="mt text-2">À rendre le {{ date(m.dueDate) }}</p>
                <p class="mt"><span class="badge" [class]="'badge ' + (m.late ? 'red' : due(m.dueDate) <= 3 ? 'amber' : 'blue')">
                  {{ m.late ? 'En retard de ' + (-due(m.dueDate)) + ' jour(s)' : due(m.dueDate) === 0 ? 'Aujourd’hui' : 'Dans ' + due(m.dueDate) + ' jour(s)' }}</span>
                  <app-badge [label]="milestoneStatus[m.status]" /></p>
              } @else { <div class="empty">Tous les jalons sont rendus.</div> }
            </div>
            <div class="card">
              <div class="card-head"><h2>Soutenance</h2></div>
              @if (p.defense; as d) {
                <h3>{{ date(d.defenseDate) }} · {{ time(d.startTime) }}–{{ time(d.endTime) }}</h3>
                <p class="mt text-2">Salle {{ d.room }}</p>
                @if (d.grade != null) { <p class="mt"><strong>{{ d.grade }} / 20</strong> · mention {{ d.mention }}</p> }
              } @else { <div class="empty">Pas encore programmée.</div> }
            </div>
          </div>
        } @else {
          <div class="grid grid-3">
            <div class="card stat"><div class="icon bg-blue">📚</div><div><div class="value">{{ publishedCount() }}</div><div class="label">sujets ouverts</div></div></div>
            <div class="card stat"><div class="icon bg-amber">✉</div><div><div class="value">{{ applications().length }}/3</div><div class="label">candidatures envoyées</div></div></div>
            <div class="card stat"><div class="icon bg-violet">✦</div><div><div class="value">IA</div><div class="label">demandez à l’assistant quel sujet choisir</div></div></div>
          </div>
          <div class="card mt">
            <div class="card-head"><h2>Mes candidatures</h2><a routerLink="/pfe/sujets" class="btn btn-primary btn-sm">Parcourir les sujets</a></div>
            @for (a of applications(); track a.id) {
              <a class="line" [routerLink]="['/pfe/sujets', a.subject.id]">
                <span><strong>Choix n° {{ a.preferenceRank }} · {{ a.subject.title }}</strong><br /><small class="muted">{{ a.subject.supervisorName }}</small></span>
                <app-badge [label]="applicationStatus[a.status]" />
              </a>
            } @empty { <div class="empty">Vous n’avez pas encore candidaté. Vous pouvez classer jusqu’à 3 sujets.</div> }
          </div>
        }
      }

      <ng-template #defenseCard>
        <div class="card">
          <div class="card-head"><h2>Prochaines soutenances</h2><a routerLink="/pfe/soutenances" class="small">Calendrier</a></div>
          @for (d of defenses(); track d.id) {
            <a class="line" routerLink="/pfe/soutenances" [fragment]="'soutenance-' + d.id">
              <span class="date-box"><strong>{{ day(d.defenseDate) }}</strong><small>{{ month(d.defenseDate) }}</small></span>
              <span class="grow"><strong>{{ d.subjectTitle }}</strong><br /><small class="muted">{{ time(d.startTime) }} · salle {{ d.room }}</small></span>
            </a>
          } @empty { <div class="empty">Aucune soutenance à venir.</div> }
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    .line { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 11px 0; border-bottom: 1px solid #eef1f6; color: inherit; text-decoration: none !important; }
    .line:last-child { border-bottom: 0; }
    .line:hover strong { color: var(--primary-2); }
    .grow { flex: 1; min-width: 0; }
    .mt-sm { margin-top: 6px; }
    .hero-card { background: linear-gradient(135deg, #fff, #f1f5ff); }
    .eyebrow { display: block; margin-bottom: 4px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; }
    .date-box { width: 50px; text-align: center; border-radius: 10px; background: var(--primary-soft); color: var(--primary-2); padding: 6px 0; line-height: 1.1; flex: none; }
    .date-box small { display: block; font-size: 11px; text-transform: uppercase; }
  `]
})
export class Dashboard implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);

  readonly subjectStatus = SUBJECT_STATUS;
  readonly projectStatus = PROJECT_STATUS;
  readonly milestoneStatus = MILESTONE_STATUS;
  readonly applicationStatus = APPLICATION_STATUS;
  readonly projectKeys = ['IN_PROGRESS', 'READY_FOR_DEFENSE', 'DEFENDED'] as const;
  readonly todayLabel = frDate(today(), true);

  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly message = signal('');

  readonly stats = signal<PfeStats | null>(null);
  readonly subjects = signal<Subject[]>([]);
  readonly projects = signal<Project[]>([]);
  readonly defenses = signal<Defense[]>([]);
  readonly applications = signal<Application[]>([]);
  readonly myProject = signal<Project | null>(null);
  readonly conventions = signal<Convention[]>([]);

  readonly pendingSubjects = computed(() => this.subjects().filter(s => s.status === 'PROPOSED').slice(0, 6));
  readonly publishedCount = computed(() => this.subjects().filter(s => s.status === 'PUBLISHED').length);
  readonly pendingApplicationsCount = computed(() => this.subjects().reduce((n, s) => n + s.pendingApplications, 0));
  readonly toReview = computed<ReviewItem[]>(() => this.projects().flatMap(p =>
    p.milestones.filter(m => m.status === 'SUBMITTED').map(m => ({ project: p, milestone: m }))));
  readonly nextMilestone = computed(() => this.myProject()?.milestones.find(m => m.status !== 'VALIDATED' && m.status !== 'SUBMITTED') ?? null);
  readonly subtitle = computed(() => {
    switch (this.auth.role()) {
      case 'CHEF_DEPARTEMENT': case 'ADMIN': return 'Vue d’ensemble des projets de fin d’études du département.';
      case 'ENSEIGNANT': return 'Vos sujets, vos étudiants et les livrables qui attendent votre avis.';
      default: return 'Suivez votre PFE : candidatures, jalons et soutenance.';
    }
  });

  ngOnInit(): void {
    const safe = <T>(o: any, fallback: T) => o.pipe(catchError(() => of(fallback)));
    const upcoming = { from: today(), mine: !this.auth.isManager() };
    const role = this.auth.role();
    let jobs: Record<string, any>;
    if (role === 'ADMIN' || role === 'CHEF_DEPARTEMENT') {
      jobs = { stats: this.api.stats(), subjects: this.api.subjects({ status: 'PROPOSED' }), defenses: safe(this.api.defenses(upcoming), []) };
    } else if (role === 'ENSEIGNANT') {
      jobs = { subjects: this.api.subjects({ mine: true }), projects: this.api.projects(), defenses: safe(this.api.defenses(upcoming), []) };
    } else {
      jobs = { project: safe(this.api.myProject(), null), applications: this.api.myApplications(), subjects: safe(this.api.subjects({ status: 'PUBLISHED' }), []) };
    }
    forkJoin(jobs).subscribe({
      next: (r: any) => {
        if (r.stats) this.stats.set(r.stats);
        if (r.subjects) this.subjects.set(r.subjects);
        if (r.projects) this.projects.set(r.projects);
        if (r.defenses) this.defenses.set(r.defenses.filter((d: Defense) => d.status === 'SCHEDULED').slice(0, 5));
        if (r.applications) this.applications.set(r.applications.filter((a: Application) => a.status !== 'WITHDRAWN'));
        if (r.project !== undefined) this.myProject.set(r.project);
        this.loading.set(false);
      },
      error: err => { this.error.set(apiError(err)); this.loading.set(false); }
    });
  }

  runReminders(): void {
    this.busy.set(true);
    this.api.runReminders().subscribe({
      next: r => { this.message.set(`${r.sent} rappel(s) envoyé(s) (application + e-mail).`); this.busy.set(false); },
      error: err => { this.error.set(apiError(err)); this.busy.set(false); }
    });
  }

  names(p: Project): string { return p.students.map(s => s.fullName).join(', '); }
  date = (d: string) => frDate(d);
  time = hhmm;
  due = daysUntil;
  day(d: string): string { return d.substring(8, 10); }
  month(d: string): string {
    return new Date(Number(d.substring(0, 4)), Number(d.substring(5, 7)) - 1, 1).toLocaleDateString('fr-FR', { month: 'short' });
  }
}
