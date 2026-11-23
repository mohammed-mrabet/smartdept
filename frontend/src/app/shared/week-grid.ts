import { Component, computed, input, output } from '@angular/core';
import { DAYS, addDays, minutes } from '../core/academic';

export interface GridItem {
  key: string; date: string; startTime: string; endTime: string; title: string; line2?: string; line3?: string;
  type: 'CM' | 'TD' | 'TP' | 'EXAM'; cancelled?: boolean; data?: unknown;
}

interface Placed { item: GridItem; top: number; height: number; left: number; width: number; }

const START = 8 * 60;
const END = 19 * 60;

/** Grille hebdomadaire (lundi → samedi, 8 h → 19 h, 1 minute = 1 pixel). Les créneaux qui se chevauchent sont côte à côte. */
@Component({
  selector: 'app-week-grid',
  template: `
    <div class="wg">
      <div class="hours">
        <div class="corner"></div>
        @for (h of hours; track h) { <div class="hour" [style.top.px]="(h - 8) * 60 + 44">{{ h }}:00</div> }
      </div>
      @for (d of days(); track d.date) {
        <div class="day" [class.today]="d.date === today">
          <div class="dhead"><strong>{{ d.label }}</strong><small>{{ d.short }}</small></div>
          <div class="dbody" [style.height.px]="height" (click)="empty($event, d.date)">
            @for (h of hours; track h) { <div class="line" [style.top.px]="(h - 8) * 60"></div> }
            @for (p of d.items; track p.item.key) {
              <button class="slot" [class]="'slot ' + p.item.type.toLowerCase()" [class.cancelled]="p.item.cancelled"
                      [style.top.px]="p.top" [style.height.px]="p.height" [style.left.%]="p.left" [style.width.%]="p.width"
                      (click)="$event.stopPropagation(); pick.emit(p.item)">
                <span class="t">{{ p.item.startTime.substring(0, 5) }}–{{ p.item.endTime.substring(0, 5) }} · {{ p.item.type }}</span>
                <strong>{{ p.item.title }}</strong>
                @if (p.item.line2) { <span>{{ p.item.line2 }}</span> }
                @if (p.item.line3) { <span class="muted">{{ p.item.line3 }}</span> }
              </button>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .wg { display: grid; grid-template-columns: 52px repeat(6, minmax(120px, 1fr)); border: 1px solid var(--border); border-radius: 14px; background: #fff; overflow-x: auto; }
    .hours { position: relative; border-right: 1px solid var(--border); }
    .corner { height: 44px; border-bottom: 1px solid var(--border); }
    .hour { position: absolute; right: 6px; font-size: 11px; color: var(--muted); transform: translateY(-7px); }
    .day { border-right: 1px solid #eef1f6; min-width: 120px; } .day:last-child { border-right: 0; }
    .day.today { background: #f7faff; }
    .dhead { height: 44px; display: flex; flex-direction: column; justify-content: center; align-items: center; border-bottom: 1px solid var(--border); font-size: 13px; }
    .day.today .dhead { color: var(--primary-2); }
    .dhead small { color: var(--muted); font-size: 11.5px; }
    .dbody { position: relative; cursor: default; }
    .line { position: absolute; left: 0; right: 0; border-top: 1px dashed #eef1f6; }
    .slot { position: absolute; padding: 5px 7px; border-radius: 8px; border: 0; border-left: 3px solid; text-align: left; font: 11.5px/1.3 var(--font);
      overflow: hidden; cursor: pointer; display: flex; flex-direction: column; gap: 1px; box-sizing: border-box; margin: 1px; }
    .slot strong { font-size: 12.5px; }
    .slot .t { font-weight: 700; opacity: .8; }
    .slot.cm { background: #e8efff; border-color: #2563eb; color: #1e3a8a; }
    .slot.td { background: #e2f6f2; border-color: #0f8a7a; color: #115e55; }
    .slot.tp { background: #f1eaff; border-color: #6d3fc0; color: #4c2a8a; }
    .slot.exam { background: #fff3df; border-color: #d97706; color: #7c4a03; }
    .slot.cancelled { opacity: .55; text-decoration: line-through; background: #f3f4f6; border-color: #9ca3af; color: #4b5563; }
    .muted { color: inherit; opacity: .7; }
  `]
})
export class WeekGrid {
  readonly weekStart = input.required<string>();
  readonly items = input<GridItem[]>([]);
  readonly pick = output<GridItem>();
  readonly emptyClick = output<{ date: string; time: string }>();

  readonly hours = Array.from({ length: (END - START) / 60 }, (_, i) => 8 + i);
  readonly height = END - START;
  readonly today = new Date().toISOString().substring(0, 10);

  readonly days = computed(() => DAYS.map((label, i) => {
    const date = addDays(this.weekStart(), i);
    const [y, m, d] = date.split('-').map(Number);
    return {
      label, date, short: new Date(y, m - 1, d).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' }),
      items: this.place(this.items().filter(it => it.date === date))
    };
  }));

  private place(list: GridItem[]): Placed[] {
    const sorted = [...list].sort((a, b) => minutes(a.startTime) - minutes(b.startTime));
    const ends: number[] = [];
    const lanes = sorted.map(it => {
      let lane = ends.findIndex(e => e <= minutes(it.startTime));
      if (lane === -1) { lane = ends.length; ends.push(0); }
      ends[lane] = minutes(it.endTime);
      return lane;
    });
    const count = Math.max(ends.length, 1);
    return sorted.map((item, i) => {
      const s = Math.max(minutes(item.startTime), START);
      const e = Math.min(minutes(item.endTime), END);
      return { item, top: s - START, height: Math.max(e - s, 24), left: (100 / count) * lanes[i], width: 100 / count };
    });
  }

  empty(e: MouseEvent, date: string): void {
    const m = START + Math.floor(e.offsetY / 15) * 15;
    this.emptyClick.emit({ date, time: `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}` });
  }
}
