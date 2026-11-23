import { describe, expect, it } from 'vitest';
import { passwordScore, passwordValid } from './public-api.service';

describe('mot de passe (inscription)', () => {
  it('applique les mêmes règles minimales que le backend', () => {
    expect(passwordValid('motdepasse')).toBe(false);
    expect(passwordValid('12345678')).toBe(false);
    expect(passwordValid('Court1')).toBe(false);
    expect(passwordValid('Motdepasse1')).toBe(true);
  });

  it('note la force de 0 à 4', () => {
    expect(passwordScore('')).toBe(0);
    expect(passwordScore('abc')).toBe(0);
    expect(passwordScore('motdepasse')).toBe(1);
    expect(passwordScore('motdepasse1')).toBe(2);
    expect(passwordScore('Motdepasse1')).toBe(3);
    expect(passwordScore('Motdepasse1!')).toBe(4);
  });
});
