import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { apiError } from '../../core/labels';
import { COUNTRIES, KINDS, LEVELS, OfferApi, OfferRequest } from '../../core/offers';

@Component({
  selector: 'app-offer-form',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page">
      <a routerLink="/offres/marche" class="small">← Sources et marché</a>
      <div class="page-head mt"><div><span class="eyebrow">Stages et emploi</span>
        <h1>{{ id() ? 'Modifier l’offre' : 'Publier une offre d’une entreprise partenaire' }}</h1>
        <p>Les étudiants dont le niveau correspond sont prévenus dans l’application et par e-mail ; les alertes sont déclenchées.</p></div></div>
      @if (error()) { <div class="alert error" style="margin-bottom:14px">{{ error() }}</div> }
      <form class="card" (ngSubmit)="save()" #f="ngForm">
        <div class="form-grid">
          <div class="field full"><label>Intitulé *</label><input name="title" [(ngModel)]="m.title" required maxlength="255" placeholder="Stage PFE — Développeur full stack" /></div>
          <div class="field"><label>Entreprise *</label><input name="company" [(ngModel)]="m.company" required /></div>
          <div class="field"><label>Ville *</label><input name="location" [(ngModel)]="m.location" required placeholder="Oujda" /></div>
          <div class="field"><label>Pays *</label><select name="country" [(ngModel)]="m.country">@for (c of countries; track c.code) { <option [value]="c.code">{{ c.flag }} {{ c.label }}</option> }</select></div>
          <div class="field"><label>Type *</label><select name="kind" [(ngModel)]="m.kind">@for (k of kinds; track k.code) { <option [value]="k.code">{{ k.label }}</option> }</select></div>
          <div class="field"><label>Niveau minimum</label><select name="min" [(ngModel)]="m.levelMin"><option [ngValue]="null">Non précisé</option>
            @for (l of levels; track l.years) { <option [ngValue]="l.years">{{ l.label }} ({{ l.diplomas }})</option> }</select></div>
          <div class="field"><label>Niveau maximum</label><select name="max" [(ngModel)]="m.levelMax"><option [ngValue]="null">Non précisé</option>
            @for (l of levels; track l.years) { <option [ngValue]="l.years">{{ l.label }}</option> }</select></div>
          <div class="field"><label>Durée (mois)</label><input type="number" min="1" max="36" name="duration" [(ngModel)]="m.durationMonths" /></div>
          <div class="field"><label>Rémunération</label><input name="salary" [(ngModel)]="m.salary" placeholder="3 000 MAD/mois" /></div>
          <div class="field"><label>Lien pour postuler</label><input name="url" [(ngModel)]="m.url" placeholder="https://…" /></div>
          <div class="field"><label>E-mail de contact</label><input type="email" name="email" [(ngModel)]="m.contactEmail" /></div>
          <div class="field"><label>Date limite</label><input type="date" name="expires" [(ngModel)]="m.expiresAt" /></div>
          <div class="field"><label class="check" style="margin-top:28px"><input type="checkbox" name="remote" [(ngModel)]="m.remote" /> Télétravail possible</label></div>
          <div class="field full"><label>Description *</label><textarea name="description" rows="8" [(ngModel)]="m.description" required placeholder="Contexte, missions, profil recherché…"></textarea></div>
          <div class="field full"><label>Compétences (sinon détectées automatiquement)</label><input name="skills" [(ngModel)]="m.skills" placeholder="Java, Spring, Angular" /></div>
        </div>
        <div class="row mt-lg"><button class="btn btn-primary" [disabled]="busy() || !f.valid">{{ id() ? 'Enregistrer' : 'Publier et notifier' }}</button>
          <a class="btn btn-ghost" routerLink="/offres/marche">Annuler</a></div>
      </form>
    </div>
  `,
  styles: [`.eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }`]
})
export class OfferForm implements OnInit {
  readonly id = input<string>();
  private readonly api = inject(OfferApi);
  private readonly router = inject(Router);
  readonly countries = COUNTRIES.filter(c => c.code !== 'REMOTE').concat([{ code: 'REMOTE', label: 'Télétravail', flag: '🌍' }]);
  readonly kinds = KINDS;
  readonly levels = LEVELS;
  readonly busy = signal(false);
  readonly error = signal('');
  m: OfferRequest = { title: '', company: '', location: '', country: 'MA', kind: 'PFE', levelMin: 5, levelMax: 5, remote: false, description: '',
    url: '', contactEmail: '', durationMonths: 6, salary: '', expiresAt: null, skills: '' };

  ngOnInit(): void {
    if (this.id()) {
      this.api.get(+this.id()!).subscribe(o => this.m = {
        title: o.title, company: o.company ?? '', location: o.location ?? '', country: o.country, kind: o.kind, levelMin: o.levelMin ?? null,
        levelMax: o.levelMax ?? null, remote: o.remote, description: o.description ?? '', url: o.url ?? '', contactEmail: o.contactEmail ?? '',
        durationMonths: o.durationMonths ?? null, salary: o.salary ?? '', expiresAt: o.expiresAt ?? null, skills: o.skills.join(', ')
      });
    }
  }

  save(): void {
    this.busy.set(true);
    const body = { ...this.m, url: this.m.url || undefined, contactEmail: this.m.contactEmail || undefined, salary: this.m.salary || undefined, expiresAt: this.m.expiresAt || null };
    const call = this.id() ? this.api.update(+this.id()!, body) : this.api.create(body);
    call.subscribe({
      next: o => this.router.navigate(['/offres'], { queryParams: { offre: o.id } }),
      error: err => { this.error.set(apiError(err)); this.busy.set(false); }
    });
  }
}
