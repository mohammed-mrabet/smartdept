import { describe, expect, it } from 'vitest';
import { companyColor, initials, TRACKING_LABEL } from './offers';
import { addDays, minutes, mondayOf } from './academic';

describe('offres', () => {
  it('initiales et couleur stables pour une entreprise', () => {
    expect(initials('Atlas Numérique (entreprise fictive)')).toBe('AN');
    expect(initials(undefined)).toBe('?');
    expect(companyColor('Acme')).toBe(companyColor('Acme'));
  });
  it('libellés du suivi', () => {
    expect(TRACKING_LABEL['INTERVIEW']).toBe('Entretien');
  });
});

describe('dates de la scolarité', () => {
  it('lundi de la semaine et décalage de jours', () => {
    expect(mondayOf('2026-11-26')).toBe('2026-11-23');
    expect(mondayOf('2026-11-29')).toBe('2026-11-23');
    expect(addDays('2026-11-30', 2)).toBe('2026-12-02');
    expect(minutes('10:45')).toBe(645);
  });
});
