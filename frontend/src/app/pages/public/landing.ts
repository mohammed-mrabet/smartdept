import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PublicApi } from '../../core/public-api.service';
import { ContactTopic, PublicOffer, PublicStats } from '../../core/models';
import { COUNTRY_FLAG, COUNTRY_LABEL, LEVELS, companyColor, initials } from '../../core/offers';
import { apiError } from '../../core/labels';
import { PublicNav } from './public-nav';

const SERVICES = [
  { icon: '🎓', tone: 'blue', title: 'PFE de bout en bout',
    text: 'Proposition et validation des sujets, candidatures classées par préférence, affectation automatique, jalons notés, jury et soutenances.' },
  { icon: '💼', tone: 'teal', title: 'Offres de stage agrégées',
    text: 'Des offres réelles récupérées chaque jour depuis 9 sources gratuites : France, Maroc, Canada, Belgique, Espagne et télétravail.' },
  { icon: '🎯', tone: 'violet', title: 'Recommandations personnalisées',
    text: 'Score de compatibilité entre vos compétences, votre niveau et chaque offre. Alertes e-mail dès qu’une offre vous correspond.' },
  { icon: '🗓', tone: 'amber', title: 'Scolarité',
    text: 'Emploi du temps généré automatiquement sans conflit, examens, notes, moyennes, absences et détection des étudiants en difficulté.' },
  { icon: '📄', tone: 'blue', title: 'Conventions de stage',
    text: 'Création, validation par le département et document prêt à imprimer, avec suivi de chaque étape.' },
  { icon: '🤖', tone: 'violet', title: 'Assistant IA',
    text: 'Amélioration des sujets, suggestion d’encadrant et de jury, chatbot qui répond aux questions sur la plateforme.' },
  { icon: '✉', tone: 'teal', title: 'Notifications et e-mails',
    text: 'Chaque étape importante est notifiée dans l’application et envoyée par e-mail sur votre vraie adresse (Gmail, Outlook, UMP…).' },
  { icon: '📊', tone: 'amber', title: 'Tableaux de bord',
    text: 'Indicateurs par rôle : avancement des projets, marché des stages par pays et par compétence, suivi des candidatures.' }
];

const AUDIENCES = [
  { title: 'Étudiants', icon: '🧑‍🎓', points: ['Trouver un sujet de PFE ou un stage adapté à son niveau', 'Suivre ses candidatures et ses jalons', 'Consulter emploi du temps, notes et absences'] },
  { title: 'Enseignants', icon: '👩‍🏫', points: ['Proposer des sujets et choisir les étudiants', 'Noter les jalons et participer aux jurys', 'Repérer les étudiants à accompagner'] },
  { title: 'Entreprises', icon: '🏢', points: ['Publier une offre auprès du département', 'Proposer un partenariat ou un sujet', 'Être encadrant externe d’un PFE'] }
];

const STEPS = [
  { n: '1', title: 'Créez votre compte', text: 'Inscription en 1 minute avec votre adresse e-mail, puis confirmation par un lien reçu par e-mail.' },
  { n: '2', title: 'Complétez votre profil', text: 'Filière, niveau et compétences : ce sont eux qui calculent vos recommandations.' },
  { n: '3', title: 'Postulez', text: 'Candidatez aux sujets de PFE ou enregistrez des offres de stage et suivez chaque étape.' },
  { n: '4', title: 'Soutenez', text: 'Jalons validés, convention signée, soutenance planifiée : tout est au même endroit.' }
];

const FAQ = [
  { q: 'Qui peut créer un compte ?', a: 'Les étudiants s’inscrivent eux-mêmes depuis cette page. Les comptes enseignants et administrateurs sont créés par le chef de département.' },
  { q: 'Puis-je utiliser mon adresse Gmail ou Outlook ?', a: 'Oui. L’adresse institutionnelle (@ump.ac.ma) est recommandée, mais toute adresse valide fonctionne : le lien de confirmation y est envoyé.' },
  { q: 'D’où viennent les offres de stage ?', a: 'De 9 API publiques et gratuites (France Travail, Adzuna, Jooble, Remotive, Arbeitnow…), en plus des offres publiées directement par le département et ses partenaires.' },
  { q: 'Quels niveaux sont concernés ?', a: 'Bac+2 (DUT, BTS), Bac+3 (Licence), Bac+4 (Master 1) et Bac+5 (Master 2, ingénieur). Chaque offre est classée automatiquement.' },
  { q: 'Je n’ai pas reçu l’e-mail de confirmation.', a: 'Vérifiez le dossier « Spam », puis utilisez « Renvoyer le lien » depuis la page de connexion. Le lien est valable 24 heures.' },
  { q: 'Mes données sont-elles protégées ?', a: 'Les mots de passe sont chiffrés (BCrypt), les liens envoyés par e-mail sont à usage unique et seules leurs empreintes sont conservées.' }
];

const TOPICS: { code: ContactTopic; label: string }[] = [
  { code: 'PARTENARIAT', label: 'Partenariat entreprise' },
  { code: 'OFFRE_STAGE', label: 'Proposer une offre de stage' },
  { code: 'INSCRIPTION', label: 'Question sur l’inscription' },
  { code: 'AUTRE', label: 'Autre question' }
];

@Component({
  selector: 'app-landing',
  imports: [FormsModule, RouterLink, PublicNav],
  template: `
    <app-public-nav [anchors]="true" [dark]="true" />

    <!-- ===== Hero ===== -->
    <section class="hero">
      <div class="hero-in">
        <div class="hero-copy">
          <span class="kicker">Département informatique · Faculté des Sciences d’Oujda</span>
          <h1>Votre stage, votre PFE et votre scolarité, <em>sur une seule plateforme.</em></h1>
          <p class="lead">SmartDept accompagne les étudiants de Bac+2 à Bac+5 : sujets de PFE, offres de stage en France, au Maroc, au Canada,
            en Belgique et en Espagne, emploi du temps, notes et soutenances.</p>
          <div class="row hero-cta">
            <a routerLink="/inscription" class="btn btn-lg btn-light">Créer mon compte étudiant</a>
            <a href="#offres" class="btn btn-lg btn-outline">Voir les offres</a>
          </div>
          <div class="trust">
            <span>✓ Gratuit</span><span>✓ Confirmation par e-mail</span><span>✓ Offres mises à jour chaque jour</span>
          </div>
        </div>

        <div class="hero-board" aria-label="Chiffres de la plateforme">
          <div class="board-head"><span class="dot"></span> En direct sur SmartDept</div>
          <div class="kpis">
            @for (k of kpis(); track k.label) {
              <div class="kpi"><strong>{{ k.value }}</strong><span>{{ k.label }}</span></div>
            }
          </div>
          <div class="board-levels">
            @for (l of levels(); track l.label) {
              <div class="lvl">
                <span class="lvl-name">{{ l.label }}</span>
                <span class="lvl-bar"><i [style.width.%]="l.pct"></i></span>
                <span class="lvl-n">{{ l.count }}</span>
              </div>
            }
          </div>
          <div class="board-flags">
            @for (c of countries(); track c.code) {
              <span class="flag" [title]="c.label">{{ c.flag }} {{ c.count }}</span>
            }
          </div>
        </div>
      </div>
    </section>

    <!-- ===== Services ===== -->
    <section id="services" class="sec">
      <div class="sec-in">
        <div class="sec-head">
          <span class="eyebrow">Services</span>
          <h2>Tout ce dont un département a besoin, de la rentrée à la soutenance</h2>
        </div>
        <div class="services">
          @for (s of services; track s.title) {
            <article class="svc">
              <span class="svc-ico" [class]="'bg-' + s.tone">{{ s.icon }}</span>
              <h3>{{ s.title }}</h3>
              <p>{{ s.text }}</p>
            </article>
          }
        </div>
      </div>
    </section>

    <!-- ===== Pour qui ===== -->
    <section class="sec alt">
      <div class="sec-in">
        <div class="sec-head">
          <span class="eyebrow">Pour qui ?</span>
          <h2>Un espace adapté à chaque rôle</h2>
        </div>
        <div class="aud">
          @for (a of audiences; track a.title) {
            <article class="aud-card">
              <div class="aud-top"><span class="aud-ico">{{ a.icon }}</span><h3>{{ a.title }}</h3></div>
              <ul>@for (p of a.points; track p) { <li>{{ p }}</li> }</ul>
            </article>
          }
        </div>
      </div>
    </section>

    <!-- ===== Dernières offres ===== -->
    <section id="offres" class="sec">
      <div class="sec-in">
        <div class="sec-head row between">
          <div>
            <span class="eyebrow">Offres de stage</span>
            <h2>Les dernières offres publiées</h2>
            <p class="text-2">Aperçu public : connectez-vous pour voir le lien de candidature, votre score de compatibilité et créer des alertes.</p>
          </div>
          <a routerLink="/inscription" class="btn btn-primary">Accéder à toutes les offres</a>
        </div>
        @if (offers() === null) {
          <div class="spinner"></div>
        } @else if (!offers()!.length) {
          <div class="empty card flat"><div class="big">💼</div>Les offres apparaîtront après la première synchronisation des sources.</div>
        } @else {
          <div class="offers">
            @for (o of offers(); track o.id) {
              <article class="offer">
                <div class="offer-top">
                  <span class="avatar" [style.background]="color(o.company)">{{ init(o.company) }}</span>
                  <div class="offer-co">
                    <strong>{{ o.company || 'Entreprise' }}</strong>
                    <small class="muted">{{ o.flag }} {{ o.location || o.countryLabel }}</small>
                  </div>
                </div>
                <h3 class="offer-title">{{ o.title }}</h3>
                <div class="row tags">
                  <span class="badge blue">{{ o.kindLabel }}</span>
                  <span class="badge violet">{{ o.levelLabel }}</span>
                  @if (o.remote) { <span class="badge teal">Télétravail</span> }
                </div>
                @if (o.skills.length) {
                  <div class="skills">@for (s of o.skills.slice(0, 4); track s) { <span class="chip">{{ s }}</span> }</div>
                }
                <div class="offer-foot muted small">
                  <span>{{ o.sourceLabel }}</span>
                  @if (o.publishedAt) { <span>· {{ o.publishedAt }}</span> }
                </div>
              </article>
            }
          </div>
        }
      </div>
    </section>

    <!-- ===== Niveaux ===== -->
    <section class="sec alt">
      <div class="sec-in">
        <div class="sec-head">
          <span class="eyebrow">Tous les niveaux</span>
          <h2>Des offres classées automatiquement de Bac+2 à Bac+5</h2>
        </div>
        <div class="levels">
          @for (l of levels(); track l.label) {
            <div class="level">
              <span class="level-n">{{ l.label }}</span>
              <span class="muted small">{{ l.diplomas }}</span>
              <strong>{{ l.count }}</strong><span class="muted small">offres actives</span>
            </div>
          }
        </div>
      </div>
    </section>

    <!-- ===== Parcours ===== -->
    <section id="parcours" class="sec">
      <div class="sec-in">
        <div class="sec-head">
          <span class="eyebrow">Comment ça marche</span>
          <h2>Quatre étapes, de l’inscription à la soutenance</h2>
        </div>
        <ol class="steps">
          @for (s of steps; track s.n) {
            <li><span class="step-n">{{ s.n }}</span><h3>{{ s.title }}</h3><p>{{ s.text }}</p></li>
          }
        </ol>
        <div class="band">
          <div>
            <h3>Prêt à commencer ?</h3>
            <p>Créez votre compte en une minute : vous recevrez un lien de confirmation sur votre adresse e-mail.</p>
          </div>
          <div class="row">
            <a routerLink="/inscription" class="btn btn-lg btn-light">Créer un compte</a>
            <a routerLink="/connexion" class="btn btn-lg btn-outline">J’ai déjà un compte</a>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== FAQ ===== -->
    <section id="faq" class="sec alt">
      <div class="sec-in faq-wrap">
        <div class="sec-head">
          <span class="eyebrow">Questions fréquentes</span>
          <h2>Vous vous posez une question ?</h2>
          <p class="text-2">Sinon, écrivez-nous avec le formulaire de contact juste en dessous.</p>
        </div>
        <div class="faq">
          @for (f of faq; track f.q) {
            <details><summary>{{ f.q }}</summary><p>{{ f.a }}</p></details>
          }
        </div>
      </div>
    </section>

    <!-- ===== Contact ===== -->
    <section id="contact" class="sec">
      <div class="sec-in contact">
        <div class="contact-copy">
          <span class="eyebrow">Contact</span>
          <h2>Entreprise, partenaire ou futur étudiant ?</h2>
          <p class="text-2">Proposez une offre de stage, un sujet de PFE ou un partenariat. Le chef de département reçoit votre message
            immédiatement et vous recevez un accusé de réception par e-mail.</p>
          <ul class="contact-info">
            <li>📍 Faculté des Sciences, Université Mohammed Premier, Oujda</li>
            <li>🏛 Département d’informatique</li>
          </ul>
        </div>
        <form class="card contact-form" (ngSubmit)="send()" #cf="ngForm">
          @if (sent()) {
            <div class="alert success">{{ sent() }}</div>
          }
          @if (contactError()) { <div class="alert error">{{ contactError() }}</div> }
          <div class="form-grid mt">
            <div class="field"><label for="c-name">Nom</label><input id="c-name" name="name" [(ngModel)]="contactForm.name" required maxlength="120" /></div>
            <div class="field"><label for="c-email">E-mail</label><input id="c-email" name="email" type="email" [(ngModel)]="contactForm.email" required email maxlength="150" /></div>
            <div class="field"><label for="c-org">Organisation <span class="muted">(facultatif)</span></label><input id="c-org" name="organisation" [(ngModel)]="contactForm.organisation" maxlength="150" /></div>
            <div class="field"><label for="c-topic">Sujet</label>
              <select id="c-topic" name="topic" [(ngModel)]="contactForm.topic" required>
                @for (t of topics; track t.code) { <option [value]="t.code">{{ t.label }}</option> }
              </select>
            </div>
            <div class="field full"><label for="c-msg">Message</label>
              <textarea id="c-msg" name="message" [(ngModel)]="contactForm.message" required minlength="10" maxlength="3000"></textarea>
            </div>
            <!-- champ piège invisible pour les robots -->
            <div class="hp" aria-hidden="true"><label>Site web <input name="website" tabindex="-1" autocomplete="off" [(ngModel)]="contactForm.website" /></label></div>
          </div>
          <button class="btn btn-primary mt" [disabled]="sending() || !cf.valid">{{ sending() ? 'Envoi…' : 'Envoyer le message' }}</button>
        </form>
      </div>
    </section>

    <footer class="foot">
      <div class="foot-in">
        <div>
          <div class="brand-f"><span class="logo">S</span> SmartDept</div>
          <p>Plateforme de gestion du département informatique · FSO Oujda · Projet de Master 2026.</p>
        </div>
        <div class="foot-links">
          <a href="#services">Services</a><a href="#offres">Offres</a><a href="#faq">FAQ</a><a href="#contact">Contact</a>
          <a routerLink="/connexion">Connexion</a><a routerLink="/inscription">Inscription</a>
        </div>
      </div>
    </footer>
  `,
  styles: [`
    :host { display: block; background: #fff; }
    .btn-lg { padding: 13px 22px; font-size: 15px; border-radius: 11px; }
    .btn-light { background: #fff; color: #0f1b3d; }
    .btn-light:hover { background: #e8efff; }
    .btn-outline { background: transparent; color: #fff; border-color: rgba(255,255,255,.35); }
    .btn-outline:hover { border-color: #fff; }

    /* hero */
    .hero { color: #dfe7fb; background:
        radial-gradient(900px 420px at 85% 0%, rgba(109,63,192,.45), transparent 60%),
        radial-gradient(700px 380px at 0% 100%, rgba(37,99,235,.40), transparent 60%),
        linear-gradient(170deg, #0b1530, #13245a); margin-top: -59px; padding-top: 59px; }
    .hero-in { max-width: 1200px; margin: 0 auto; padding: 72px 20px 84px; display: grid; grid-template-columns: 1.15fr .85fr; gap: 48px; align-items: center; }
    .kicker { display: inline-block; font-size: 12.5px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; color: #9fb4ea;
      border: 1px solid rgba(159,180,234,.3); padding: 6px 12px; border-radius: 999px; }
    .hero h1 { color: #fff; font-size: clamp(32px, 4.4vw, 52px); line-height: 1.08; letter-spacing: -.025em; margin: 22px 0 18px; font-weight: 800; }
    .hero h1 em { font-style: normal; background: linear-gradient(90deg, #8fb3ff, #c6a8ff); -webkit-background-clip: text; background-clip: text; color: transparent; }
    .lead { font-size: 17.5px; line-height: 1.6; color: #c3cde6; max-width: 600px; }
    .hero-cta { margin-top: 30px; gap: 12px; }
    .trust { display: flex; gap: 18px; flex-wrap: wrap; margin-top: 22px; color: #9fb0d6; font-size: 13.5px; }

    .hero-board { background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.12); border-radius: 20px; padding: 22px;
      box-shadow: 0 30px 80px rgba(0,0,0,.35); backdrop-filter: blur(6px); }
    .board-head { display: flex; align-items: center; gap: 8px; color: #c3cde6; font-size: 13px; font-weight: 700; }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: #34d399; box-shadow: 0 0 0 4px rgba(52,211,153,.2); }
    .kpis { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; margin: 16px 0; }
    .kpi { background: rgba(255,255,255,.06); border-radius: 12px; padding: 12px 14px; display: flex; flex-direction: column; }
    .kpi strong { color: #fff; font-size: 26px; line-height: 1.1; font-variant-numeric: tabular-nums; }
    .kpi span { color: #9fb0d6; font-size: 12.5px; }
    .board-levels { display: grid; gap: 8px; }
    .lvl { display: grid; grid-template-columns: 52px 1fr 34px; align-items: center; gap: 10px; font-size: 13px; }
    .lvl-name { color: #c3cde6; font-weight: 700; }
    .lvl-bar { height: 8px; background: rgba(255,255,255,.08); border-radius: 99px; overflow: hidden; }
    .lvl-bar i { display: block; height: 100%; border-radius: 99px; background: linear-gradient(90deg, #60a5fa, #a78bfa); min-width: 3px; }
    .lvl-n { color: #fff; text-align: right; font-variant-numeric: tabular-nums; }
    .board-flags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 16px; }
    .flag { background: rgba(255,255,255,.08); color: #fff; border-radius: 8px; padding: 4px 9px; font-size: 13px; }

    /* sections */
    .sec { padding: 84px 0; }
    .sec.alt { background: var(--bg); }
    .sec-in { max-width: 1200px; margin: 0 auto; padding: 0 20px; }
    .sec-head { margin-bottom: 34px; max-width: 760px; }
    .sec-head.row { max-width: none; align-items: flex-end; }
    .sec-head h2 { font-size: clamp(24px, 2.8vw, 34px); letter-spacing: -.02em; font-weight: 800; margin-top: 8px; }
    .sec-head p { margin-top: 10px; }
    .eyebrow { color: var(--primary-2); font-size: 12px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }

    .services { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
    .svc { border: 1px solid var(--border); border-radius: 16px; padding: 22px; transition: transform .15s, box-shadow .15s, border-color .15s; background: #fff; }
    .svc:hover { transform: translateY(-3px); box-shadow: 0 14px 34px rgba(16,24,40,.08); border-color: #c7d4f0; }
    .svc-ico { width: 46px; height: 46px; border-radius: 13px; display: grid; place-items: center; font-size: 22px; }
    .svc h3 { margin: 16px 0 8px; font-size: 16px; }
    .svc p { color: var(--text-2); font-size: 14px; line-height: 1.55; }

    .aud { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    .aud-card { background: #fff; border: 1px solid var(--border); border-radius: 16px; padding: 24px; }
    .aud-top { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
    .aud-ico { font-size: 28px; }
    .aud-card h3 { font-size: 18px; }
    .aud-card ul { margin: 0; padding: 0; list-style: none; display: grid; gap: 10px; }
    .aud-card li { padding-left: 24px; position: relative; color: var(--text-2); }
    .aud-card li::before { content: "✓"; position: absolute; left: 0; color: var(--teal); font-weight: 800; }

    .offers { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    .offer { border: 1px solid var(--border); border-radius: 14px; padding: 18px; display: flex; flex-direction: column; gap: 10px; background: #fff; }
    .offer-top { display: flex; gap: 12px; align-items: center; }
    .avatar { width: 40px; height: 40px; border-radius: 10px; color: #fff; display: grid; place-items: center; font-weight: 800; font-size: 14px; flex: none; }
    .offer-co { display: flex; flex-direction: column; min-width: 0; }
    .offer-co strong, .offer-co small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .offer-title { font-size: 15.5px; line-height: 1.35; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .tags { gap: 6px; }
    .skills { margin-bottom: -6px; }
    .offer-foot { margin-top: auto; display: flex; gap: 4px; }

    .levels { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
    .level { background: #fff; border: 1px solid var(--border); border-radius: 16px; padding: 22px; display: flex; flex-direction: column; gap: 2px; }
    .level-n { font-size: 26px; font-weight: 800; color: var(--primary); letter-spacing: -.02em; }
    .level strong { font-size: 30px; margin-top: 14px; font-variant-numeric: tabular-nums; }

    .steps { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; counter-reset: s; }
    .steps li { position: relative; padding: 22px; border-radius: 16px; border: 1px solid var(--border); }
    .step-n { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; font-weight: 800; color: #fff; background: var(--primary-2); }
    .steps h3 { margin: 14px 0 6px; }
    .steps p { color: var(--text-2); font-size: 14px; }
    .band { margin-top: 40px; border-radius: 20px; padding: 32px; display: flex; justify-content: space-between; align-items: center; gap: 20px; flex-wrap: wrap;
      color: #dfe7fb; background: radial-gradient(500px 200px at 90% 0%, rgba(109,63,192,.5), transparent 60%), linear-gradient(135deg, #0f1b3d, #1e3a8a); }
    .band h3 { color: #fff; font-size: 22px; }
    .band p { margin-top: 6px; color: #c3cde6; }

    .faq-wrap { display: grid; grid-template-columns: .8fr 1.2fr; gap: 40px; align-items: start; }
    .faq { display: grid; gap: 10px; }
    details { background: #fff; border: 1px solid var(--border); border-radius: 12px; padding: 14px 18px; }
    summary { cursor: pointer; font-weight: 700; list-style: none; display: flex; justify-content: space-between; gap: 12px; }
    summary::-webkit-details-marker { display: none; }
    summary::after { content: "+"; color: var(--primary-2); font-size: 20px; line-height: 1; }
    details[open] summary::after { content: "–"; }
    details p { margin-top: 10px; color: var(--text-2); }

    .contact { display: grid; grid-template-columns: .9fr 1.1fr; gap: 40px; align-items: start; }
    .contact-copy h2 { font-size: clamp(24px, 2.8vw, 32px); margin: 8px 0 12px; letter-spacing: -.02em; }
    .contact-info { list-style: none; padding: 0; margin: 22px 0 0; display: grid; gap: 8px; color: var(--text-2); }
    .contact-form { padding: 24px; }
    .hp { position: absolute; left: -10000px; width: 1px; height: 1px; overflow: hidden; }

    .foot { background: #0b1530; color: #9fb0d6; padding: 40px 0; }
    .foot-in { max-width: 1200px; margin: 0 auto; padding: 0 20px; display: flex; justify-content: space-between; gap: 24px; flex-wrap: wrap; }
    .brand-f { display: flex; align-items: center; gap: 10px; color: #fff; font-weight: 800; font-size: 18px; margin-bottom: 8px; }
    .brand-f .logo { width: 32px; height: 32px; border-radius: 9px; display: grid; place-items: center; background: linear-gradient(135deg, #6d3fc0, #2563eb); }
    .foot-links { display: flex; gap: 18px; flex-wrap: wrap; align-items: center; }
    .foot-links a { color: #c3cde6; }

    @media (max-width: 1020px) {
      .hero-in { grid-template-columns: 1fr; padding: 48px 20px 60px; }
      .services { grid-template-columns: repeat(2, 1fr); }
      .offers, .aud { grid-template-columns: repeat(2, 1fr); }
      .levels, .steps { grid-template-columns: repeat(2, 1fr); }
      .faq-wrap, .contact { grid-template-columns: 1fr; gap: 20px; }
    }
    @media (max-width: 640px) {
      .sec { padding: 56px 0; }
      .services, .offers, .aud, .levels, .steps { grid-template-columns: 1fr; }
      .hero-cta .btn { width: 100%; }
      .band { padding: 24px; }
    }
  `]
})
export class Landing {
  private readonly api = inject(PublicApi);

  readonly services = SERVICES;
  readonly audiences = AUDIENCES;
  readonly steps = STEPS;
  readonly faq = FAQ;
  readonly topics = TOPICS;

  readonly stats = signal<PublicStats | null>(null);
  readonly offers = signal<PublicOffer[] | null>(null);
  readonly sending = signal(false);
  readonly sent = signal('');
  readonly contactError = signal('');
  contactForm = { name: '', email: '', organisation: '', topic: 'PARTENARIAT' as ContactTopic, message: '', website: '' };

  readonly kpis = computed(() => {
    const s = this.stats();
    const v = (n?: number) => s ? (n ?? 0).toLocaleString('fr-FR') : '—';
    return [
      { label: 'offres de stage actives', value: v(s?.activeOffers) },
      { label: 'sujets de PFE publiés', value: v(s?.publishedSubjects) },
      { label: 'étudiants inscrits', value: v(s?.students) },
      { label: 'enseignants', value: v(s?.teachers) }
    ];
  });

  readonly levels = computed(() => {
    const by = this.stats()?.offersByLevel ?? {};
    const max = Math.max(1, ...LEVELS.map(l => by['BAC_' + l.years] ?? 0));
    return LEVELS.map(l => {
      const count = by['BAC_' + l.years] ?? 0;
      return { label: l.label, diplomas: l.diplomas, count, pct: Math.round(count * 100 / max) };
    });
  });

  readonly countries = computed(() => Object.entries(this.stats()?.offersByCountry ?? {})
    .filter(([, n]) => n > 0)
    .sort((a, b) => b[1] - a[1])
    .map(([code, count]) => ({ code, count, flag: COUNTRY_FLAG[code] ?? '🏳', label: COUNTRY_LABEL[code] ?? code })));

  constructor() {
    this.api.stats().subscribe({ next: s => this.stats.set(s), error: () => this.stats.set(null) });
    this.api.offers().subscribe({ next: o => this.offers.set(o), error: () => this.offers.set([]) });
  }

  color(name?: string): string { return companyColor(name); }
  init(name?: string): string { return initials(name); }

  send(): void {
    this.sending.set(true);
    this.sent.set('');
    this.contactError.set('');
    this.api.contact(this.contactForm).subscribe({
      next: r => {
        this.sent.set(r.message);
        this.contactForm = { name: '', email: '', organisation: '', topic: 'PARTENARIAT', message: '', website: '' };
        this.sending.set(false);
      },
      error: e => { this.contactError.set(apiError(e)); this.sending.set(false); }
    });
  }
}
