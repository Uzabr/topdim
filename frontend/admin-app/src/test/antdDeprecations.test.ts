import { describe, expect, it } from 'vitest';

const sourceModules = import.meta.glob('../**/*.{ts,tsx}', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as Record<string, string>;

const deprecatedPatterns = [
  { name: 'Space.direction', pattern: /<Space\b[^>]*\bdirection\s*=/g },
  { name: 'Statistic.valueStyle', pattern: /<Statistic\b[^>]*\bvalueStyle\s*=/g },
  { name: 'Alert.message', pattern: /<Alert\b[^>]*\bmessage\s*=/g },
  {
    name: 'bordered on components that use variant',
    pattern: /<(?:Card|Input|InputNumber|Select|Cascader|TreeSelect|Tag)\b[^>]*\bbordered(?:\s|=|>)/g,
  },
];

describe('Ant Design maintenance', () => {
  it('does not use props deprecated by the installed Ant Design version', () => {
    const violations = Object.entries(sourceModules)
      .filter(([path]) => !path.endsWith('.test.ts') && !path.endsWith('.test.tsx'))
      .flatMap(([path, source]) => deprecatedPatterns.flatMap(({ name, pattern }) => {
        const matches = [...source.matchAll(pattern)];
        return matches.map((match) => `${path}: ${name}: ${match[0]}`);
      }));

    expect(violations).toEqual([]);
  });
});
