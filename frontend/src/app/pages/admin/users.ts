import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Filiere, Role, User } from '../../core/models';
import { ROLE, apiError, frDate } from '../../core/labels';

interface UserForm {
  email: string; password: string; firstName: string; lastName: string; role: Role; phone: string;
  studentNumber: string; filiereId: number | null; specialties: string;
}

@Component({
  selector: 'app-users',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="page-head">
        <div><span class="eyebrow">Administration</span><h1>Utilisateurs</h1>
          <p>Comptes des enseignants et des étudiants. Chaque nouveau compte reçoit un e-mail de bienvenue.</p></div>
        @if (isAdmin()) { <button class="btn btn-primary" (click)="openForm()">✚ Nouvel utilisateur</button> }
      </div>
      @if (error()) { <div class="alert error" style="margin-bottom:16px">{{ error() }}</div> }
      <div class="toolbar">
        <input class="input search" placeholder="Nom, e-mail, numéro étudiant…" [(ngModel)]="q" (keyup.enter)="load()" />
        <select class="input" [(ngModel)]="role" (change)="load()">
          <option value="">Tous les rôles</option>
          @for (r of roles; track r) { <option [value]="r">{{ roleLabel[r] }}</option> }
        </select>
        <button class="btn btn-ghost" (click)="load()">Rechercher</button>
      </div>
      @if (loading()) { <div class="spinner"></div> } @else {
        <div class="card">
          <table class="table">
            <thead><tr><th>Nom</th><th>Rôle</th><th>Filière / spécialités</th><th>Dernière connexion</th><th>État</th>@if (isAdmin()) {<th></th>}</tr></thead>
            <tbody>
              @for (u of users(); track u.id) {
                <tr>
                  <td><strong>{{ u.fullName }}</strong><br /><small class="muted">{{ u.email }}@if (u.studentNumber) { · {{ u.studentNumber }} }</small></td>
                  <td>{{ roleLabel[u.role] }}</td>
                  <td class="small">{{ u.filiere?.code ?? '' }}{{ u.specialties ?? '' }}</td>
                  <td class="small">{{ u.lastLoginAt ? date(u.lastLoginAt) : '—' }}</td>
                  <td>@if (u.enabled) { <span class="badge teal">Actif</span> } @else { <span class="badge gray">Désactivé</span> }</td>
                  @if (isAdmin()) {
                    <td class="nowrap"><button class="btn btn-ghost btn-sm" (click)="openForm(u)">Modifier</button>
                      @if (u.id !== auth.user()?.id) {
                        <button class="btn-link small" (click)="toggle(u)">{{ u.enabled ? 'Désactiver' : 'Réactiver' }}</button> }</td>
                  }
                </tr>
              } @empty { <tr><td colspan="6" class="empty">Aucun utilisateur.</td></tr> }
            </tbody>
          </table>
        </div>
      }

      @if (formOpen()) {
        <div class="modal-backdrop" (click)="formOpen.set(false)">
          <form class="modal wide" (click)="$event.stopPropagation()" (ngSubmit)="save()" #f="ngForm">
            <h2>{{ editId ? 'Modifier l’utilisateur' : 'Nouvel utilisateur' }}</h2>
            @if (formError()) { <div class="alert error mt">{{ formError() }}</div> }
            <div class="form-grid mt">
              <div class="field"><label>Prénom *</label><input name="firstName" [(ngModel)]="model.firstName" required /></div>
              <div class="field"><label>Nom *</label><input name="lastName" [(ngModel)]="model.lastName" required /></div>
              @if (!editId) {
                <div class="field"><label>E-mail *</label><input type="email" name="email" [(ngModel)]="model.email" required /></div>
                <div class="field"><label>Mot de passe initial *</label><input name="password" [(ngModel)]="model.password" required minlength="8" /></div>
              }
              <div class="field"><label>Rôle *</label>
                <select name="role" [(ngModel)]="model.role">@for (r of roles; track r) { <option [value]="r">{{ roleLabel[r] }}</option> }</select></div>
              <div class="field"><label>Téléphone</label><input name="phone" [(ngModel)]="model.phone" /></div>
              @if (model.role === 'ETUDIANT') {
                <div class="field"><label>N° étudiant *</label><input name="studentNumber" [(ngModel)]="model.studentNumber" required /></div>
                <div class="field"><label>Filière *</label>
                  <select name="filiereId" [(ngModel)]="model.filiereId" required>
                    @for (fi of filieres(); track fi.id) { <option [ngValue]="fi.id">{{ fi.code }} — {{ fi.name }}</option> }
                  </select></div>
              } @else {
                <div class="field full"><label>Spécialités</label><input name="specialties" [(ngModel)]="model.specialties" placeholder="génie logiciel, devops, ia…" /></div>
              }
            </div>
            <div class="actions">
              <button type="button" class="btn btn-ghost" (click)="formOpen.set(false)">Annuler</button>
              <button class="btn btn-primary" [disabled]="!f.valid">Enregistrer</button>
            </div>
          </form>
        </div>
      }
    </div>
  `,
  styles: [`
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .wide { width: min(720px, 100%); }
  `]
})
export class Users implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  readonly roleLabel = ROLE;
  readonly roles = Object.keys(ROLE) as Role[];
  readonly users = signal<User[]>([]);
  readonly filieres = signal<Filiere[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly formError = signal('');
  readonly formOpen = signal(false);
  q = '';
  role: Role | '' = '';
  editId: number | null = null;
  model: UserForm = this.empty();

  isAdmin(): boolean { return this.auth.role() === 'ADMIN'; }

  ngOnInit(): void {
    this.api.filieres().subscribe(f => this.filieres.set(f));
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.users(this.role, this.q.trim()).subscribe({
      next: u => { this.users.set(u); this.loading.set(false); },
      error: err => { this.error.set(apiError(err)); this.loading.set(false); }
    });
  }

  private empty(): UserForm {
    return { email: '', password: '', firstName: '', lastName: '', role: 'ETUDIANT', phone: '', studentNumber: '', filiereId: null, specialties: '' };
  }

  openForm(u?: User): void {
    this.editId = u?.id ?? null;
    this.model = u ? {
      email: u.email, password: '', firstName: u.firstName, lastName: u.lastName, role: u.role, phone: u.phone ?? '',
      studentNumber: u.studentNumber ?? '', filiereId: u.filiere?.id ?? null, specialties: u.specialties ?? ''
    } : { ...this.empty(), filiereId: this.filieres()[0]?.id ?? null };
    this.formError.set('');
    this.formOpen.set(true);
  }

  save(): void {
    const m = this.model;
    const student = m.role === 'ETUDIANT';
    const common = {
      firstName: m.firstName.trim(), lastName: m.lastName.trim(), role: m.role, phone: m.phone || null,
      studentNumber: student ? m.studentNumber : null, filiereId: student ? m.filiereId : null, specialties: student ? null : m.specialties
    };
    const call = this.editId
      ? this.api.updateUser(this.editId, common)
      : this.api.createUser({ ...common, email: m.email.trim(), password: m.password });
    call.subscribe({
      next: () => { this.formOpen.set(false); this.load(); },
      error: err => this.formError.set(apiError(err))
    });
  }

  toggle(u: User): void {
    this.api.setUserEnabled(u.id, !u.enabled).subscribe({ next: () => this.load(), error: err => this.error.set(apiError(err)) });
  }

  date = (d: string) => frDate(d);
}
