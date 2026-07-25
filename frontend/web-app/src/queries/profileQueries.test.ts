import { describe, expect, it, vi } from 'vitest';
import { loadAllProfilePages, profileQueryKeys } from './profileQueries';

interface RecordData {
  id: number;
}

function page(content: RecordData[], number: number, last: boolean) {
  return {
    data: {
      data: {
        content,
        number,
        last,
      },
    },
  };
}

describe('profileQueries', () => {
  it('loads every policy page instead of stopping after the first 100 records', async () => {
    const firstHundred = Array.from({ length: 100 }, (_, index) => ({ id: index + 1 }));
    const fetchPage = vi.fn()
      .mockResolvedValueOnce(page(firstHundred, 0, false))
      .mockResolvedValueOnce(page([{ id: 101 }], 1, true));

    const records = await loadAllProfilePages(fetchPage);

    expect(fetchPage).toHaveBeenNthCalledWith(1, 0, 100);
    expect(fetchPage).toHaveBeenNthCalledWith(2, 1, 100);
    expect(records).toHaveLength(101);
    expect(records.at(-1)).toEqual({ id: 101 });
  });

  it('scopes every private profile query key by authenticated user id', () => {
    expect(profileQueryKeys.coupons(7)).not.toEqual(profileQueryKeys.coupons(8));
    expect(profileQueryKeys.complaints(7)).not.toEqual(profileQueryKeys.complaints(8));
    expect(profileQueryKeys.reviews(7)).not.toEqual(profileQueryKeys.reviews(8));
    expect(profileQueryKeys.orders(7)).not.toEqual(profileQueryKeys.orders(8));
    expect(profileQueryKeys.notifications(7, false))
      .not.toEqual(profileQueryKeys.notifications(8, false));
  });
});
