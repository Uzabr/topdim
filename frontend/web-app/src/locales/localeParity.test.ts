import { describe, expect, it } from 'vitest';
import ru from './ru.json';
import uz from './uz.json';

const PLURAL_SUFFIX = /_(zero|one|two|few|many|other)$/;

function localeKeys(value: unknown, prefix = ''): string[] {
  if (value == null || typeof value !== 'object' || Array.isArray(value)) {
    return [prefix];
  }
  return Object.entries(value)
    .flatMap(([key, nested]) =>
      localeKeys(nested, prefix ? `${prefix}.${key}` : key))
    .filter((key) => !PLURAL_SUFFIX.test(key))
    .sort();
}

describe('locale key parity', () => {
  it('keeps Russian and Uzbek non-plural translation keys aligned', () => {
    expect(localeKeys(uz)).toEqual(localeKeys(ru));
  });
});
