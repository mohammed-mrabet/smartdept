import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { AuthService } from '../../../core/auth.service';
import { Application, Subject, SummaryResponse } from '../../../core/models';
import { APPLICATION_STATUS, SUBJECT_STATUS, SUBJECT_TYPE, apiError, frDate } from '../../../core/labels';
import { Badge } from '../../../shared/badge';

@Component({
  selector: 'app-subject-detail',
  imports: [FormsModule, RouterLink, Badge],
  template: `
    <div class="page">
      <a routerLink="/pfe/sujets" class="small">← Tous les sujets</a>
      @if (error()) { <div class="alert error mt">{{ error() }}</div> }
      @if (success()) { <div class="alert success mt">{{ success() }}</div> }
      @if (!subject()) { <div class="spinner"></div> }

      @if (subject(); as s) {
        <div class="page-head mt">
          <div>
            <div class="row"><span class="badge violet">{{ typeLabel[s.type] }}</span><app-badge [label]="statusLabel[s.status]" />
              <span class="muted small">{{ s.filiere.name }} · {{ s.academicYear }}</span></div>
            <h1 class="mt">{{ s.title }}</h1>
            <p>Encadrant : <strong>{{ s.supervisor.fullName }}</strong>@if (s.companyName) { · Entreprise : <strong>{{ s.companyName }}</strong> }</p>
          </div>
          <div class="row">
            @if (canEdit()) {
              <a class="btn btn-ghost" [routerLink]="['/pfe/sujets', s.id, 'modifier']">Modifier</a>
              <button class="btn btn-danger" (click)="remove()">Supprimer</button>
            }
            @if (auth.isManager() && s.status === 'PROPOSED') {
              <button class="btn btn-success" (click)="publish()">Publier</button>
              <button class="btn btn-danger" (click)="rejecting.set(true)">Refuser</button>
            }
            @if (auth.isManager() && (s.status === 'PUBLISHED' || s.status === 'ASSIGNED')) {
              <button class="btn btn-ghost" (click)="archive()">Archiver</button>
            }
          </div>
        </div>

        @if (s.status === 'REJECTED' && s.rejectionReason) { <div class="alert warn">Motif du refus : {{ s.rejectionReason }}</div> }

        <div class="grid layout mt">
          <div class="stack">
            <div class="card">
              <div class="card-head"><h2>Description</h2>
                <button class="btn btn-ai btn-sm" (click)="summarize()" [disabled]="summarizing()">✦ {{ summarizing() ? 'Analyse…' : 'Résumé IA' }}</button></div>
              @if (summary(); as sum) {
                <div class="alert info" style="margin-bottom:14px"><strong>En bref :</strong> {{ sum.summary }}
                  <small class="muted"> ({{ sum.source === 'ia' ? 'modèle IA' : 'analyse locale' }})</small></div>
              }
              <p class="pre">{{ s.description }}</p>
              <div class="mt">@for (k of s.keywords; track k) { <span class="chip">{{ k }}</span> }</div>
            </div>

            @if (canSeeApplications()) {
              <div class="card">
                <div class="card-head"><h2>Candidatures ({{ applications().length }})</h2>
                  <span class="muted small">{{ s.acceptedStudents }}/{{ s.maxStudents }} place(s) prise(s)</span></div>
                <table class="table">
                  <thead><tr><th>Étudiant</th><th>Choix</th><th>Motivation</th><th>Statut</th><th></th></tr></thead>
                  <tbody>
                    @for (a of applications(); track a.id) {
                      <tr>
                        <td><strong>{{ a.student.fullName }}</strong><br /><small class="muted">{{ a.studentNumber }}</small></td>
                        <td>n° {{ a.preferenceRank }}</td>
                        <td class="text-2 small">{{ a.motivation || '—' }}</td>
                        <td><app-badge [label]="appStatus[a.status]" />@if (a.decisionComment) {<br /><small class="muted">{{ a.decisionComment }}</small>}</td>
                        <td class="nowrap">
                          @if (a.status === 'PENDING' && s.status === 'PUBLISHED') {
                            <button class="btn btn-success btn-sm" (click)="decide(a, true)">Accepter</button>
                            <button class="btn btn-danger btn-sm" (click)="decide(a, false)">Refuser</button>
                          }
                        </td>
                      </tr>
                    } @empty { <tr><td colspan="5" class="empty">Aucune candidature pour l’instant.</td></tr> }
                  </tbody>
                </table>
              </div>
            }
          </div>

          <div class="stack">
            <div class="card">
              <h3>En résumé</h3>
              <dl>
                <dt>Type</dt><dd>{{ typeLabel[s.type] }}</dd>
                <dt>Filière</dt><dd>{{ s.filiere.code }}</dd>
                <dt>Places</dt><dd>{{ s.maxStudents === 2 ? 'Binôme' : 'Monôme' }} ({{ s.acceptedStudents }}/{{ s.maxStudents }})</dd>
                <dt>Contact</dt><dd><a [href]="'mailto:' + s.supervisor.email">{{ s.supervisor.email }}</a></dd>
                <dt>Publié le</dt><dd>{{ date(s.createdAt) }}</dd>
              </dl>
            </div>

            @if (auth.isStudent()) {
              <div class="card">
                @if (myApplication(); as a) {
                  <h3>Ma candidature</h3>
                  <p class="mt">Choix n° {{ a.preferenceRank }} · <app-badge [label]="appStatus[a.status]" /></p>
                  @if (a.decisionComment) { <p class="mt text-2 small">{{ a.decisionComment }}</p> }
                  @if (a.status === 'PENDING') { <button class="btn btn-danger btn-sm mt" (click)="withdraw(a)">Retirer ma candidature</button> }
                  @if (a.status === 'ACCEPTED') { <a class="btn btn-primary btn-sm mt" routerLink="/pfe/mon-projet">Voir mon projet</a> }
                } @else if (s.status === 'PUBLISHED') {
                  <h3>Candidater</h3>
                  @if (freeRanks().length === 0) {
                    <p class="mt text-2">Vous avez déjà 3 candidatures en cours. Retirez-en une pour choisir ce sujet.</p>
                  } @else {
                    <form (ngSubmit)="apply()" class="stack mt">
                      <div class="field"><label>Rang de préférence</label>
                        <select name="rank" [(ngModel)]="rank">@for (r of freeRanks(); track r) { <option [ngValue]="r">Choix n° {{ r }}</option> }</select></div>
                      <div class="field"><label>Motivation</label>
                        <textarea name="motivation" [(ngModel)]="motivation" maxlength="2000" placeholder="Pourquoi ce sujet ? Compétences, projets réalisés…"></textarea></div>
                      <button class="btn btn-primary" [disabled]="busy()">Envoyer ma candidature</button>
                    </form>
                  }
                } @else {
                  <p class="text-2">Ce sujet n’accepte plus de candidatures.</p>
                }
              </div>
            }
          </div>
        </div>
      }

      @if (rejecting()) {
        <div class="modal-backdrop" (click)="rejecting.set(false)">
          <form class="modal" (click)="$event.stopPropagation()" (ngSubmit)="reject()">
            <h2>Refuser le sujet</h2>
            <div class="field mt"><label>Motif (envoyé à l’encadrant)</label>
              <textarea name="reason" [(ngModel)]="reason" required></textarea></div>
            <div class="actions">
              <button type="button" class="btn btn-ghost" (click)="rejecting.set(false)">Annuler</button>
              <button class="btn btn-danger" [disabled]="!reason.trim()">Refuser</button>
            </div>
          </form>
        </div>
      }
    </div>
  `,
  styles: [`
    .layout { grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr); align-items: start; }
    dl { margin: 12px 0 0; display: grid; grid-template-columns: auto 1fr; gap: 8px 14px; font-size: 14px; }
    dt { color: var(--muted); } dd { margin: 0; word-break: break-word; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
  `]
})
export class SubjectDetail implements OnInit {
  readonly id = input.required<string>();
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly statusLabel = SUBJECT_STATUS;
  readonly typeLabel = SUBJECT_TYPE;
  readonly appStatus = APPLICATION_STATUS;

  readonly subject = signal<Subject | null>(null);
  readonly applications = signal<Application[]>([]);
  readonly mine = signal<Application[]>([]);
  readonly summary = signal<SummaryResponse | null>(null);
  readonly summarizing = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly rejecting = signal(false);

  rank = 1;
  motivation = '';
  reason = '';

  readonly canEdit = computed(() => {
    const s = this.subject();
    const me = this.auth.user();
    return !!s && !!me && (this.auth.isManager() || s.supervisor.id === me.id) && (s.status === 'PROPOSED' || s.status === 'REJECTED' || s.status === 'PUBLISHED')
      && s.acceptedStudents === 0;
  });
  readonly canSeeApplications = computed(() => {
    const s = this.subject();
    return !!s && (this.auth.isManager() || s.supervisor.id === this.auth.user()?.id);
  });
  readonly myApplication = computed(() => this.mine().find(a => a.subject.id === this.subject()?.id && a.status !== 'WITHDRAWN'));
  readonly freeRanks = computed(() => {
    const used = this.mine().filter(a => a.status === 'PENDING').map(a => a.preferenceRank);
    return [1, 2, 3].filter(r => !used.includes(r));
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.subject(+this.id()).subscribe({
      next: s => {
        this.subject.set(s);
        if (this.canSeeApplications()) this.api.subjectApplications(s.id).subscribe({ next: a => this.applications.set(a), error: () => {} });
      },
      error: err => this.error.set(apiError(err, 'Sujet introuvable.'))
    });
    if (this.auth.isStudent()) {
      this.api.myApplications().subscribe({ next: a => { this.mine.set(a); this.rank = this.freeRanks()[0] ?? 1; } });
    }
  }

  private done(msg: string): void {
    this.success.set(msg);
    this.error.set('');
    this.busy.set(false);
    this.load();
  }

  private fail(err: any): void {
    this.error.set(apiError(err));
    this.success.set('');
    this.busy.set(false);
  }

  apply(): void {
    this.busy.set(true);
    this.api.apply(this.subject()!.id, this.rank, this.motivation.trim()).subscribe({
      next: () => { this.motivation = ''; this.done('Candidature envoyée : l’encadrant a été prévenu.'); },
      error: err => this.fail(err)
    });
  }

  withdraw(a: Application): void {
    if (!confirm('Retirer cette candidature ?')) return;
    this.api.withdraw(a.id).subscribe({ next: () => this.done('Candidature retirée.'), error: err => this.fail(err) });
  }

  decide(a: Application, accept: boolean): void {
    const comment = prompt(accept ? `Accepter ${a.student.fullName} ? Message facultatif :` : `Refuser ${a.student.fullName} ? Motif facultatif :`, '');
    if (comment === null) return;
    const call = accept ? this.api.acceptApplication(a.id, comment) : this.api.rejectApplication(a.id, comment);
    call.subscribe({
      next: () => this.done(accept ? `${a.student.fullName} est affecté(e) au sujet : le projet et ses jalons sont créés.` : 'Candidature refusée.'),
      error: err => this.fail(err)
    });
  }

  publish(): void {
    this.api.publishSubject(this.subject()!.id).subscribe({ next: () => this.done('Sujet publié : les étudiants de la filière sont notifiés.'), error: err => this.fail(err) });
  }

  reject(): void {
    this.api.rejectSubject(this.subject()!.id, this.reason.trim()).subscribe({
      next: () => { this.rejecting.set(false); this.reason = ''; this.done('Sujet refusé : l’encadrant est prévenu.'); },
      error: err => this.fail(err)
    });
  }

  archive(): void {
    if (!confirm('Archiver ce sujet ?')) return;
    this.api.archiveSubject(this.subject()!.id).subscribe({ next: () => this.done('Sujet archivé.'), error: err => this.fail(err) });
  }

  remove(): void {
    if (!confirm('Supprimer définitivement ce sujet ?')) return;
    this.api.deleteSubject(this.subject()!.id).subscribe({ next: () => this.router.navigate(['/pfe/sujets']), error: err => this.fail(err) });
  }

  summarize(): void {
    const s = this.subject()!;
    this.summarizing.set(true);
    this.api.summarize(`${s.title}. ${s.description}`, 3).subscribe({
      next: r => { this.summary.set(r); this.summarizing.set(false); },
      error: err => { this.fail(err); this.summarizing.set(false); }
    });
  }

  date = (d: string) => frDate(d);
}
