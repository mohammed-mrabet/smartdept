import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiService } from '../../../core/api.service';
import { AuthService } from '../../../core/auth.service';
import { Milestone, MilestoneStatus, Project } from '../../../core/models';
import { DEFENSE_STATUS, MILESTONE_STATUS, PROJECT_STATUS, SUBJECT_TYPE, apiError, daysUntil, frDate, hhmm, today } from '../../../core/labels';
import { Badge } from '../../../shared/badge';

type Dialog = { kind: 'submit' | 'review'; milestone: Milestone } | { kind: 'add' } | { kind: 'external' } | null;

@Component({
  selector: 'app-project-detail',
  imports: [FormsModule, RouterLink, Badge],
  template: `
    <div class="page">
      @if (!mine()) { <a routerLink="/pfe/projets" class="small">← Projets</a> }
      @if (error()) { <div class="alert error mt">{{ error() }}</div> }
      @if (success()) { <div class="alert success mt">{{ success() }}</div> }
      @if (loading()) { <div class="spinner"></div> }

      @if (!loading() && !project() && mine()) {
        <div class="card empty mt"><div class="big">🎯</div>
          <p>Vous n’avez pas encore de projet. Il est créé dès qu’une de vos candidatures est acceptée.</p>
          <a class="btn btn-primary mt" routerLink="/pfe/sujets">Choisir un sujet</a></div>
      }

      @if (project(); as p) {
        <div class="page-head mt">
          <div>
            <div class="row"><span class="badge violet">{{ typeLabel[p.subjectType] }}</span><app-badge [label]="projectStatus[p.status]" /></div>
            <h1 class="mt">{{ p.subjectTitle }}</h1>
            <p>{{ names(p) }} · encadré par <strong>{{ p.supervisor.fullName }}</strong></p>
          </div>
          <div class="row">
            <a class="btn btn-ghost" [routerLink]="['/pfe/sujets', p.subjectId]">Voir le sujet</a>
            @if (canManage() && p.status === 'IN_PROGRESS') {
              <button class="btn btn-ghost" (click)="openAdd()">✚ Jalon</button>
              <button class="btn btn-success" (click)="markReady()" [disabled]="!allValidated()" [title]="allValidated() ? '' : 'Tous les jalons doivent être validés'">Prêt à soutenir</button>
            }
          </div>
        </div>

        <div class="grid layout">
          <div class="card">
            <div class="card-head">
              <h2>Jalons</h2>
              <div class="row"><div class="progress" style="width:160px"><span [style.width.%]="p.progress"></span></div><strong>{{ p.progress }} %</strong></div>
            </div>
            <div class="timeline">
              @for (m of p.milestones; track m.id) {
                <div class="tl-item" [id]="'jalon-' + m.id">
                  <span class="tl-dot" [class]="'tl-dot ' + m.status + (m.late ? ' late' : '')"></span>
                  <div class="row between">
                    <h3>{{ m.title }}</h3>
                    <div class="row"><app-badge [label]="milestoneStatus[m.status]" />
                      @if (m.late) { <span class="badge red">En retard</span> }</div>
                  </div>
                  <small class="muted">Échéance : {{ date(m.dueDate) }} · {{ dueText(m) }}</small>
                  @if (m.description) { <p class="text-2 small mt-xs">{{ m.description }}</p> }
                  @if (m.deliverableUrl) {
                    <div class="deliv">📎 <a [href]="m.deliverableUrl" target="_blank" rel="noopener">{{ m.deliverableUrl }}</a>
                      @if (m.submissionComment) { <div class="small text-2">« {{ m.submissionComment }} »</div> }
                      <small class="muted block">Rendu le {{ date(m.submittedAt!) }}</small></div>
                  }
                  @if (m.feedback || m.grade != null) {
                    <div class="feedback"><strong class="small">Retour de l’encadrant</strong>@if (m.grade != null) { <span class="badge teal">{{ m.grade }} / 20</span> }
                      @if (m.feedback) { <p class="small pre">{{ m.feedback }}</p> }</div>
                  }
                  <div class="row mt-xs">
                    @if (isMember() && (m.status === 'TODO' || m.status === 'NEEDS_REVISION')) {
                      <button class="btn btn-primary btn-sm" (click)="openSubmit(m)">{{ m.status === 'NEEDS_REVISION' ? 'Rendre la version corrigée' : 'Rendre le livrable' }}</button>
                    }
                    @if (canManage() && m.status === 'SUBMITTED') {
                      <button class="btn btn-primary btn-sm" (click)="openReview(m)">Évaluer</button>
                    }
                    @if (canManage() && m.status === 'TODO') {
                      <button class="btn-link small danger" (click)="deleteMilestone(m)">Supprimer</button>
                    }
                  </div>
                </div>
              } @empty { <div class="empty">Aucun jalon.</div> }
            </div>
          </div>

          <div class="stack">
            <div class="card">
              <h3>Équipe</h3>
              <ul class="people">
                @for (s of p.students; track s.id) { <li><span class="av">{{ s.fullName[0] }}</span><span><strong>{{ s.fullName }}</strong><br /><a class="small" [href]="'mailto:' + s.email">{{ s.email }}</a></span></li> }
                <li><span class="av t">{{ p.supervisor.fullName[0] }}</span><span><strong>{{ p.supervisor.fullName }}</strong>&nbsp;<small class="muted">encadrant</small><br /><a class="small" [href]="'mailto:' + p.supervisor.email">{{ p.supervisor.email }}</a></span></li>
                @if (p.externalSupervisorName) {
                  <li><span class="av e">{{ p.externalSupervisorName[0] }}</span><span><strong>{{ p.externalSupervisorName }}</strong>&nbsp;<small class="muted">tuteur entreprise</small><br /><small>{{ p.externalSupervisorEmail }}</small></span></li>
                }
              </ul>
              @if (canManage()) { <button class="btn-link small" (click)="openExternal()">{{ p.externalSupervisorName ? 'Modifier' : 'Ajouter' }} le tuteur en entreprise</button> }
            </div>
            <div class="card">
              <h3>Soutenance</h3>
              @if (p.defense; as d) {
                <p class="mt"><strong>{{ longDate(d.defenseDate) }}</strong></p>
                <p class="text-2">{{ time(d.startTime) }} – {{ time(d.endTime) }} · salle {{ d.room }}</p>
                <p class="mt"><app-badge [label]="defenseStatus[d.status]" /></p>
                @if (d.grade != null) { <p class="mt grade">{{ d.grade }} / 20 <small class="muted">mention {{ d.mention }}</small></p> }
              } @else {
                <p class="mt text-2 small">{{ p.status === 'READY_FOR_DEFENSE' ? 'Le chef de département va programmer la soutenance.' : 'Elle sera programmée lorsque tous les jalons seront validés.' }}</p>
              }
            </div>
          </div>
        </div>
      }

      @if (dialog(); as d) {
        <div class="modal-backdrop" (click)="dialog.set(null)">
          <form class="modal" (click)="$event.stopPropagation()" (ngSubmit)="confirmDialog()">
            @switch (d.kind) {
              @case ('submit') {
                <h2>Rendre « {{ $any(d).milestone.title }} »</h2>
                <div class="field mt"><label>Lien du livrable *</label>
                  <input name="url" [(ngModel)]="form.url" required placeholder="https://github.com/… ou lien Drive" /></div>
                <div class="field mt"><label>Commentaire</label><textarea name="comment" [(ngModel)]="form.comment"></textarea></div>
              }
              @case ('review') {
                <h2>Évaluer « {{ $any(d).milestone.title }} »</h2>
                <p class="mt"><a [href]="$any(d).milestone.deliverableUrl" target="_blank" rel="noopener">Ouvrir le livrable ↗</a></p>
                <div class="field mt"><label>Décision</label>
                  <div class="row">
                    <label class="check"><input type="radio" name="decision" value="VALIDATED" [(ngModel)]="form.decision" /> Valider</label>
                    <label class="check"><input type="radio" name="decision" value="NEEDS_REVISION" [(ngModel)]="form.decision" /> Demander une correction</label>
                  </div></div>
                <div class="field mt"><label>Retour pour l’étudiant {{ form.decision === 'NEEDS_REVISION' ? '*' : '' }}</label>
                  <textarea name="feedback" [(ngModel)]="form.feedback" [required]="form.decision === 'NEEDS_REVISION'"></textarea></div>
                <div class="field mt"><label>Note (facultative, sur 20)</label>
                  <input name="grade" type="number" min="0" max="20" step="0.25" [(ngModel)]="form.grade" /></div>
              }
              @case ('add') {
                <h2>Nouveau jalon</h2>
                <div class="field mt"><label>Titre *</label><input name="title" [(ngModel)]="form.title" required /></div>
                <div class="field mt"><label>Échéance *</label><input name="due" type="date" [min]="todayIso" [(ngModel)]="form.due" required /></div>
                <div class="field mt"><label>Description</label><textarea name="desc" [(ngModel)]="form.comment"></textarea></div>
              }
              @case ('external') {
                <h2>Tuteur en entreprise</h2>
                <div class="field mt"><label>Nom *</label><input name="name" [(ngModel)]="form.title" required /></div>
                <div class="field mt"><label>E-mail *</label><input name="email" type="email" [(ngModel)]="form.url" required /></div>
              }
            }
            <div class="actions">
              <button type="button" class="btn btn-ghost" (click)="dialog.set(null)">Annuler</button>
              <button class="btn btn-primary" [disabled]="busy()">Valider</button>
            </div>
          </form>
        </div>
      }
    </div>
  `,
  styles: [`
    .layout { grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr); align-items: start; }
    .tl-item { scroll-margin-top: 90px; }
    .tl-item:target { animation: flash 2s; }
    @keyframes flash { 0% { background: #fff6d6; } 100% { background: transparent; } }
    .mt-xs { margin-top: 8px; }
    .deliv, .feedback { margin-top: 10px; padding: 10px 12px; border-radius: 9px; background: var(--surface-2); border: 1px solid var(--border); font-size: 13.5px; word-break: break-all; }
    .feedback { background: var(--teal-soft); border-color: #c4eae2; word-break: normal; }
    .people { list-style: none; padding: 0; margin: 12px 0; display: grid; gap: 12px; }
    .people li { display: flex; gap: 10px; align-items: center; }
    .av { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; background: var(--primary-soft); color: var(--primary-2); font-weight: 800; flex: none; }
    .av.t { background: var(--teal-soft); color: var(--teal); } .av.e { background: var(--amber-soft); color: var(--amber); }
    .block { display: block; margin-top: 2px; }
    .grade { font-size: 22px; font-weight: 800; }
    .danger { color: var(--red); }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
  `]
})
export class ProjectDetail implements OnInit {
  readonly id = input<string>();
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  readonly typeLabel = SUBJECT_TYPE;
  readonly projectStatus = PROJECT_STATUS;
  readonly milestoneStatus = MILESTONE_STATUS;
  readonly defenseStatus = DEFENSE_STATUS;
  readonly todayIso = today();

  readonly project = signal<Project | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly dialog = signal<Dialog>(null);
  readonly mine = signal(false);

  form = { url: '', comment: '', decision: 'VALIDATED' as MilestoneStatus, feedback: '', grade: null as number | null, title: '', due: '' };

  readonly isMember = computed(() => !!this.project()?.students.some(s => s.id === this.auth.user()?.id));
  readonly canManage = computed(() => {
    const p = this.project();
    return !!p && (this.auth.isManager() || p.supervisor.id === this.auth.user()?.id);
  });
  readonly allValidated = computed(() => {
    const p = this.project();
    return !!p && p.milestones.length > 0 && p.milestones.every(m => m.status === 'VALIDATED');
  });

  ngOnInit(): void {
    this.mine.set(!!this.route.snapshot.data['mine']);
    this.load();
  }

  load(): void {
    const call = this.mine() ? this.api.myProject() : this.api.project(+this.id()!);
    call.subscribe({
      next: p => { this.project.set(p); this.loading.set(false); this.scrollToFragment(); },
      error: err => {
        this.loading.set(false);
        if (!(this.mine() && err.status === 404)) this.error.set(apiError(err, 'Projet introuvable.'));
      }
    });
  }

  private scrollToFragment(): void {
    const frag = this.route.snapshot.fragment;
    if (frag) setTimeout(() => document.getElementById(frag)?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 50);
  }

  private reset(): void {
    this.form = { url: '', comment: '', decision: 'VALIDATED', feedback: '', grade: null, title: '', due: '' };
  }

  openSubmit(m: Milestone): void { this.reset(); this.form.url = m.deliverableUrl ?? ''; this.dialog.set({ kind: 'submit', milestone: m }); }
  openReview(m: Milestone): void { this.reset(); this.dialog.set({ kind: 'review', milestone: m }); }
  openAdd(): void { this.reset(); this.dialog.set({ kind: 'add' }); }
  openExternal(): void {
    this.reset();
    const p = this.project()!;
    this.form.title = p.externalSupervisorName ?? '';
    this.form.url = p.externalSupervisorEmail ?? '';
    this.dialog.set({ kind: 'external' });
  }

  confirmDialog(): void {
    const d = this.dialog();
    const p = this.project();
    if (!d || !p) return;
    let call: Observable<Project>;
    let msg: string;
    switch (d.kind) {
      case 'submit':
        call = this.api.submitMilestone(d.milestone.id, this.form.url.trim(), this.form.comment.trim());
        msg = 'Livrable rendu : votre encadrant a reçu une notification et un e-mail.';
        break;
      case 'review':
        call = this.api.reviewMilestone(d.milestone.id, this.form.decision, this.form.feedback.trim(), this.form.grade);
        msg = this.form.decision === 'VALIDATED' ? 'Jalon validé.' : 'Correction demandée : l’étudiant est prévenu.';
        break;
      case 'add':
        call = this.api.addMilestone(p.id, { title: this.form.title.trim(), dueDate: this.form.due, description: this.form.comment.trim() });
        msg = 'Jalon ajouté.';
        break;
      default:
        call = this.api.setExternalSupervisor(p.id, this.form.title.trim(), this.form.url.trim());
        msg = 'Tuteur en entreprise enregistré.';
    }
    this.busy.set(true);
    call.subscribe({
      next: updated => { this.project.set(updated); this.dialog.set(null); this.busy.set(false); this.success.set(msg); this.error.set(''); },
      error: err => { this.busy.set(false); this.error.set(apiError(err)); this.dialog.set(null); }
    });
  }

  deleteMilestone(m: Milestone): void {
    if (!confirm(`Supprimer le jalon « ${m.title} » ?`)) return;
    this.api.deleteMilestone(m.id).subscribe({ next: p => this.project.set(p), error: err => this.error.set(apiError(err)) });
  }

  markReady(): void {
    if (!confirm('Déclarer le projet prêt pour la soutenance ? Le chef de département sera prévenu.')) return;
    this.api.markReady(this.project()!.id).subscribe({
      next: p => { this.project.set(p); this.success.set('Projet prêt : le chef de département peut programmer la soutenance.'); },
      error: err => this.error.set(apiError(err))
    });
  }

  dueText(m: Milestone): string {
    if (m.status === 'VALIDATED' || m.status === 'SUBMITTED') return 'rendu';
    const n = daysUntil(m.dueDate);
    return n < 0 ? `dépassée de ${-n} j` : n === 0 ? 'aujourd’hui' : `dans ${n} j`;
  }

  names(p: Project): string { return p.students.map(s => s.fullName).join(' & '); }
  date = (d: string) => frDate(d);
  longDate = (d: string) => frDate(d, true);
  time = hhmm;
}
