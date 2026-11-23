import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { AuthService } from '../../../core/auth.service';
import { Convention, ConventionRequest, ConventionStatus } from '../../../core/models';
import { CONVENTION_STATUS, apiError, frDate, today } from '../../../core/labels';
import { Badge } from '../../../shared/badge';

export function emptyConvention(): ConventionRequest {
  return { companyName: '', companyAddress: '', companyEmail: '', tutorName: '', tutorEmail: '', startDate: '', endDate: '', missionDescription: '' };
}

@Component({
  selector: 'app-conventions',
  imports: [FormsModule, RouterLink, Badge],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <span class="eyebrow">Projets de fin d’études</span>
          <h1>Conventions de stage</h1>
          <p>{{ auth.isStudent() ? 'Préparez votre convention puis soumettez-la au chef de département.' : 'Conventions déposées par les étudiants : vérification et validation.' }}</p>
        </div>
        @if (auth.isStudent()) { <button class="btn btn-primary" (click)="openForm()">✚ Nouvelle convention</button> }
      </div>
      @if (error()) { <div class="alert error" style="margin-bottom:16px">{{ error() }}</div> }
      @if (auth.isManager()) {
        <div class="toolbar">
          <select class="input" [(ngModel)]="status" (change)="load()">
            <option value="">Toutes</option>
            @for (s of statuses; track s) { <option [value]="s">{{ labels[s][0] }}</option> }
          </select>
        </div>
      }
      @if (auth.role() === 'ENSEIGNANT') {
        <div class="alert info">Les conventions sont gérées par les étudiants et le chef de département.</div>
      } @else if (loading()) { <div class="spinner"></div> } @else {
        <div class="card">
          <table class="table">
            <thead><tr>@if (!auth.isStudent()) {<th>Étudiant</th>}<th>Entreprise</th><th>Tuteur</th><th>Période</th><th>Statut</th><th></th></tr></thead>
            <tbody>
              @for (c of conventions(); track c.id) {
                <tr>
                  @if (!auth.isStudent()) { <td><strong>{{ c.student.fullName }}</strong><br /><small class="muted">{{ c.filiereCode }} · {{ c.studentNumber }}</small></td> }
                  <td><a [routerLink]="['/pfe/conventions', c.id]"><strong>{{ c.companyName }}</strong></a><br /><small class="muted">{{ c.companyAddress }}</small></td>
                  <td>{{ c.tutorName }}<br /><small class="muted">{{ c.tutorEmail }}</small></td>
                  <td class="nowrap">{{ date(c.startDate) }} → {{ date(c.endDate) }}<br /><small class="muted">{{ c.durationWeeks }} semaines</small></td>
                  <td><app-badge [label]="labels[c.status]" /></td>
                  <td class="nowrap">
                    @if (auth.isStudent() && (c.status === 'DRAFT' || c.status === 'REJECTED')) {
                      <button class="btn btn-ghost btn-sm" (click)="openForm(c)">Modifier</button>
                      <button class="btn btn-primary btn-sm" (click)="submit(c)">Soumettre</button>
                    }
                    @if (auth.isStudent() && c.status === 'DRAFT') { <button class="btn-link small danger" (click)="remove(c)">Supprimer</button> }
                    @if (auth.isManager() && c.status === 'SUBMITTED') { <a class="btn btn-primary btn-sm" [routerLink]="['/pfe/conventions', c.id]">Examiner</a> }
                    @if (c.status === 'VALIDATED') { <a class="btn btn-ghost btn-sm" [routerLink]="['/pfe/conventions', c.id]">Imprimer</a> }
                  </td>
                </tr>
              } @empty { <tr><td colspan="6" class="empty">Aucune convention.</td></tr> }
            </tbody>
          </table>
        </div>
      }

      @if (formOpen()) {
        <div class="modal-backdrop" (click)="formOpen.set(false)">
          <form class="modal wide" (click)="$event.stopPropagation()" (ngSubmit)="save()" #f="ngForm">
            <h2>{{ editId ? 'Modifier la convention' : 'Nouvelle convention de stage' }}</h2>
            @if (formError()) { <div class="alert error mt">{{ formError() }}</div> }
            <div class="form-grid mt">
              <div class="field"><label>Entreprise *</label><input name="companyName" [(ngModel)]="model.companyName" required /></div>
              <div class="field"><label>E-mail de l’entreprise</label><input type="email" name="companyEmail" [(ngModel)]="model.companyEmail" /></div>
              <div class="field full"><label>Adresse *</label><input name="companyAddress" [(ngModel)]="model.companyAddress" required /></div>
              <div class="field"><label>Tuteur en entreprise *</label><input name="tutorName" [(ngModel)]="model.tutorName" required /></div>
              <div class="field"><label>E-mail du tuteur *</label><input type="email" name="tutorEmail" [(ngModel)]="model.tutorEmail" required /></div>
              <div class="field"><label>Début *</label><input type="date" name="startDate" [min]="todayIso" [(ngModel)]="model.startDate" required /></div>
              <div class="field"><label>Fin *</label><input type="date" name="endDate" [min]="model.startDate" [(ngModel)]="model.endDate" required />
                <span class="hint">Entre 4 et 26 semaines</span></div>
              <div class="field full"><label>Missions *</label><textarea name="mission" [(ngModel)]="model.missionDescription" required></textarea></div>
            </div>
            <div class="actions">
              <button type="button" class="btn btn-ghost" (click)="formOpen.set(false)">Annuler</button>
              <button class="btn btn-primary" [disabled]="busy() || !f.valid">Enregistrer le brouillon</button>
            </div>
          </form>
        </div>
      }
    </div>
  `,
  styles: [`
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .wide { width: min(760px, 100%); }
    .danger { color: var(--red); margin-left: 6px; }
  `]
})
export class Conventions implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly labels = CONVENTION_STATUS;
  readonly statuses = Object.keys(CONVENTION_STATUS) as ConventionStatus[];
  readonly todayIso = today();
  readonly conventions = signal<Convention[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly formError = signal('');
  readonly formOpen = signal(false);
  status: ConventionStatus | '' = '';
  editId: number | null = null;
  model: ConventionRequest = emptyConvention();

  ngOnInit(): void {
    if (this.auth.isManager()) this.status = 'SUBMITTED';
    if (this.auth.role() !== 'ENSEIGNANT') this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.conventions(this.status).subscribe({
      next: c => { this.conventions.set(c); this.loading.set(false); },
      error: err => { this.error.set(apiError(err)); this.loading.set(false); }
    });
  }

  openForm(c?: Convention): void {
    this.editId = c?.id ?? null;
    this.model = c ? {
      companyName: c.companyName, companyAddress: c.companyAddress, companyEmail: c.companyEmail ?? '', tutorName: c.tutorName,
      tutorEmail: c.tutorEmail, startDate: c.startDate, endDate: c.endDate, missionDescription: c.missionDescription
    } : emptyConvention();
    this.formError.set('');
    this.formOpen.set(true);
  }

  save(): void {
    this.busy.set(true);
    const call = this.editId ? this.api.updateConvention(this.editId, this.model) : this.api.createConvention(this.model);
    call.subscribe({
      next: () => { this.busy.set(false); this.formOpen.set(false); this.load(); },
      error: err => { this.busy.set(false); this.formError.set(apiError(err)); }
    });
  }

  submit(c: Convention): void {
    if (!confirm('Soumettre la convention au chef de département ?')) return;
    this.api.submitConvention(c.id).subscribe({ next: () => this.load(), error: err => this.error.set(apiError(err)) });
  }

  remove(c: Convention): void {
    if (!confirm('Supprimer ce brouillon ?')) return;
    this.api.deleteConvention(c.id).subscribe({ next: () => this.load(), error: err => this.error.set(apiError(err)) });
  }

  date = (d: string) => frDate(d);
}
