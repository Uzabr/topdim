import { describe, expect, it } from 'vitest';
import { canManageCompany } from './permissions';

describe('canManageCompany', () => {
  it.each(['OWNER', 'MANAGER'] as const)('allows %s to manage the company profile', (role) => {
    expect(canManageCompany({ role })).toBe(true);
  });

  it('denies CASHIER even when cashier can redeem', () => {
    expect(canManageCompany({ role: 'CASHIER' })).toBe(false);
  });

  it('denies a missing partner context', () => {
    expect(canManageCompany(null)).toBe(false);
  });
});
