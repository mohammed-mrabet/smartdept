import { Component, computed, inject, input } from '@angular/core';
import { ThemeService } from '../core/theme.service';

const LABEL = { light: 'Thème clair', dark: 'Thème sombre', system: 'Thème du système' };
const ICON = { light: '☀', dark: '☾', system: '◐' };

@Component({
  selector: 'app-theme-toggle',
  template: `
    <button type="button" class="tt" [class.on-dark]="onDark()" (click)="theme.cycle()"
            [title]="label() + ' — cliquer pour changer'" [attr.aria-label]="label() + ', cliquer pour changer'">
      <span aria-hidden="true">{{ icon() }}</span>
    </button>
  `,
  styles: [`
    .tt { width: 40px; height: 40px; border-radius: 10px; border: 1px solid var(--border); background: var(--surface); color: var(--text);
      cursor: pointer; font-size: 17px; display: grid; place-items: center; transition: border-color .15s, background .15s; }
    .tt:hover { border-color: var(--border-strong); }
    .tt.on-dark { background: rgba(255,255,255,.06); border-color: rgba(255,255,255,.2); color: #fff; width: 34px; height: 34px; font-size: 15px; }
  `]
})
export class ThemeToggle {
  readonly theme = inject(ThemeService);
  /** Bouton posé sur un fond sombre (bandeau de l'accueil, panneau de connexion). */
  readonly onDark = input(false);
  readonly icon = computed(() => ICON[this.theme.mode()]);
  readonly label = computed(() => LABEL[this.theme.mode()]);
}
