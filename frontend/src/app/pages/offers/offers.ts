import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { apiError, frDate, timeAgo } from '../../core/labels';
import {
  COUNTRIES, COUNTRY_FLAG, COUNTRY_LABEL, CoverLetter, Facets, KINDS, LEVELS, MatchResult, Offer, OfferApi, OfferCountry,
  OfferFilters, OfferKind, OfferSource, TRACKING, TrackingStatus, companyColor, initials
} from '../../core/offers';

/**
 * Recherche d'offres : niveau (Bac+2 à Bac+5), pays, type, source, télétravail ; liste et fiche détaillée côte à côte.
 * Pour un étudiant : score de correspondance, recommandations, analyse IA et lettre de motivation.
 */
@Component({
  selector: 'app-offers',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page offers-page">
      <!-- ============ bandeau de recherche ============ -->
      <section class="hero">
        <div class="hero-text">
          <span class="eyebrow">Stages · PFE · Alternance · Premier emploi</span>
          <h1>Trouvez le stage qui correspond à votre niveau</h1>
          <p>Offres du Maroc, de France, du Canada, de Belgique, d’Espagne et en télétravail, rassemblées depuis
            {{ sourceCount() }} sources et mises à jour toutes les 6 heures.</p>
        </div>
        <form class="search" (ngSubmit)="apply()">
          <span class="ico">⌕</span>
          <input name="q" [(ngModel)]="q" placeholder="Métier, technologie, entreprise… ex. Spring Boot, data, cybersécurité" autocomplete="off" />
          <select name="country" [(ngModel)]="country" (change)="apply()">
            <option value="">Tous les pays</option>
            @for (c of countries; track c.code) { <option [value]="c.code">{{ c.flag }} {{ c.label }}</option> }
          </select>
          <button class="btn btn-primary">Rechercher</button>
        </form>
        <div class="hero-actions">
          <button class="btn-link light" (click)="live()" [disabled]="liveBusy()" title="Interroger les API maintenant">
            {{ liveBusy() ? 'Interrogation des API…' : '⚡ Chercher en direct sur les API' }}</button>
          <span class="sep">·</span>
          <button class="btn-link light" (click)="alertOpen.set(true)">🔔 Créer une alerte avec ces critères</button>
          <span class="sep">·</span>
          <a class="light" routerLink="/offres/suivi">📋 Mon suivi de candidatures</a>
        </div>
      </section>

      @if (message()) { <div class="alert success mt">{{ message() }}</div> }
      @if (error()) { <div class="alert error mt">{{ error() }}</div> }

      <!-- ============ niveaux ============ -->
      <div class="levels" role="radiogroup" aria-label="Niveau d'études">
        <button class="level" [class.on]="level === null" (click)="setLevel(null)">
          <strong>Tous niveaux</strong><small>{{ total() }} offre(s)</small>
        </button>
        @for (l of levels; track l.years) {
          <button class="level" [class.on]="level === l.years" (click)="setLevel(l.years)">
            <span class="lvl-top"><strong>{{ l.label }}</strong>@if (myLevel() === l.years) { <span class="me">mon niveau</span> }</span>
            <small>{{ l.diplomas }}</small>
            <span class="count">{{ facets()?.levels?.['BAC_' + l.years] ?? 0 }}</span>
          </button>
        }
      </div>

      <!-- ============ filtres ============ -->
      <div class="filters">
        <div class="chips">
          @for (c of countries; track c.code) {
            <button class="fchip" [class.on]="country === c.code" (click)="toggleCountry(c.code)">
              {{ c.flag }} {{ c.label }} <em>{{ facets()?.countries?.[c.code] ?? 0 }}</em></button>
          }
        </div>
        <div class="chips">
          @for (k of kinds; track k.code) {
            <button class="fchip" [class.on]="kind === k.code" (click)="toggleKind(k.code)">{{ k.icon }} {{ k.label }}
              <em>{{ facets()?.kinds?.[k.code] ?? 0 }}</em></button>
          }
          <label class="fchip check"><input type="checkbox" [(ngModel)]="remote" (change)="apply()" /> Télétravail</label>
          @if (level !== null) {
            <label class="fchip check" title="Beaucoup d'annonces ne précisent pas le diplôme visé">
              <input type="checkbox" [(ngModel)]="includeUnknown" (change)="apply()" /> + niveau non précisé ({{ facets()?.unknownLevel ?? 0 }})</label>
          }
          <select class="input slim" [(ngModel)]="source" (change)="apply()">
            <option value="">Toutes les sources</option>
            @for (s of sourceOptions(); track s.code) { <option [value]="s.code">{{ s.label }} ({{ s.count }})</option> }
          </select>
          <select class="input slim" [(ngModel)]="sort" (change)="apply()">
            <option value="recent">Plus récentes</option>
            @if (auth.isStudent()) { <option value="match">Meilleure correspondance ✦</option> }
          </select>
          @if (hasFilters()) { <button class="btn-link small" (click)="reset()">Effacer les filtres</button> }
        </div>
      </div>

      <!-- ============ recommandations ============ -->
      @if (auth.isStudent() && recommended().length && !hasFilters()) {
        <section class="reco">
          <div class="row between"><h2>✦ Sélectionnées pour vous</h2><small class="muted">d’après votre niveau et vos compétences (Mon profil)</small></div>
          <div class="reco-row">
            @for (o of recommended(); track o.id) {
              <button class="reco-card" (click)="open(o)">
                <span class="ring sm" [style.--p]="o.match ?? 0">{{ o.match }}%</span>
                <strong>{{ o.title }}</strong>
                <small>{{ o.flag }} {{ o.company }}</small>
              </button>
            }
          </div>
        </section>
      }

      <!-- ============ résultats ============ -->
      <div class="results" [class.with-detail]="!!selected()">
        <div class="list">
          <div class="list-head"><strong>{{ total() }}</strong> offre(s)@if (loading()) { <span class="spinner-inline"></span> }</div>
          @for (o of items(); track o.id) {
            <article class="ocard" [class.active]="selected()?.id === o.id" (click)="open(o)" tabindex="0" (keyup.enter)="open(o)">
              <div class="avatar" [style.background]="color(o.company)">{{ init(o.company) }}</div>
              <div class="body">
                <div class="row between top">
                  <h3>{{ o.title }}</h3>
                  @if (o.match != null) { <span class="ring" [style.--p]="o.match" [title]="'Correspondance avec votre profil : ' + o.match + ' %'">{{ o.match }}</span> }
                </div>
                <div class="meta">{{ o.company || 'Entreprise non précisée' }} · {{ o.flag }} {{ o.location || o.countryLabel }}</div>
                <div class="tags">
                  <span class="badge" [class]="'badge ' + kindColor(o.kind)">{{ o.kindLabel }}</span>
                  <span class="badge" [class.violet]="o.levelMin" [class.gray]="!o.levelMin">{{ o.levelLabel }}</span>
                  @if (o.remote) { <span class="badge teal">Télétravail</span> }
                  @if (o.durationMonths) { <span class="badge">{{ o.durationMonths }} mois</span> }
                  @for (s of o.skills.slice(0, 4); track s) { <span class="chip">{{ s }}</span> }
                </div>
                <div class="foot">
                  <span class="src" [class.internal]="o.internal">{{ o.internal ? '🏛 Offre du département' : 'via ' + o.sourceLabel }}</span>
                  <span class="muted">{{ ago(o.publishedAt) }}</span>
                  @if (o.tracking) { <span class="badge blue">{{ trackingLabel(o.tracking) }}</span> }
                  <button class="star" [class.on]="!!o.tracking" (click)="toggleSave(o, $event)" [attr.aria-label]="o.tracking ? 'Retirer du suivi' : 'Enregistrer'">{{ o.tracking ? '★' : '☆' }}</button>
                </div>
              </div>
            </article>
          } @empty {
            @if (!loading()) {
              <div class="card empty"><div class="big">🔎</div>
                <p>Aucune offre ne correspond à ces critères.</p>
                <p class="small muted">Élargissez la recherche, cochez « niveau non précisé », ou lancez une recherche en direct sur les API.</p>
                <button class="btn btn-ghost btn-sm mt" (click)="reset()">Effacer les filtres</button></div>
            }
          }
          @if (page() + 1 < totalPages()) {
            <button class="btn btn-ghost more" (click)="loadMore()" [disabled]="loading()">Afficher plus d’offres</button>
          }
        </div>

        <!-- ============ fiche détaillée ============ -->
        @if (selected(); as o) {
          <aside class="detail card">
            <button class="close btn-link" (click)="closeDetail()" aria-label="Fermer">✕</button>
            <div class="d-head">
              <div class="avatar lg" [style.background]="color(o.company)">{{ init(o.company) }}</div>
              <div>
                <h2>{{ o.title }}</h2>
                <p class="text-2">{{ o.company }} · {{ o.flag }} {{ o.location || o.countryLabel }}</p>
              </div>
            </div>
            <div class="facts">
              <div><small>Type</small><strong>{{ o.kindLabel }}</strong></div>
              <div><small>Niveau</small><strong>{{ o.levelLabel }}</strong></div>
              <div><small>Durée</small><strong>{{ o.durationMonths ? o.durationMonths + ' mois' : '—' }}</strong></div>
              <div><small>Rémunération</small><strong>{{ o.salary || '—' }}</strong></div>
              <div><small>Publiée</small><strong>{{ date(o.publishedAt) }}</strong></div>
              <div><small>Lieu</small><strong>{{ o.remote ? 'Télétravail possible' : 'Sur site' }}</strong></div>
            </div>
            <div class="actions-row">
              @if (o.url) { <a class="btn btn-primary" [href]="o.url" target="_blank" rel="noopener">Postuler sur le site ↗</a> }
              @if (o.contactEmail) { <a class="btn btn-ghost" [href]="'mailto:' + o.contactEmail + '?subject=' + encode('Candidature : ' + o.title)">✉ Écrire au recruteur</a> }
              <select class="input slim" [ngModel]="o.tracking ?? ''" (ngModelChange)="setTracking(o, $event)" aria-label="Suivi">
                <option value="">☆ Suivre cette offre…</option>
                @for (t of tracking; track t.code) { <option [value]="t.code">{{ t.label }}</option> }
              </select>
            </div>

            @if (auth.isStudent()) {
              <div class="ai-box">
                <div class="row between">
                  <strong>✦ Analyse de correspondance</strong>
                  @if (!matchResult()) { <button class="btn btn-ai btn-sm" (click)="analyze(o)" [disabled]="aiBusy()">Analyser mon profil</button> }
                </div>
                @if (matchResult(); as m) {
                  <div class="match">
                    <span class="ring lg" [style.--p]="m.score">{{ m.score }}%</span>
                    <div><strong>{{ m.verdict }}</strong>
                      <div class="mt-xs">@for (s of m.matchedSkills; track s) { <span class="chip ok">✓ {{ s }}</span> }
                        @for (s of m.missingSkills; track s) { <span class="chip ko">+ {{ s }}</span> }</div></div>
                  </div>
                  <ul class="advice">@for (a of m.advice; track a) { <li>{{ a }}</li> }</ul>
                }
                <button class="btn btn-ghost btn-sm mt" (click)="letterOpen.set(true)">✍ Rédiger ma lettre de motivation</button>
              </div>
            }

            <h3 class="mt-lg">Description</h3>
            <p class="desc pre">{{ o.description }}</p>
            @if (o.skills.length) {
              <h3 class="mt">Compétences repérées</h3>
              <div>@for (s of o.skills; track s) { <span class="chip">{{ s }}</span> }</div>
            }
            <p class="attrib">
              @if (o.internal) { Offre publiée par {{ o.postedBy || 'le département' }} dans SmartDept. }
              @else { Source : <strong>{{ o.sourceLabel }}</strong> — l’offre d’origine fait foi, vérifiez-la avant de postuler. }
            </p>
          </aside>
        }
      </div>

      <!-- ============ lettre de motivation ============ -->
      @if (letterOpen() && selected(); as o) {
        <div class="modal-backdrop" (click)="letterOpen.set(false)">
          <div class="modal wide" (click)="$event.stopPropagation()">
            <h2>Lettre de motivation</h2>
            <p class="text-2 small mt-xs">{{ o.title }} — {{ o.company }}</p>
            <div class="form-grid mt">
              <div class="field"><label>Langue</label>
                <select [(ngModel)]="letterLang"><option value="">Langue du pays</option><option value="fr">Français</option><option value="es">Espagnol</option><option value="en">Anglais</option></select></div>
              <div class="field"><label>À ajouter (facultatif)</label><input [(ngModel)]="letterExtra" placeholder="Disponible dès février, projet de PFE en cours…" /></div>
            </div>
            <button class="btn btn-ai mt" (click)="writeLetter(o)" [disabled]="aiBusy()">{{ aiBusy() ? 'Rédaction…' : '✦ Générer' }}</button>
            @if (letter(); as l) {
              <textarea class="letter mt" [(ngModel)]="l.text" rows="14"></textarea>
              <div class="row between mt-xs"><small class="muted">{{ l.source === 'ia' ? 'Rédigée par le modèle IA' : 'Modèle de lettre (sans IA)' }} · relisez et personnalisez avant envoi.</small>
                <button class="btn btn-ghost btn-sm" (click)="copy(l.text)">{{ copied() ? 'Copié ✓' : 'Copier' }}</button></div>
            }
            <div class="actions"><button class="btn btn-ghost" (click)="letterOpen.set(false)">Fermer</button></div>
          </div>
        </div>
      }

      <!-- ============ alerte ============ -->
      @if (alertOpen()) {
        <div class="modal-backdrop" (click)="alertOpen.set(false)">
          <form class="modal" (click)="$event.stopPropagation()" (ngSubmit)="saveAlert()">
            <h2>Créer une alerte</h2>
            <p class="text-2 small mt-xs">Vous serez prévenu(e) dans l’application et par e-mail dès qu’une nouvelle offre correspond.</p>
            <div class="field mt"><label>Nom de l’alerte</label><input name="name" [(ngModel)]="alertName" required placeholder="PFE Spring Boot au Maroc" /></div>
            <ul class="criteria">
              <li>Mots-clés : <strong>{{ q || 'tous' }}</strong></li>
              <li>Pays : <strong>{{ country ? countryLabel[country] : 'tous' }}</strong></li>
              <li>Type : <strong>{{ kind ? kindLabel(kind) : 'tous' }}</strong></li>
              <li>Niveau : <strong>{{ level ? 'Bac+' + level : 'tous' }}</strong></li>
              <li>Télétravail uniquement : <strong>{{ remote ? 'oui' : 'non' }}</strong></li>
            </ul>
            <div class="actions">
              <a class="btn btn-ghost" routerLink="/offres/alertes">Mes alertes</a>
              <button class="btn btn-primary" [disabled]="!alertName.trim()">Créer l’alerte</button>
            </div>
          </form>
        </div>
      }
    </div>
  `,
  styles: [`
    .offers-page { max-width: 1380px; }
    .hero { border-radius: 20px; padding: 30px 32px 22px; color: #e8eefc; background:
      radial-gradient(circle at 85% 0%, rgba(109, 63, 192, .55) 0, transparent 45%),
      radial-gradient(circle at 0% 100%, rgba(15, 138, 122, .45) 0, transparent 40%), linear-gradient(135deg, #0f1b3d, #1d3a8a); }
    .hero h1 { color: #fff; font-size: 28px; margin-top: 4px; }
    .hero p { color: #c6d2f0; max-width: 760px; margin-top: 6px; }
    .hero .eyebrow { color: #9fd8ce; font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .search { margin-top: 18px; display: flex; gap: 8px; align-items: center; background: #fff; border-radius: 14px; padding: 6px; box-shadow: 0 12px 30px rgba(0, 0, 0, .2); }
    .search .ico { font-size: 20px; color: var(--muted); padding: 0 4px 0 10px; }
    .search input { flex: 1; border: 0; outline: 0; font: inherit; font-size: 15px; min-width: 0; color: var(--text); background: transparent; }
    .search select { border: 0; border-left: 1px solid var(--border); padding: 8px 10px; font: inherit; background: transparent; color: var(--text); max-width: 170px; }
    .hero-actions { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 6px 10px; align-items: center; font-size: 13.5px; }
    .light { color: #dbe5ff !important; } .light:hover { color: #fff !important; }
    .sep { color: #6f80a8; }
    .levels { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin: 18px 0 12px; }
    .level { position: relative; text-align: left; padding: 12px 14px; border-radius: 12px; border: 1px solid var(--border); background: #fff; cursor: pointer; font: inherit; color: inherit; transition: .15s; }
    .level:hover { border-color: #b9c8ea; }
    .level.on { border-color: var(--primary-2); background: var(--primary-soft); box-shadow: 0 0 0 1px var(--primary-2) inset; }
    .level strong { font-size: 16px; } .level small { display: block; color: var(--muted); font-size: 12px; margin-top: 2px; }
    .level .count { position: absolute; top: 10px; right: 12px; font-size: 12px; font-weight: 700; color: var(--primary-2); }
    .lvl-top { display: flex; gap: 6px; align-items: center; }
    .me { font-size: 10.5px; font-weight: 800; text-transform: uppercase; color: var(--teal); background: var(--teal-soft); border-radius: 999px; padding: 1px 7px; }
    .filters { display: grid; gap: 8px; margin-bottom: 14px; }
    .chips { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
    .fchip { border: 1px solid var(--border); background: #fff; border-radius: 999px; padding: 6px 12px; font: 600 13px var(--font); cursor: pointer; color: var(--text-2); display: inline-flex; gap: 6px; align-items: center; }
    .fchip em { font-style: normal; font-size: 11.5px; color: var(--muted); }
    .fchip.on { background: var(--text); color: #fff; border-color: var(--text); } .fchip.on em { color: #c8d0e0; }
    .fchip.check { cursor: pointer; }
    .slim { height: 34px; padding: 4px 10px; font-size: 13px; width: auto; }
    .reco { margin-bottom: 16px; } .reco h2 { font-size: 16px; }
    .reco-row { display: grid; grid-auto-flow: column; grid-auto-columns: minmax(220px, 1fr); gap: 10px; overflow-x: auto; padding: 10px 2px 6px; }
    .reco-card { text-align: left; border: 1px solid #ddd2f3; border-radius: 12px; padding: 12px; background: linear-gradient(180deg, #faf7ff, #fff); cursor: pointer; font: inherit; color: inherit; display: grid; gap: 4px; }
    .reco-card strong { font-size: 13.5px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .reco-card small { color: var(--muted); }
    .results { display: grid; grid-template-columns: 1fr; gap: 16px; align-items: start; }
    .results.with-detail { grid-template-columns: minmax(0, 1fr) minmax(0, 1.05fr); }
    .list { display: grid; gap: 10px; }
    .list-head { color: var(--text-2); font-size: 13.5px; display: flex; gap: 8px; align-items: center; }
    .ocard { display: flex; gap: 14px; padding: 14px 16px; background: #fff; border: 1px solid var(--border); border-radius: 14px; cursor: pointer; transition: .15s; }
    .ocard:hover { border-color: #b9c8ea; box-shadow: 0 6px 18px rgba(15, 23, 42, .06); }
    .ocard.active { border-color: var(--primary-2); box-shadow: 0 0 0 1px var(--primary-2) inset; }
    .avatar { width: 44px; height: 44px; border-radius: 12px; display: grid; place-items: center; color: #fff; font-weight: 800; flex: none; letter-spacing: .02em; }
    .avatar.lg { width: 56px; height: 56px; font-size: 18px; border-radius: 14px; }
    .body { flex: 1; min-width: 0; }
    .ocard h3 { font-size: 15px; line-height: 1.35; }
    .top { align-items: flex-start; gap: 10px; flex-wrap: nowrap; }
    .meta { color: var(--text-2); font-size: 13px; margin-top: 3px; }
    .tags { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 9px; }
    .tags .chip { margin: 0; }
    .foot { display: flex; gap: 10px; align-items: center; margin-top: 10px; font-size: 12.5px; }
    .src { color: var(--muted); } .src.internal { color: var(--teal); font-weight: 650; }
    .star { margin-left: auto; border: 0; background: none; font-size: 19px; cursor: pointer; color: #b6bfd0; line-height: 1; }
    .star.on { color: #f0a500; }
    .ring { --p: 0; flex: none; width: 38px; height: 38px; border-radius: 50%; display: grid; place-items: center; font-size: 11.5px; font-weight: 800; color: var(--text);
      background: radial-gradient(closest-side, #fff 78%, transparent 80% 100%), conic-gradient(var(--teal) calc(var(--p) * 1%), #e6ebf3 0); }
    .ring.sm { width: 34px; height: 34px; font-size: 11px; } .ring.lg { width: 64px; height: 64px; font-size: 16px; }
    .more { justify-self: center; }
    .detail { position: sticky; top: 80px; max-height: calc(100vh - 100px); overflow-y: auto; padding: 22px 24px; }
    .close { position: absolute; top: 14px; right: 16px; font-size: 16px; color: var(--muted); }
    .d-head { display: flex; gap: 14px; align-items: center; padding-right: 24px; }
    .d-head h2 { font-size: 19px; }
    .facts { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin: 16px 0; }
    .facts div { background: var(--surface-2); border: 1px solid var(--border); border-radius: 10px; padding: 8px 10px; }
    .facts small { display: block; color: var(--muted); font-size: 11.5px; } .facts strong { font-size: 13.5px; }
    .actions-row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
    .ai-box { margin-top: 16px; padding: 14px; border-radius: 12px; border: 1px solid #ddd2f3; background: linear-gradient(180deg, #faf7ff, #fff); }
    .match { display: flex; gap: 14px; align-items: center; margin-top: 10px; }
    .chip.ok { background: var(--teal-soft); color: var(--teal); } .chip.ko { background: var(--amber-soft); color: var(--amber); }
    .advice { margin: 10px 0 0; padding-left: 18px; font-size: 13.5px; color: var(--text-2); }
    .desc { font-size: 14px; color: var(--text-2); margin-top: 6px; }
    .attrib { margin-top: 18px; padding-top: 12px; border-top: 1px dashed var(--border); font-size: 12.5px; color: var(--muted); }
    .mt-xs { margin-top: 6px; }
    .wide { width: min(760px, 100%); }
    .letter { width: 100%; font: 14px/1.6 var(--font); border: 1px solid var(--border); border-radius: 10px; padding: 12px; }
    .criteria { margin: 12px 0 0; padding-left: 18px; font-size: 14px; color: var(--text-2); }
    .spinner-inline { width: 14px; height: 14px; border: 2px solid #cfd8ea; border-top-color: var(--primary-2); border-radius: 50%; animation: spin .8s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
    @media (max-width: 1100px) {
      .results.with-detail { grid-template-columns: 1fr; }
      .detail { position: fixed; inset: 0; top: 0; max-height: none; z-index: 45; border-radius: 0; }
      .levels { grid-template-columns: repeat(3, 1fr); }
    }
    @media (max-width: 640px) {
      .hero { padding: 22px 18px; } .hero h1 { font-size: 22px; }
      .search { flex-wrap: wrap; } .search select { border-left: 0; max-width: none; flex: 1; }
      .levels { grid-template-columns: repeat(2, 1fr); }
      .facts { grid-template-columns: repeat(2, 1fr); }
    }
  `]
})
export class Offers implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(OfferApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly countries = COUNTRIES;
  readonly countryLabel = COUNTRY_LABEL;
  readonly kinds = KINDS;
  readonly levels = LEVELS;
  readonly tracking = TRACKING;

  readonly items = signal<Offer[]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly facets = signal<Facets | null>(null);
  readonly myLevel = signal<number | null>(null);
  readonly recommended = signal<Offer[]>([]);
  readonly selected = signal<Offer | null>(null);
  readonly matchResult = signal<MatchResult | null>(null);
  readonly letter = signal<CoverLetter | null>(null);
  readonly loading = signal(false);
  readonly liveBusy = signal(false);
  readonly aiBusy = signal(false);
  readonly copied = signal(false);
  readonly letterOpen = signal(false);
  readonly alertOpen = signal(false);
  readonly message = signal('');
  readonly error = signal('');

  readonly sourceCount = computed(() => Object.keys(this.facets()?.sources ?? {}).length || 10);
  readonly sourceOptions = computed(() => Object.entries(this.facets()?.sources ?? {})
    .map(([code, count]) => ({ code, count, label: code === 'INTERNAL' ? 'Département' : code.replace(/_/g, ' ').toLowerCase().replace(/^./, c => c.toUpperCase()) })));

  q = '';
  country: OfferCountry | '' = '';
  kind: OfferKind | '' = '';
  source: OfferSource | '' = '';
  remote = false;
  level: number | null = null;
  includeUnknown = true;
  sort: 'recent' | 'match' = 'recent';
  alertName = '';
  letterLang = '';
  letterExtra = '';
  private levelInitialised = false;

  ngOnInit(): void {
    const qp = this.route.snapshot.queryParamMap;
    this.q = qp.get('q') ?? '';
    this.country = (qp.get('pays') as OfferCountry) ?? '';
    this.kind = (qp.get('type') as OfferKind) ?? '';
    const lvl = qp.get('niveau');
    if (lvl) { this.level = Number(lvl); this.levelInitialised = true; }
    if (this.auth.isStudent()) {
      this.sort = 'match';
      this.api.recommended(8).subscribe({ next: r => this.recommended.set(r), error: () => {} });
    }
    const alertId = qp.get('alerte');
    if (alertId) {
      this.api.alerts().subscribe(list => {
        const a = list.find(x => x.id === Number(alertId));
        if (a) {
          this.q = a.keywords ?? ''; this.country = a.country ?? ''; this.kind = a.kind ?? ''; this.level = a.studyLevel ?? null;
          this.remote = a.remoteOnly; this.levelInitialised = true; this.sort = 'recent';
        }
        this.load(0);
      });
    } else {
      this.load(0);
    }
    const offerId = qp.get('offre');
    if (offerId) this.api.get(Number(offerId)).subscribe({ next: o => this.selected.set(o), error: () => {} });
  }

  private filters(page: number): OfferFilters {
    return { q: this.q.trim(), country: this.country, kind: this.kind, source: this.source, remote: this.remote || undefined,
      level: this.level, includeUnknown: this.includeUnknown, sort: this.sort, page, size: 20 };
  }

  load(page: number): void {
    this.loading.set(true);
    this.api.search(this.filters(page)).subscribe({
      next: r => {
        this.items.set(page === 0 ? r.items : [...this.items(), ...r.items]);
        this.total.set(r.total);
        this.page.set(r.page);
        this.totalPages.set(r.totalPages);
        this.facets.set(r.facets);
        this.myLevel.set(r.myLevel ?? null);
        this.loading.set(false);
        // Premier affichage d'un étudiant : son niveau est présélectionné
        if (!this.levelInitialised && r.myLevel) {
          this.levelInitialised = true;
          this.level = r.myLevel;
          this.load(0);
        }
        this.levelInitialised = true;
      },
      error: err => { this.error.set(apiError(err)); this.loading.set(false); }
    });
  }

  apply(): void {
    this.message.set('');
    this.router.navigate([], { relativeTo: this.route, replaceUrl: true, queryParams: {
      q: this.q || null, pays: this.country || null, type: this.kind || null, niveau: this.level ?? null } });
    this.load(0);
  }

  loadMore(): void { this.load(this.page() + 1); }
  setLevel(l: number | null): void { this.level = l; this.apply(); }
  toggleCountry(c: OfferCountry): void { this.country = this.country === c ? '' : c; this.apply(); }
  toggleKind(k: OfferKind): void { this.kind = this.kind === k ? '' : k; this.apply(); }
  hasFilters(): boolean { return !!(this.q || this.country || this.kind || this.source || this.remote); }
  reset(): void { this.q = ''; this.country = ''; this.kind = ''; this.source = ''; this.remote = false; this.includeUnknown = true; this.apply(); }

  open(o: Offer): void {
    this.matchResult.set(null);
    this.letter.set(null);
    this.selected.set(o);
    this.api.get(o.id).subscribe({ next: full => this.selected.set(full) });
  }

  closeDetail(): void { this.selected.set(null); }

  live(): void {
    this.liveBusy.set(true);
    this.error.set('');
    this.api.liveSearch(this.q.trim(), this.country, this.kind).subscribe({
      next: s => {
        this.liveBusy.set(false);
        const ok = s.results.filter(r => r.success).map(r => r.label);
        this.message.set(s.results.length === 0
          ? 'Aucune API configurée pour ce pays : demandez à l’administrateur d’ajouter les clés (page Sources).'
          : `${s.created} nouvelle(s) offre(s), ${s.updated} mise(s) à jour — ${ok.join(', ') || 'aucune API n’a répondu'}.`);
        this.load(0);
      },
      error: err => { this.liveBusy.set(false); this.error.set(apiError(err)); }
    });
  }

  toggleSave(o: Offer, e: Event): void {
    e.stopPropagation();
    this.setTracking(o, o.tracking ? '' : 'SAVED');
  }

  setTracking(o: Offer, status: TrackingStatus | ''): void {
    const done = (t?: TrackingStatus) => {
      const update = (x: Offer) => x.id === o.id ? { ...x, tracking: t } : x;
      this.items.update(list => list.map(update));
      this.recommended.update(list => list.map(update));
      const sel = this.selected();
      if (sel?.id === o.id) this.selected.set({ ...sel, tracking: t });
    };
    if (!status) this.api.untrack(o.id).subscribe({ next: () => done(undefined), error: err => this.error.set(apiError(err)) });
    else this.api.track(o.id, status).subscribe({ next: t => done(t.status), error: err => this.error.set(apiError(err)) });
  }

  analyze(o: Offer): void {
    this.aiBusy.set(true);
    this.api.match(o.id).subscribe({
      next: m => { this.matchResult.set(m); this.aiBusy.set(false); },
      error: err => { this.error.set(apiError(err)); this.aiBusy.set(false); }
    });
  }

  writeLetter(o: Offer): void {
    this.aiBusy.set(true);
    this.api.coverLetter(o.id, this.letterLang, this.letterExtra).subscribe({
      next: l => { this.letter.set(l); this.aiBusy.set(false); },
      error: err => { this.error.set(apiError(err)); this.aiBusy.set(false); }
    });
  }

  copy(text: string): void {
    navigator.clipboard?.writeText(text).then(() => { this.copied.set(true); setTimeout(() => this.copied.set(false), 2000); });
  }

  saveAlert(): void {
    this.api.createAlert({ name: this.alertName.trim(), keywords: this.q.trim() || undefined, country: this.country || null,
      kind: this.kind || null, studyLevel: this.level, remoteOnly: this.remote }).subscribe({
      next: () => { this.alertOpen.set(false); this.alertName = ''; this.message.set('Alerte créée : vous serez notifié(e) des nouvelles offres.'); },
      error: err => { this.alertOpen.set(false); this.error.set(apiError(err)); }
    });
  }

  kindColor(k: OfferKind): string { return k === 'PFE' ? 'violet' : k === 'ALTERNANCE' ? 'amber' : k === 'EMPLOI' ? 'teal' : 'blue'; }
  kindLabel(k: string): string { return KINDS.find(x => x.code === k)?.label ?? k; }
  trackingLabel(t: TrackingStatus): string { return TRACKING.find(x => x.code === t)?.label ?? t; }
  color = companyColor;
  init = initials;
  ago(d?: string): string { return d ? timeAgo(d + (d.length === 10 ? 'T12:00:00' : '')) : ''; }
  date(d?: string): string { return frDate(d); }
  encode(s: string): string { return encodeURIComponent(s); }
  flag(c: string): string { return COUNTRY_FLAG[c] ?? ''; }
}
