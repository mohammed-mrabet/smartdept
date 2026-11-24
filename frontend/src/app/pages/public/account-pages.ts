import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Filiere, RegisterResponse } from '../../core/models';
import { PublicApi, passwordScore, passwordValid } from '../../core/public-api.service';
import { apiError } from '../../core/labels';
import { PublicNav } from './public-nav';

const LEVEL_LABEL: Record<string, string> = { BAC_2: 'Bac+2', BAC_3: 'Bac+3', BAC_4: 'Bac+4', BAC_5: 'Bac+5' };
const STRENGTH = ['Trop court', 'Faible', 'Moyen', 'Bon', 'Excellent'];

/** Mise en page commune aux pages de compte : colonne d'explication + carte de formulaire. */
const SHARED_STYLES = `
  :host { display: block; min-height: 100vh; background: var(--bg); }
  .acc { max-width: 1080px; margin: 0 auto; padding: 48px 20px 64px; display: grid; grid-template-columns: .85fr 1.15fr; gap: 40px; align-items: start; }
  .acc.single { grid-template-columns: minmax(0, 520px); justify-content: center; }
  .side h1 { font-size: 30px; letter-spacing: -.02em; margin: 10px 0 12px; }
  .side p { color: var(--text-2); }
  .side ul { list-style: none; padding: 0; margin: 22px 0 0; display: grid; gap: 12px; }
  .side li { display: flex; gap: 12px; align-items: flex-start; color: var(--text-2); }
  .side li b { color: var(--text); display: block; }
  .side .ico { width: 34px; height: 34px; border-radius: 10px; display: grid; place-items: center; flex: none; }
  .eyebrow { color: var(--primary-2); font-size: 12px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
  form.card, .card.box { padding: 28px; }
  .full { width: 100%; }
  .meter { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; margin-top: 6px; }
  .meter i { height: 5px; border-radius: 9px; background: var(--track); }
  .meter i.on.s1 { background: var(--red); } .meter i.on.s2 { background: var(--amber); }
  .meter i.on.s3 { background: var(--primary-2); } .meter i.on.s4 { background: var(--teal); }
  .pw { position: relative; }
  .pw button { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); border: 0; background: none; color: var(--muted); cursor: pointer; font-size: 12.5px; }
  .done { text-align: center; }
  .done .big { font-size: 46px; }
  .done h2 { margin: 8px 0; }
  .channel { text-align: left; margin-top: 16px; }
  .terms { grid-column: 1 / -1; align-items: flex-start; color: var(--text-2); font-size: 13.5px; }
  .terms input { margin-top: 3px; }
  .links { display: flex; justify-content: space-between; gap: 10px; margin-top: 18px; font-size: 14px; flex-wrap: wrap; }
  @media (max-width: 860px) { .acc { grid-template-columns: 1fr; padding-top: 28px; } }
`;

// =====================================================================
// Inscription
// =====================================================================
@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink, PublicNav],
  template: `
    <app-public-nav />
    <div class="acc">
      <aside class="side">
        <span class="eyebrow">Inscription étudiant</span>
        <h1>Créez votre compte SmartDept</h1>
        <p>Une minute suffit. Un lien de confirmation est envoyé sur votre adresse e-mail (Gmail, Outlook, @ump.ac.ma…).</p>
        <ul>
          <li><span class="ico bg-blue">🎓</span><div><b>Sujets de PFE</b>Candidatez à 3 sujets classés par préférence.</div></li>
          <li><span class="ico bg-teal">💼</span><div><b>Offres recommandées</b>Score de compatibilité avec vos compétences et votre niveau.</div></li>
          <li><span class="ico bg-violet">🔔</span><div><b>Alertes e-mail</b>Soyez prévenu dès qu’une offre vous correspond.</div></li>
          <li><span class="ico bg-amber">🗓</span><div><b>Scolarité</b>Emploi du temps, examens, notes et absences.</div></li>
        </ul>
      </aside>

      @if (done(); as d) {
        <div class="card box done">
          <div class="big">✉</div>
          <h2>Vérifiez votre boîte de réception</h2>
          <p class="text-2">{{ d.message }}</p>
          @if (d.emailChannel === 'MAILPIT') {
            <div class="alert warn channel">
              <b>E-mail de test :</b> {{ d.emailReason }}. Le message a été déposé dans Mailpit (boîte de test).
              @if (d.mailpitUrl) { <br /><a [href]="d.mailpitUrl" target="_blank" rel="noopener">Ouvrir Mailpit ↗</a> }
            </div>
          } @else if (d.emailChannel === 'SMTP') {
            <div class="alert success channel">Envoyé à votre vraie adresse. Pensez à regarder le dossier « Spam ».</div>
          } @else {
            <div class="alert info channel">L’envoi d’e-mails est désactivé sur ce serveur : contactez l’administration pour activer votre compte.</div>
          }
          <div class="links">
            <button class="btn-link" (click)="resend(d.email)" [disabled]="resending()">{{ resending() ? 'Envoi…' : 'Renvoyer le lien' }}</button>
            <a routerLink="/connexion">Aller à la connexion →</a>
          </div>
          @if (resendMsg()) { <div class="alert info mt">{{ resendMsg() }}</div> }
        </div>
      } @else {
        <form class="card" (ngSubmit)="submit()" #f="ngForm" novalidate>
          <h2>Vos informations</h2>
          @if (error()) { <div class="alert error mt">{{ error() }}</div> }
          <div class="form-grid mt">
            <div class="field"><label for="fn">Prénom</label><input id="fn" name="firstName" [(ngModel)]="form.firstName" required maxlength="80" autocomplete="given-name" /></div>
            <div class="field"><label for="ln">Nom</label><input id="ln" name="lastName" [(ngModel)]="form.lastName" required maxlength="80" autocomplete="family-name" /></div>
            <div class="field full"><label for="em">Adresse e-mail</label>
              <input id="em" name="email" type="email" [(ngModel)]="form.email" required email maxlength="150" autocomplete="email" />
              <span class="hint">L’adresse institutionnelle est recommandée ; Gmail et Outlook fonctionnent aussi.</span>
            </div>
            <div class="field full"><label for="pw">Mot de passe</label>
              <div class="pw">
                <input id="pw" name="password" [type]="showPw() ? 'text' : 'password'" [(ngModel)]="form.password" (ngModelChange)="pw.set($event)" required minlength="8" autocomplete="new-password" />
                <button type="button" (click)="showPw.set(!showPw())">{{ showPw() ? 'Masquer' : 'Afficher' }}</button>
              </div>
              <div class="meter">@for (i of [1,2,3,4]; track i) { <i [class]="score() >= i ? 'on s' + score() : ''"></i> }</div>
              <span class="hint">{{ strength() }} · au moins 8 caractères, une lettre et un chiffre.</span>
            </div>
            <div class="field"><label for="fi">Filière</label>
              <select id="fi" name="filiereId" [(ngModel)]="form.filiereId" required>
                <option [ngValue]="null" disabled>Choisir…</option>
                @for (fl of filieres(); track fl.id) {
                  <option [ngValue]="fl.id">{{ fl.name }}{{ fl.studyLevel ? ' · ' + levelLabel[fl.studyLevel] : '' }}</option>
                }
              </select>
            </div>
            <div class="field"><label for="sn">N° étudiant <span class="muted">(facultatif)</span></label>
              <input id="sn" name="studentNumber" [(ngModel)]="form.studentNumber" maxlength="30" />
            </div>
            <div class="field full"><label for="sk">Compétences <span class="muted">(facultatif)</span></label>
              <input id="sk" name="skills" [(ngModel)]="form.skills" maxlength="500" placeholder="Java, Spring Boot, Angular, Docker…" />
              <span class="hint">Séparées par des virgules : elles servent à vous recommander des offres.</span>
            </div>
            <label class="check terms"><input type="checkbox" name="acceptTerms" [(ngModel)]="form.acceptTerms" required />
              J’accepte que mes données soient utilisées par le département pour la gestion des stages et de la scolarité.</label>
          </div>
          <button class="btn btn-primary full mt" [disabled]="loading() || !f.valid || !valid()">{{ loading() ? 'Création…' : 'Créer mon compte' }}</button>
          <div class="links"><span class="muted">Déjà inscrit ?</span><a routerLink="/connexion">Se connecter</a></div>
        </form>
      }
    </div>
  `,
  styles: [SHARED_STYLES]
})
export class Register {
  private readonly api = inject(PublicApi);

  readonly levelLabel = LEVEL_LABEL;
  readonly filieres = signal<Filiere[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly done = signal<RegisterResponse | null>(null);
  readonly showPw = signal(false);
  readonly resending = signal(false);
  readonly resendMsg = signal('');
  readonly pw = signal('');
  readonly score = computed(() => passwordScore(this.pw()));
  readonly strength = computed(() => STRENGTH[this.score()]);

  form = { firstName: '', lastName: '', email: '', password: '', filiereId: null as number | null, studentNumber: '', skills: '', acceptTerms: false };

  constructor() {
    this.api.filieres().subscribe({ next: f => this.filieres.set(f) });
  }

  valid(): boolean { return passwordValid(this.form.password) && this.form.acceptTerms && this.form.filiereId != null; }

  submit(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.register({ ...this.form, email: this.form.email.trim() }).subscribe({
      next: r => { this.done.set(r); this.loading.set(false); },
      error: e => { this.error.set(apiError(e)); this.loading.set(false); }
    });
  }

  resend(email: string): void {
    this.resending.set(true);
    this.api.resendVerification(email).subscribe({
      next: r => { this.resendMsg.set(r.message); this.resending.set(false); },
      error: e => { this.resendMsg.set(apiError(e)); this.resending.set(false); }
    });
  }
}

// =====================================================================
// Confirmation de l'adresse (lien reçu par e-mail)
// =====================================================================
@Component({
  selector: 'app-verify-email',
  imports: [RouterLink, PublicNav],
  template: `
    <app-public-nav />
    <div class="acc single">
      <div class="card box done">
        @if (state() === 'loading') {
          <div class="spinner"></div><p class="text-2">Confirmation de votre adresse…</p>
        } @else if (state() === 'ok') {
          <div class="big">✅</div>
          <h2>Adresse confirmée</h2>
          <p class="text-2">Bienvenue {{ name() }} ! Redirection vers votre espace…</p>
        } @else {
          <div class="big">⚠</div>
          <h2>Lien invalide</h2>
          <p class="text-2">{{ error() }}</p>
          <div class="links"><a routerLink="/connexion">Demander un nouveau lien depuis la connexion</a><a routerLink="/">Accueil</a></div>
        }
      </div>
    </div>
  `,
  styles: [SHARED_STYLES]
})
export class VerifyEmail {
  private readonly api = inject(PublicApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly state = signal<'loading' | 'ok' | 'error'>('loading');
  readonly error = signal('');
  readonly name = signal('');

  constructor() {
    const token = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';
    if (!token) {
      this.state.set('error');
      this.error.set('Le lien ne contient pas de jeton de confirmation.');
      return;
    }
    this.api.verifyEmail(token).subscribe({
      next: res => {
        this.auth.startSession(res);
        this.name.set(res.user.firstName);
        this.state.set('ok');
        setTimeout(() => this.router.navigate(['/profil']), 1500);
      },
      error: e => { this.state.set('error'); this.error.set(apiError(e)); }
    });
  }
}

// =====================================================================
// Mot de passe oublié
// =====================================================================
@Component({
  selector: 'app-forgot-password',
  imports: [FormsModule, RouterLink, PublicNav],
  template: `
    <app-public-nav />
    <div class="acc single">
      <form class="card" (ngSubmit)="submit()" #f="ngForm">
        <span class="eyebrow">Mot de passe oublié</span>
        <h2 class="mt">Recevoir un lien de réinitialisation</h2>
        <p class="text-2 mt">Saisissez l’adresse de votre compte : un lien valable 1 heure vous sera envoyé.</p>
        @if (message()) {
          <div class="alert success mt">{{ message() }}</div>
          @if (mailpitUrl()) {
            <p class="muted small mt">Adresse de démonstration ? Les e-mails de test arrivent dans <a [href]="mailpitUrl()" target="_blank" rel="noopener">Mailpit ↗</a>.</p>
          }
        }
        @if (error()) { <div class="alert error mt">{{ error() }}</div> }
        <div class="field mt"><label for="em">Adresse e-mail</label>
          <input id="em" name="email" type="email" [(ngModel)]="email" required email autocomplete="email" />
        </div>
        <button class="btn btn-primary full mt" [disabled]="loading() || !f.valid">{{ loading() ? 'Envoi…' : 'Envoyer le lien' }}</button>
        <div class="links"><a routerLink="/connexion">← Retour à la connexion</a><a routerLink="/inscription">Créer un compte</a></div>
      </form>
    </div>
  `,
  styles: [SHARED_STYLES]
})
export class ForgotPassword {
  private readonly api = inject(PublicApi);
  readonly loading = signal(false);
  readonly message = signal('');
  readonly mailpitUrl = signal('');
  readonly error = signal('');
  email = '';

  submit(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.forgotPassword(this.email.trim()).subscribe({
      next: r => { this.message.set(r.message); this.mailpitUrl.set(r.mailpitUrl ?? ''); this.loading.set(false); },
      error: e => { this.error.set(apiError(e)); this.loading.set(false); }
    });
  }
}

// =====================================================================
// Nouveau mot de passe (lien reçu par e-mail)
// =====================================================================
@Component({
  selector: 'app-reset-password',
  imports: [FormsModule, RouterLink, PublicNav],
  template: `
    <app-public-nav />
    <div class="acc single">
      <form class="card" (ngSubmit)="submit()" #f="ngForm">
        <span class="eyebrow">Nouveau mot de passe</span>
        <h2 class="mt">Choisissez un nouveau mot de passe</h2>
        @if (!token) { <div class="alert error mt">Lien incomplet : refaites une demande depuis « Mot de passe oublié ».</div> }
        @if (done()) {
          <div class="alert success mt">{{ done() }}</div>
          <a routerLink="/connexion" class="btn btn-primary full mt">Se connecter</a>
        } @else {
          @if (error()) { <div class="alert error mt">{{ error() }}</div> }
          <div class="field mt"><label for="pw">Nouveau mot de passe</label>
            <input id="pw" name="password" type="password" [(ngModel)]="password" required minlength="8" autocomplete="new-password" />
            <span class="hint">Au moins 8 caractères, une lettre et un chiffre.</span>
          </div>
          <div class="field mt"><label for="pw2">Confirmation</label>
            <input id="pw2" name="confirm" type="password" [(ngModel)]="confirm" required autocomplete="new-password" />
            @if (confirm && confirm !== password) { <span class="hint" style="color: var(--red)">Les deux mots de passe sont différents.</span> }
          </div>
          <button class="btn btn-primary full mt" [disabled]="loading() || !f.valid || !token || !ok()">{{ loading() ? 'Enregistrement…' : 'Enregistrer' }}</button>
          <div class="links"><a routerLink="/mot-de-passe-oublie">Demander un nouveau lien</a><a routerLink="/connexion">Connexion</a></div>
        }
      </form>
    </div>
  `,
  styles: [SHARED_STYLES]
})
export class ResetPassword {
  private readonly api = inject(PublicApi);
  readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';
  readonly loading = signal(false);
  readonly done = signal('');
  readonly error = signal('');
  password = '';
  confirm = '';

  ok(): boolean { return passwordValid(this.password) && this.password === this.confirm; }

  submit(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.resetPassword(this.token, this.password).subscribe({
      next: r => { this.done.set(r.message); this.loading.set(false); },
      error: e => { this.error.set(apiError(e)); this.loading.set(false); }
    });
  }
}
