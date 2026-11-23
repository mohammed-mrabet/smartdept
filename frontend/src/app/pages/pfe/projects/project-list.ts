import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { AuthService } from '../../../core/auth.service';
import { Project, ProjectStatus } from '../../../core/models';
import { PROJECT_STATUS, apiError } from '../../../core/labels';
import { Badge } from '../../../shared/badge';

@Component({
  selector: 'app-project-list',
  imports: [FormsModule, RouterLink, Badge],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <span class="eyebrow">Projets de fin d’études</span>
          <h1>{{ auth.isManager() ? 'Tous les projets' : 'Projets encadrés' }}</h1>
          <p>Avancement des jalons, livrables à relire et préparation des soutenances.</p>
        </div>
      </div>
      @if (error()) { <div class="alert error" style="margin-bottom:16px">{{ error() }}</div> }
      <div class="toolbar">
        <input class="input search" placeholder="Rechercher un sujet ou un étudiant…" [(ngModel)]="q" />
        <select class="input" [(ngModel)]="status">
          <option value="">Tous les statuts</option>
          @for (s of statuses; track s) { <option [value]="s">{{ labels[s][0] }}</option> }
        </select>
      </div>
      @if (loading()) { <div class="spinner"></div> } @else {
        <div class="card">
          <table class="table">
            <thead><tr><th>Sujet</th><th>Étudiant(s)</th>@if (auth.isManager()) {<th>Encadrant</th>}<th style="width:180px">Avancement</th><th>À relire</th><th>Statut</th></tr></thead>
            <tbody>
              @for (p of visible(); track p.id) {
                <tr>
                  <td><a [routerLink]="['/pfe/projets', p.id]"><strong>{{ p.subjectTitle }}</strong></a>
                    @if (p.companyName) { <br /><small class="muted">{{ p.companyName }}</small> }</td>
                  <td>{{ names(p) }}</td>
                  @if (auth.isManager()) { <td>{{ p.supervisor.fullName }}</td> }
                  <td><div class="row"><div class="progress" style="flex:1"><span [style.width.%]="p.progress"></span></div><small>{{ p.progress }} %</small></div>
                    @if (late(p)) { <small class="badge red mt-xs">{{ late(p) }} en retard</small> }</td>
                  <td>@if (submitted(p)) { <span class="badge blue">{{ submitted(p) }}</span> } @else { <span class="muted">—</span> }</td>
                  <td><app-badge [label]="labels[p.status]" /></td>
                </tr>
              } @empty { <tr><td colspan="6" class="empty">Aucun projet.</td></tr> }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .mt-xs { margin-top: 6px; }
  `]
})
export class ProjectList implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  readonly labels = PROJECT_STATUS;
  readonly statuses = Object.keys(PROJECT_STATUS) as ProjectStatus[];
  readonly projects = signal<Project[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  q = '';
  status: ProjectStatus | '' = '';


  ngOnInit(): void {
    this.api.projects().subscribe({
      next: p => { this.projects.set(p); this.loading.set(false); },
      error: err => { this.error.set(apiError(err)); this.loading.set(false); }
    });
  }

  // Filtrage côté client (les listes restent courtes à l'échelle d'un département)
  visible(): Project[] {
    const q = this.q.trim().toLowerCase();
    return this.projects().filter(p => (!this.status || p.status === this.status)
      && (!q || p.subjectTitle.toLowerCase().includes(q) || this.names(p).toLowerCase().includes(q)));
  }

  names(p: Project): string { return p.students.map(s => s.fullName).join(', '); }
  late(p: Project): number { return p.milestones.filter(m => m.late).length; }
  submitted(p: Project): number { return p.milestones.filter(m => m.status === 'SUBMITTED').length; }
}
