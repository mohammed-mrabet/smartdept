import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ROLE, apiError, frDate } from '../../core/labels';

@Component({
  selector: 'app-profile',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="page-head"><div><span class="eyebrow">Compte</span><h1>Mon profil</h1></div></div>
      @if (auth.user(); as u) {
        <div class="grid grid-2">
          <form class="card" (ngSubmit)="save()">
            <div class="row"><span class="avatar">{{ u.firstName[0] }}{{ u.lastName[0] }}</span>
              <div><h2>{{ u.fullName }}</h2><span class="muted small">{{ role[u.role] }} · {{ u.email }}</span></div></div>
            @if (saved()) { <div class="alert success mt">{{ saved() }}</div> }
            @if (error()) { <div class="alert error mt">{{ error() }}</div> }
            <div class="form-grid mt-lg">
              <div class="field"><label>Prénom</label><input name="firstName" [(ngModel)]="form.firstName" required /></div>
              <div class="field"><label>Nom</label><input name="lastName" [(ngModel)]="form.lastName" required /></div>
              <div class="field"><label>Téléphone</label><input name="phone" [(ngModel)]="form.phone" /></div>
              @if (u.studentNumber) { <div class="field"><label>N° étudiant</label><input [value]="u.studentNumber" disabled /></div> }
              @if (u.filiere) { <div class="field"><label>Filière</label><input [value]="u.filiere.code + ' — ' + u.filiere.name" disabled /></div> }
              @if (u.role !== 'ETUDIANT') {
                <div class="field full"><label>Spécialités</label><input name="specialties" [(ngModel)]="form.specialties" placeholder="génie logiciel, devops, ia…" />
                  <span class="hint">Utilisées par l’IA pour proposer les encadrants et les jurys.</span></div>
              }
              <div class="field full">
                <label class="check"><input type="checkbox" name="emailNotifications" [(ngModel)]="form.emailNotifications" />
                  Recevoir aussi les notifications par e-mail</label>
              </div>
            </div>
            <button class="btn btn-primary mt">Enregistrer</button>
            @if (u.lastLoginAt) { <p class="muted small mt">Dernière connexion : {{ date(u.lastLoginAt) }}</p> }
          </form>

          <form class="card" (ngSubmit)="changePassword()" #pf="ngForm">
            <h2>Mot de passe</h2>
            @if (pwdMsg()) { <div class="alert success mt">{{ pwdMsg() }}</div> }
            @if (pwdError()) { <div class="alert error mt">{{ pwdError() }}</div> }
            <div class="stack mt">
              <div class="field"><label>Mot de passe actuel</label><input type="password" name="current" [(ngModel)]="current" required autocomplete="current-password" /></div>
              <div class="field"><label>Nouveau mot de passe</label><input type="password" name="next" [(ngModel)]="next" required minlength="8" autocomplete="new-password" />
                <span class="hint">8 caractères minimum, avec une majuscule, une minuscule et un chiffre.</span></div>
              <div class="field"><label>Confirmation</label><input type="password" name="confirm" [(ngModel)]="confirm" required autocomplete="new-password" /></div>
            </div>
            <button class="btn btn-primary mt" [disabled]="!pf.valid || next !== confirm">Changer le mot de passe</button>
            @if (confirm && next !== confirm) { <p class="small mt" style="color:var(--red)">Les deux mots de passe ne correspondent pas.</p> }
          </form>
        </div>
      }
    </div>
  `,
  styles: [`
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .avatar { width: 52px; height: 52px; border-radius: 50%; display: grid; place-items: center; background: var(--primary-soft); color: var(--primary-2); font-weight: 800; font-size: 18px; }
  `]
})
export class Profile implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  readonly role = ROLE;
  readonly saved = signal('');
  readonly error = signal('');
  readonly pwdMsg = signal('');
  readonly pwdError = signal('');
  form = { firstName: '', lastName: '', phone: '', specialties: '', emailNotifications: true };
  current = '';
  next = '';
  confirm = '';

  ngOnInit(): void {
    const u = this.auth.user();
    if (u) this.form = { firstName: u.firstName, lastName: u.lastName, phone: u.phone ?? '', specialties: u.specialties ?? '', emailNotifications: u.emailNotifications };
  }

  save(): void {
    this.api.updateMe(this.form).subscribe({
      next: u => { this.auth.setUser(u); this.saved.set('Profil enregistré.'); this.error.set(''); },
      error: err => { this.error.set(apiError(err)); this.saved.set(''); }
    });
  }

  changePassword(): void {
    this.api.changePassword(this.current, this.next).subscribe({
      next: () => { this.pwdMsg.set('Mot de passe modifié.'); this.pwdError.set(''); this.current = this.next = this.confirm = ''; },
      error: err => { this.pwdError.set(apiError(err)); this.pwdMsg.set(''); }
    });
  }

  date = (d: string) => frDate(d);
}
