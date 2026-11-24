import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ThemeService } from './theme.service';

describe('ThemeService (mode sombre)', () => {
  beforeEach(() => localStorage.removeItem('smartdept_theme'));

  it('passe de clair à sombre puis système, et mémorise le choix', () => {
    const theme = TestBed.inject(ThemeService);
    theme.set('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    theme.cycle();
    expect(theme.mode()).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem('smartdept_theme')).toBe('dark');
    theme.cycle();
    expect(theme.mode()).toBe('system');
    expect(['light', 'dark']).toContain(theme.resolved());
  });
});
