import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AcademicApi, CourseModule, Room } from '../../core/academic';
import { Filiere, User } from '../../core/models';
import { apiError } from '../../core/labels';

/** Référentiel de la scolarité : salles et modules (volume hebdomadaire utilisé par la planification automatique). */
@Component({
  selector: 'app-referential',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="page-head"><div><span class="eyebrow">Administration</span><h1>Salles et modules</h1>
        <p>Le volume hebdomadaire de chaque module (séances de 2 h de CM, TD, TP) sert à la planification automatique.</p></div></div>
      @if (error()) { <div class="alert error" style="margin-bottom:12px">{{ error() }}</div> }
      <div class="card">
        <div class="card-head"><h3>Modules ({{ modules().length }})</h3><button class="btn btn-primary btn-sm" (click)="editModule()">✚ Module</button></div>
        <table class="table"><thead><tr><th>Code</th><th>Module</th><th>Filière</th><th>Sem.</th><th>Enseignant</th><th>Coef.</th><th>CM/TD/TP</th><th></th></tr></thead>
          <tbody>@for (m of modules(); track m.id) {
            <tr><td><strong>{{ m.code }}</strong></td><td>{{ m.name }}</td><td>{{ m.filiere.code }}</td><td>{{ m.semester }}</td><td>{{ m.teacher?.fullName || '—' }}</td>
              <td>{{ m.coefficient }}</td><td>{{ m.cmPerWeek }} / {{ m.tdPerWeek }} / {{ m.tpPerWeek }}</td><td><button class="btn-link small" (click)="editModule(m)">Modifier</button></td></tr>
          }</tbody></table>
      </div>
      <div class="card mt">
        <div class="card-head"><h3>Salles ({{ rooms().length }})</h3><button class="btn btn-primary btn-sm" (click)="editRoom()">✚ Salle</button></div>
        <table class="table"><thead><tr><th>Code</th><th>Nom</th><th>Type</th><th>Places</th><th>Bâtiment</th><th>État</th><th></th></tr></thead>
          <tbody>@for (r of rooms(); track r.id) {
            <tr><td><strong>{{ r.code }}</strong></td><td>{{ r.name }}</td><td>{{ r.type }}</td><td>{{ r.capacity }}</td><td>{{ r.building || '—' }}</td>
              <td>@if (r.active) { <span class="badge teal">En service</span> } @else { <span class="badge gray">Hors service</span> }</td>
              <td><button class="btn-link small" (click)="editRoom(r)">Modifier</button></td></tr>
          }</tbody></table>
      </div>

      @if (moduleForm(); as f) {
        <div class="modal-backdrop" (click)="moduleForm.set(null)">
          <form class="modal wide" (click)="$event.stopPropagation()" (ngSubmit)="saveModule()">
            <h2>{{ f.id ? 'Modifier le module' : 'Nouveau module' }}</h2>
            <div class="form-grid mt">
              <div class="field"><label>Code *</label><input name="c" [(ngModel)]="f.code" required /></div>
              <div class="field"><label>Nom *</label><input name="n" [(ngModel)]="f.name" required /></div>
              <div class="field"><label>Filière *</label><select name="f" [(ngModel)]="f.filiereId">@for (x of filieres(); track x.id) { <option [ngValue]="x.id">{{ x.code }}</option> }</select></div>
              <div class="field"><label>Semestre *</label><select name="s" [(ngModel)]="f.semester">@for (s of semesters; track s) { <option [value]="s">{{ s }}</option> }</select></div>
              <div class="field"><label>Enseignant</label><select name="t" [(ngModel)]="f.teacherId"><option [ngValue]="null">—</option>@for (t of teachers(); track t.id) { <option [ngValue]="t.id">{{ t.fullName }}</option> }</select></div>
              <div class="field"><label>Coefficient</label><input type="number" step="0.5" min="0.5" name="co" [(ngModel)]="f.coefficient" /></div>
              <div class="field"><label>CM / semaine</label><input type="number" min="0" max="6" name="cm" [(ngModel)]="f.cmPerWeek" /></div>
              <div class="field"><label>TD / semaine</label><input type="number" min="0" max="6" name="td" [(ngModel)]="f.tdPerWeek" /></div>
              <div class="field"><label>TP / semaine</label><input type="number" min="0" max="6" name="tp" [(ngModel)]="f.tpPerWeek" /></div>
              <div class="field full"><label>Description / mots-clés</label><input name="d" [(ngModel)]="f.description" /></div>
            </div>
            <div class="actions"><button type="button" class="btn btn-ghost" (click)="moduleForm.set(null)">Annuler</button><button class="btn btn-primary">Enregistrer</button></div>
          </form>
        </div>
      }
      @if (roomForm(); as f) {
        <div class="modal-backdrop" (click)="roomForm.set(null)">
          <form class="modal" (click)="$event.stopPropagation()" (ngSubmit)="saveRoom()">
            <h2>{{ f.id ? 'Modifier la salle' : 'Nouvelle salle' }}</h2>
            <div class="form-grid mt">
              <div class="field"><label>Code *</label><input name="c" [(ngModel)]="f.code" required /></div>
              <div class="field"><label>Nom *</label><input name="n" [(ngModel)]="f.name" required /></div>
              <div class="field"><label>Type</label><select name="t" [(ngModel)]="f.type"><option value="AMPHI">Amphithéâtre</option><option value="SALLE">Salle</option><option value="LABO">Laboratoire</option></select></div>
              <div class="field"><label>Places *</label><input type="number" min="1" name="cap" [(ngModel)]="f.capacity" /></div>
              <div class="field"><label>Bâtiment</label><input name="b" [(ngModel)]="f.building" /></div>
              <div class="field"><label class="check" style="margin-top:28px"><input type="checkbox" name="a" [(ngModel)]="f.active" /> En service</label></div>
            </div>
            <div class="actions"><button type="button" class="btn btn-ghost" (click)="roomForm.set(null)">Annuler</button><button class="btn btn-primary">Enregistrer</button></div>
          </form>
        </div>
      }
    </div>
  `,
  styles: [`
    .eyebrow { display: block; margin-bottom: 6px; color: var(--primary-2); font-size: 11.5px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .wide { width: min(760px, 100%); }
  `]
})
export class Referential implements OnInit {
  private readonly api = inject(AcademicApi);
  private readonly core = inject(ApiService);
  readonly semesters = ['S1', 'S2', 'S3', 'S4', 'S5', 'S6', 'S7', 'S8', 'S9', 'S10'];
  readonly rooms = signal<Room[]>([]);
  readonly modules = signal<CourseModule[]>([]);
  readonly filieres = signal<Filiere[]>([]);
  readonly teachers = signal<User[]>([]);
  readonly moduleForm = signal<any>(null);
  readonly roomForm = signal<any>(null);
  readonly error = signal('');

  ngOnInit(): void {
    this.load();
    this.core.filieres().subscribe(f => this.filieres.set(f));
    this.core.users('ENSEIGNANT').subscribe(t => this.teachers.set(t));
  }

  load(): void {
    this.api.rooms().subscribe(r => this.rooms.set(r));
    this.api.modules().subscribe(m => this.modules.set(m));
  }

  editModule(m?: CourseModule): void {
    this.moduleForm.set(m ? { id: m.id, code: m.code, name: m.name, filiereId: m.filiere.id, semester: m.semester, teacherId: m.teacher?.id ?? null,
      coefficient: m.coefficient, cmPerWeek: m.cmPerWeek, tdPerWeek: m.tdPerWeek, tpPerWeek: m.tpPerWeek, description: m.description ?? '' }
      : { code: '', name: '', filiereId: this.filieres()[0]?.id, semester: 'S1', teacherId: null, coefficient: 1, cmPerWeek: 1, tdPerWeek: 1, tpPerWeek: 0, description: '' });
  }

  editRoom(r?: Room): void {
    this.roomForm.set(r ? { ...r } : { code: '', name: '', type: 'SALLE', capacity: 30, building: '', active: true });
  }

  saveModule(): void {
    const { id, ...body } = this.moduleForm();
    this.api.saveModule(body, id).subscribe({ next: () => { this.moduleForm.set(null); this.load(); }, error: err => { this.moduleForm.set(null); this.error.set(apiError(err)); } });
  }

  saveRoom(): void {
    const { id, ...body } = this.roomForm();
    this.api.saveRoom(body, id).subscribe({ next: () => { this.roomForm.set(null); this.load(); }, error: err => { this.roomForm.set(null); this.error.set(apiError(err)); } });
  }
}
