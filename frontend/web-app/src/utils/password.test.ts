import { describe, expect, it } from 'vitest';
import { isStrongPassword } from './password';

describe('strong password validation', () => {
  it.each([
    'Abcdef1!',
    'Valid-Pass_2026',
    `${'A'.repeat(124)}a1!`,
  ])('accepts backend-compatible password %s', (password) => {
    expect(isStrongPassword(password)).toBe(true);
  });

  it.each([
    'Short1!',
    'nouppercase1!',
    'NOLOWERCASE1!',
    'NoNumber!',
    'NoSpecial1',
    'Contains space1!',
    `${'A'.repeat(126)}a1!`,
  ])('rejects password outside backend rules: %s', (password) => {
    expect(isStrongPassword(password)).toBe(false);
  });
});
