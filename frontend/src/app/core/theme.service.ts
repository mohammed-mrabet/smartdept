import { Injectable, computed, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark' | 'system';
const KEY = 'smartdept_theme';

/**
 * Thème clair / sombre. « system » suit le réglage du système d'exploitation (prefers-color-scheme).
 * Le choix est mémorisé dans le navigateur ; index.html l'applique avant le chargement d'Angular (pas de flash blanc).
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly media = typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null;
  private readonly systemDark = signal(this.media?.matches ?? false);

  readonly mode = signal<ThemeMode>(readMode());
  readonly resolved = computed<'light' | 'dark'>(() =>
    this.mode() === 'system' ? (this.systemDark() ? 'dark' : 'light') : this.mode() as 'light' | 'dark');

  constructor() {
    this.media?.addEventListener?.('change', e => { this.systemDark.set(e.matches); this.apply(); });
    this.apply();
  }

  set(mode: ThemeMode): void {
    this.mode.set(mode);
    try { localStorage.setItem(KEY, mode); } catch { /* stockage indisponible : le choix vaut pour la session */ }
    this.apply();
  }

  /** Clair → sombre → système → clair… */
  cycle(): void {
    const next: Record<ThemeMode, ThemeMode> = { light: 'dark', dark: 'system', system: 'light' };
    this.set(next[this.mode()]);
  }

  private apply(): void {
    if (typeof document !== 'undefined') document.documentElement.setAttribute('data-theme', this.resolved());
  }
}

function readMode(): ThemeMode {
  try {
    const v = localStorage.getItem(KEY);
    if (v === 'light' || v === 'dark' || v === 'system') return v;
  } catch { /* ignoré */ }
  return 'system';
}
