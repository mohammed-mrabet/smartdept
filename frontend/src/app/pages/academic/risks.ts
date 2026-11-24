import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ApiService } from '../../core/api.service';
import { AcademicApi, Overview, RISK, Risk, RiskLevel } from '../../core/academic';
import { Filiere } from '../../core/models';
import { apiError } from '../../core/labels';

/** Suivi des étudiants : score de risque (notes, absences, jalons de PFE) et synthèse IA. */
@Component({
  selector: 'app-risks',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page">
      <div class="page-head">
        <div><span class="eyebrow">Scolarité · IA</span><h1>Suivi des étudiants</h1>
          <p>Score de 0 à 100 : moyenne sous 10, modules ajournés, absences non justifiées, jalons de PFE en retard. Le scan automatique a lieu chaque lundi.</p></div>
        @if (auth.isManager()) { <button class="btn btn-primary" (click)="scan()" [disabled]="busy()">Prévenir les étudiants à risque élevé</button> }
      </div>
      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }
      @if (success()) { <div class="alert success" style="margin-bottom:12px">{{ success() }}</div> }
      @if (overview(); as o) {
        <div class="grid grid-4">
          <div class="card stat"><div class="icon bg-red">⚠</div><div><div class="value">{{ o.studentsAtRisk }}</div><div class="label">à risque élevé</div></div></div>
          <div class="card stat"><div class="icon bg-blue">∑</div><div><div class="value">{{ o.departmentAverage ?? '—' }}</div><div class="label">moyenne du département</div></div></div>
          <div class="card stat"><div class="icon bg-amber">⏳</div><div><div class="value">{{ o.pendingJustifications }}</div><div class="label">justificatifs en attente</div></div></div>
          <div class="card stat"><div class="icon bg-violet">📝</div><div><div class="value">{{ o.examsNext14Days }}</div><div class="label">examens sous 14 jours</div></div></div>
        </div>
      }
      <div class="toolbar mt">
        <select class="input slim" [(ngModel)]="filiereId" (change)="load()"><option [ngValue]="null">Toutes les filières</option>
          @for (f of filieres(); track f.id) { <option [ngValue]="f.id">{{ f.code }}</option> }</select>
        <div class="seg">
          <button [class.on]="level() === null" (click)="level.set(null)">Tous ({{ risks().length }})</button>
          <button [class.on]="level() === 'HIGH'" (click)="level.set('HIGH')">Élevé ({{ count('HIGH') }})</button>
          <button [class.on]="level() === 'MEDIUM'" (click)="level.set('MEDIUM')">Moyen ({{ count('MEDIUM') }})</button>
        </div>
      </div>
      <div class="card">
        <table class="table">
          <thead><tr><th>Étudiant</th><th style="width:170px">Risque</th><th>Moyenne</th><th>Absences</th><th>Assiduité</th><th>Signaux</th><th></th></tr></thead>
          <tbody>@for (r of filtered(); track r.student.id) {
            <tr><td><strong>{{ r.student.fullName }}</strong><br /><small class="muted">{{ r.filiereCode }} · {{ r.studentNumber }}</small></td>
              <td><div class="score"><div class="bar"><i [style.width.%]="r.score" [class]="r.level.toLowerCase()"></i></div><b>{{ r.score }}</b></div>
                <span class="badge" [class]="'badge ' + riskLabel[r.level][1]">{{ riskLabel[r.level][0] }}</span></td>
              <td [class.bad]="(r.average ?? 20) < 10">{{ r.average ?? '—' }}</td>
              <td>{{ r.unjustifiedAbsences }}</td>
              <td>{{ r.attendanceRate != null ? r.attendanceRate + ' %' : '—' }}</td>
              <td class="small text-2">{{ r.reasons.join(' · ') }}</td>
              <td class="nowrap"><button class="btn btn-ai btn-sm" (click)="explain(r)">✦ Analyse</button>
                <a class="btn-link small rel" routerLink="/notes" [queryParams]="{ etudiant: r.student.id }">Relevé</a></td></tr>
          } @empty { <tr><td colspan="7" class="empty">Aucun étudiant.</td></tr> }</tbody>
        </table>
      </div>
      @if (explaining(); as e) {
        <div class="modal-backdrop" (click)="explaining.set(null)">
          <div class="modal" (click)="$event.stopPropagation()">
            <h2>✦ {{ e.name }}</h2>
            @if (e.text) { <p class="pre mt">{{ e.text }}</p><small class="muted">{{ e.source === 'ia' ? 'Synthèse du modèle IA' : 'Synthèse par règles (IA non configurée)' }}</small> }
            @else { <div class="spinner"></div> }
            <div class="actions"><button class="btn btn-ghost" (click)="explaining.set(null)">Fermer</button></div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .eyebrow { display: block; margin-bottom: 6px; color: var(--violet); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .slim { width: auto; }
    .seg { display: inline-flex; border: 1px solid var(--border); border-radius: 9px; overflow: hidden; background: var(--surface); }
    .seg button { border: 0; background: none; padding: 8px 12px; font: 600 13px var(--font); cursor: pointer; color: var(--text-2); }
    .seg button.on { background: var(--primary-soft); color: var(--primary-2); }
    .score { display: flex; gap: 8px; align-items: center; margin-bottom: 4px; }
    .bar { flex: 1; height: 8px; background: var(--track); border-radius: 99px; overflow: hidden; }
    .bar i { display: block; height: 100%; } .bar i.high { background: var(--red); } .bar i.medium { background: #e0a100; } .bar i.low { background: var(--teal); }
    .bad { color: var(--red); font-weight: 700; }
    .rel { margin-left: 10px; }
  `]
})
export class Risks implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(AcademicApi);
  private readonly core = inject(ApiService);
  readonly riskLabel = RISK;
  readonly risks = signal<Risk[]>([]);
  readonly overview = signal<Overview | null>(null);
  readonly filieres = signal<Filiere[]>([]);
  readonly level = signal<RiskLevel | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly explaining = signal<{ name: string; text?: string; source?: string } | null>(null);
  readonly filtered = computed(() => this.risks().filter(r => !this.level() || r.level === this.level()));
  filiereId: number | null = null;

  ngOnInit(): void {
    this.core.filieres().subscribe(f => this.filieres.set(f));
    this.api.overview().subscribe({ next: o => this.overview.set(o), error: () => {} });
    this.load();
  }

  load(): void { this.api.risks(this.filiereId).subscribe({ next: r => this.risks.set(r), error: err => this.error.set(apiError(err)) }); }
  count(l: RiskLevel): number { return this.risks().filter(r => r.level === l).length; }

  scan(): void {
    this.busy.set(true);
    this.api.scanRisks().subscribe({
      next: r => { this.busy.set(false); this.success.set(`${r.high} étudiant(s) à risque élevé, ${r.medium} à risque moyen ; ${r.notified} prévenu(s) (au plus une fois par semaine).`); },
      error: err => { this.busy.set(false); this.error.set(apiError(err)); }
    });
  }

  explain(r: Risk): void {
    this.explaining.set({ name: r.student.fullName });
    this.api.explainRisk(r.student.id).subscribe({
      next: e => this.explaining.set({ name: r.student.fullName, text: e.text, source: e.source }),
      error: err => { this.explaining.set(null); this.error.set(apiError(err)); }
    });
  }
}
