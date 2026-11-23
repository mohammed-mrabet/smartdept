import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { AuthService } from '../../../core/auth.service';
import { Convention } from '../../../core/models';
import { CONVENTION_STATUS, apiError, frDate, today } from '../../../core/labels';
import { Badge } from '../../../shared/badge';

@Component({
  selector: 'app-convention-detail',
  imports: [FormsModule, RouterLink, Badge],
  template: `
    <div class="page">
      <div class="no-print row between">
        <a routerLink="/pfe/conventions" class="small">← Conventions</a>
        @if (c()?.status === 'VALIDATED') { <button class="btn btn-ghost btn-sm" (click)="print()">🖨 Imprimer</button> }
      </div>
      @if (error()) { <div class="alert error mt no-print">{{ error() }}</div> }
      @if (!c() && !error()) { <div class="spinner"></div> }

      @if (c(); as c) {
        @if (auth.isManager() && c.status === 'SUBMITTED') {
          <div class="card mt no-print review">
            <h3>Décision</h3>
            <div class="field mt"><label>Commentaire (obligatoire en cas de refus)</label><textarea [(ngModel)]="comment"></textarea></div>
            <div class="row mt">
              <button class="btn btn-success" (click)="review(true)" [disabled]="busy()">Valider la convention</button>
              <button class="btn btn-danger" (click)="review(false)" [disabled]="busy() || !comment.trim()">Refuser</button>
            </div>
          </div>
        }

        <article class="card doc mt">
          <header>
            <div><strong>Université Mohammed Premier</strong><br />Faculté des Sciences · Oujda<br />Département d’informatique</div>
            <div class="right"><app-badge [label]="labels[c.status]" /><br /><small class="muted">Réf. CONV-{{ c.id }} · {{ date(c.createdAt) }}</small></div>
          </header>
          <h1>Convention de stage de fin d’études</h1>

          <section><h3>Article 1 — Les parties</h3>
            <p>La présente convention est conclue entre la <strong>Faculté des Sciences d’Oujda</strong>, représentée par le chef du département d’informatique,
              l’entreprise <strong>{{ c.companyName }}</strong>, sise {{ c.companyAddress }}@if (c.companyEmail) { ({{ c.companyEmail }}) },
              et l’étudiant(e) <strong>{{ c.student.fullName }}</strong>@if (c.studentNumber) {, n° {{ c.studentNumber }}}@if (c.filiereCode) {, inscrit(e) en {{ c.filiereCode }}}.</p></section>

          <section><h3>Article 2 — Objet et missions</h3>
            @if (c.projectTitle) { <p>Le stage s’inscrit dans le projet de fin d’études « <strong>{{ c.projectTitle }}</strong> ».</p> }
            <p class="pre">{{ c.missionDescription }}</p></section>

          <section><h3>Article 3 — Durée</h3>
            <p>Le stage se déroule du <strong>{{ longDate(c.startDate) }}</strong> au <strong>{{ longDate(c.endDate) }}</strong>, soit {{ c.durationWeeks }} semaines.</p></section>

          <section><h3>Article 4 — Encadrement</h3>
            <p>Le suivi en entreprise est assuré par <strong>{{ c.tutorName }}</strong> ({{ c.tutorEmail }}). Le tuteur s’engage à faciliter l’accès de l’étudiant(e)
              aux informations nécessaires et à participer à l’évaluation finale.</p></section>

          <section><h3>Article 5 — Confidentialité</h3>
            <p>L’étudiant(e) s’engage à ne divulguer aucune information confidentielle de l’entreprise. Le rapport de stage peut faire l’objet d’une diffusion restreinte à la demande de l’entreprise.</p></section>

          @if (c.reviewComment) { <p class="alert info mt">Commentaire du département : {{ c.reviewComment }}</p> }

          <div class="signs">
            <div>Le chef du département<span></span></div>
            <div>Le représentant de l’entreprise<span></span></div>
            <div>L’étudiant(e)<span></span></div>
          </div>
          <p class="muted small mt">Fait à Oujda, le {{ longDate(todayIso) }} · document généré par SmartDept</p>
        </article>
      }
    </div>
  `,
  styles: [`
    .doc { max-width: 860px; margin-left: auto; margin-right: auto; padding: 40px 48px; font-size: 14.5px; line-height: 1.65; }
    .doc header { display: flex; justify-content: space-between; gap: 20px; padding-bottom: 18px; border-bottom: 2px solid var(--text); font-size: 13px; }
    .doc .right { text-align: right; }
    .doc h1 { text-align: center; margin: 28px 0 20px; font-size: 22px; }
    .doc section { margin-top: 16px; }
    .doc h3 { margin-bottom: 4px; }
    .signs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; margin-top: 40px; text-align: center; font-weight: 650; font-size: 13px; }
    .signs span { display: block; height: 70px; border-bottom: 1px dashed #9aa5b8; }
    .review { max-width: 860px; margin-left: auto; margin-right: auto; border-color: #f6dcb0; }
    @media (max-width: 700px) { .doc { padding: 22px 18px; } .signs { grid-template-columns: 1fr; } }
  `]
})
export class ConventionDetail implements OnInit {
  readonly id = input.required<string>();
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);

  readonly labels = CONVENTION_STATUS;
  readonly todayIso = today();
  readonly c = signal<Convention | null>(null);
  readonly error = signal('');
  readonly busy = signal(false);
  comment = '';

  ngOnInit(): void {
    this.api.convention(+this.id()).subscribe({ next: c => this.c.set(c), error: err => this.error.set(apiError(err, 'Convention introuvable.')) });
  }

  review(approve: boolean): void {
    this.busy.set(true);
    this.api.reviewConvention(this.c()!.id, approve, this.comment.trim()).subscribe({
      next: c => { this.c.set(c); this.busy.set(false); },
      error: err => { this.error.set(apiError(err)); this.busy.set(false); }
    });
  }

  print(): void { window.print(); }
  date = (d: string) => frDate(d);
  longDate = (d: string) => frDate(d, true);
}
