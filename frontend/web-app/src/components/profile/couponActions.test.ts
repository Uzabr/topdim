import { describe, expect, it } from 'vitest';
import { getCouponActions } from './couponActions';

describe('getCouponActions', () => {
  it('allows refund and complaint for an active coupon without an open complaint', () => {
    expect(getCouponActions('ACTIVE', false, false)).toEqual({
      canRefund: true,
      canComplain: true,
      canReview: false,
    });
  });

  it('allows only complaint while a refund is pending', () => {
    expect(getCouponActions('REFUND_PENDING', false, false)).toEqual({
      canRefund: false,
      canComplain: true,
      canReview: false,
    });
  });

  it('allows complaint and review for a used coupon without an existing review', () => {
    expect(getCouponActions('USED', false, false)).toEqual({
      canRefund: false,
      canComplain: true,
      canReview: true,
    });
  });

  it('does not allow another review for an already reviewed used coupon', () => {
    expect(getCouponActions('USED', false, true)).toEqual({
      canRefund: false,
      canComplain: true,
      canReview: false,
    });
  });

  it('does not allow a complaint while one is already pending', () => {
    expect(getCouponActions('ACTIVE', true, false)).toEqual({
      canRefund: true,
      canComplain: false,
      canReview: false,
    });
  });

  it('allows only complaint for a cancelled coupon without an open complaint', () => {
    expect(getCouponActions('CANCELLED', false, false)).toEqual({
      canRefund: false,
      canComplain: true,
      canReview: false,
    });
  });

  it('allows no action for a cancelled coupon with an open complaint', () => {
    expect(getCouponActions('CANCELLED', true, false)).toEqual({
      canRefund: false,
      canComplain: false,
      canReview: false,
    });
  });
});
