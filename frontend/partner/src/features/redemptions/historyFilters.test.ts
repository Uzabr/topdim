import { describe, expect, it } from 'vitest';
import {
  historyApiParams,
  parseHistorySearchParams,
  serializeHistoryFilters,
} from './historyFilters';

describe('redemption history URL filters', () => {
  it('normalizes valid URL filters and converts the page for the API', () => {
    const filters = parseHistorySearchParams(new URLSearchParams(
      'code=%20cp-12%20&from=2026-08-01&to=2026-08-06&page=3',
    ));

    expect(filters).toEqual({
      code: 'cp-12',
      from: '2026-08-01',
      to: '2026-08-06',
      page: 3,
    });
    expect(historyApiParams(filters)).toEqual({
      page: 2,
      size: 20,
      couponCode: 'cp-12',
      dateFrom: '2026-08-01',
      dateTo: '2026-08-06',
    });
  });

  it('discards malformed dates, inverted ranges, and invalid pages', () => {
    expect(parseHistorySearchParams(new URLSearchParams(
      'from=2026-02-31&to=2026-01-01&page=-4',
    ))).toEqual({ code: '', to: '2026-01-01', page: 1 });

    expect(parseHistorySearchParams(new URLSearchParams(
      'from=2026-08-07&to=2026-08-06&page=2',
    ))).toEqual({ code: '', page: 2 });

    expect(parseHistorySearchParams(new URLSearchParams(
      'page=999999999999999999999999',
    ))).toEqual({ code: '', page: 1 });
  });

  it('serializes only canonical non-default filters', () => {
    expect(serializeHistoryFilters({
      code: 'CP-12',
      from: '2026-08-01',
      page: 1,
    }).toString()).toBe('code=CP-12&from=2026-08-01');

    expect(serializeHistoryFilters({ code: '', page: 1 }).toString()).toBe('');
  });

  it('trims and limits coupon code to the backend contract', () => {
    const code = `  ${'A'.repeat(51)}  `;

    expect(parseHistorySearchParams(new URLSearchParams({ code })).code)
      .toBe('A'.repeat(50));
  });
});
