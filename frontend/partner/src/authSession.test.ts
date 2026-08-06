import { describe, expect, it } from 'vitest';
import { parsePartnerContext } from './authSession';

describe('parsePartnerContext', () => {
  it('accepts owner context with nullable staff fields returned by the backend', () => {
    expect(parsePartnerContext({
      role: 'OWNER',
      merchantId: 8,
      merchantLocationId: null,
      staffId: null,
      staffName: null,
      canViewDashboard: true,
      canRedeem: true,
    })).toEqual({
      role: 'OWNER',
      merchantId: 8,
      merchantLocationId: undefined,
      staffId: undefined,
      staffName: undefined,
      canViewDashboard: true,
      canRedeem: true,
    });
  });

  it('rejects cashier context without a positive location id', () => {
    expect(parsePartnerContext({
      role: 'CASHIER',
      merchantId: 8,
      merchantLocationId: null,
      canViewDashboard: false,
      canRedeem: true,
    })).toBeNull();
  });

  it('rejects an unknown role even when permission flags are elevated', () => {
    expect(parsePartnerContext({
      role: 'ADMIN',
      merchantId: 8,
      canViewDashboard: true,
      canRedeem: true,
    })).toBeNull();
  });
});
