import { describe, expect, it } from 'vitest';
import { APPLICATION_STATUS, apiError, daysUntil, hhmm, today } from './labels';

describe('labels', () => {
  it('formate les heures HH:mm', () => {
    expect(hhmm('14:30:00')).toBe('14:30');
    expect(hhmm(undefined)).toBe('');
  });

  it('calcule le nombre de jours avant une échéance', () => {
    expect(daysUntil(today())).toBe(0);
  });

  it('a un libellé pour chaque statut de candidature', () => {
    expect(APPLICATION_STATUS.ACCEPTED[0]).toBe('Acceptée');
  });

  it('extrait un message lisible des erreurs de l’API', () => {
    expect(apiError({ status: 0 })).toContain('Serveur injoignable');
    expect(apiError({ status: 409, error: { message: 'Conflit' } })).toBe('Conflit');
    expect(apiError({ status: 400, error: { errors: { title: 'Titre obligatoire' } } })).toBe('Titre obligatoire');
  });
});
