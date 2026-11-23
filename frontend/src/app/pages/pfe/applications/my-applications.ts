import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../../core/api.service';
import { Application } from '../../../core/models';
import { APPLICATION_STATUS, apiError, frDate } from '../../../core/labels';
import { Badge } from '../../../shared/badge';

@Component({
  selector: 'app-my-applications',
  imports: [RouterLink, Badge],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <span class="eyebrow">Projets de fin d’études</span>
          <h1>Mes candidatures</h1>
          <p>Trois choix au maximum. Dès qu’une candidature est acceptée, les autres sont retirées automatiquement.</p>
        </div>
        <a class="btn btn-primary" routerLink="/pfe/sujets">Parcourir les sujets</a>
      </div>
      @if (error()) { <div class="alert error" style="margin-bottom:16px">{{ error() }}</div> }
      @if (loading()) { <div class="spinner"></div> } @else {
        <div class="card">
          <table class="table">
            <thead><tr><th>Choix</th><th>Sujet</th><th>Encadrant</th><th>Déposée le</th><th>Statut</th><th></th></tr></thead>
            <tbody>
              @for (a of applications(); track a.id) {
                <tr>
                  <td><span class="rank">{{ a.preferenceRank }}</span></td>
                  <td><a [routerLink]="['/pfe/sujets', a.subject.id]"><strong>{{ a.subject.title }}</strong></a>
                    @if (a.decisionComment) { <br /><small class="muted">« {{ a.decisionComment }} »</small> }</td>
                  <td>{{ a.subject.supervisorName }}</td>
                  <td class="nowrap">{{ date(a.createdAt) }}</td>
                  <td><app-badge [label]="status[a.status]" /></td>
                  <td class="nowrap">
                    @if (a.status === 'PENDING') { <button class="btn btn-danger btn-sm" (click)="withdraw(a)">Retirer</button> }
                    @if (a.status === 'ACCEPTED') { <a class="btn btn-primary btn-sm" routerLink="/pfe/mon-projet">Mon projet</a> }
                  </td>
                </tr>
              } @empty { <tr><td colspan="6" class="empty">Aucune candidature pour le moment.</td></tr> }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .rank { display: inline-grid; place-items: center; width: 28px; height: 28px; border-radius: 50%; background: var(--primary-soft); color: var(--primary-2); font-weight: 800; }
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
  `]
})
export class MyApplications implements OnInit {
  private readonly api = inject(ApiService);
  readonly status = APPLICATION_STATUS;
  readonly applications = signal<Application[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.myApplications().subscribe({
      next: a => { this.applications.set(a.sort((x, y) => (x.status === 'WITHDRAWN' ? 1 : 0) - (y.status === 'WITHDRAWN' ? 1 : 0) || x.preferenceRank - y.preferenceRank)); this.loading.set(false); },
      error: err => { this.error.set(apiError(err)); this.loading.set(false); }
    });
  }

  withdraw(a: Application): void {
    if (!confirm(`Retirer votre candidature au sujet « ${a.subject.title} » ?`)) return;
    this.api.withdraw(a.id).subscribe({ next: () => this.load(), error: err => this.error.set(apiError(err)) });
  }

  date = (d: string) => frDate(d);
}
