import { Component, input } from '@angular/core';

/** Pastille de statut : [libellé, couleur] venant de core/labels.ts */
@Component({
  selector: 'app-badge',
  template: `<span class="badge" [class]="'badge ' + (label()?.[1] ?? 'gray')">{{ label()?.[0] ?? '' }}</span>`
})
export class Badge {
  readonly label = input<[string, string] | undefined>();
}
