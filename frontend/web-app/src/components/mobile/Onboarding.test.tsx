// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { CouponOffer } from '../../api/coupons';
import Onboarding, { swipeAxis } from './Onboarding';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'ru' },
  }),
}));

const deals = [
  { id: 1, title: 'Deal A', merchant: { name: 'Shop A' } },
  { id: 2, title: 'Deal B', merchant: { name: 'Shop B' } },
  { id: 3, title: 'Deal C', merchant: { name: 'Shop C' } },
] as CouponOffer[];

function renderOnboarding(onDone = vi.fn()) {
  render(<Onboarding deals={deals} onDone={onDone} />);
  return document.querySelector('.onb') as HTMLElement;
}

function swipe(el: HTMLElement, fromX: number, toX: number, fromY = 200, toY = 200) {
  fireEvent.pointerDown(el, { clientX: fromX, clientY: fromY, pointerId: 1, pointerType: 'touch', button: 0 });
  fireEvent.pointerUp(el, { clientX: toX, clientY: toY, pointerId: 1, pointerType: 'touch' });
}

describe('swipeAxis', () => {
  it('treats a left flick as next and a right flick as previous', () => {
    expect(swipeAxis(-80, 4)).toBe(1);
    expect(swipeAxis(80, -4)).toBe(-1);
  });

  it('ignores a tap and a mostly vertical gesture', () => {
    expect(swipeAxis(-20, 0)).toBe(0);
    expect(swipeAxis(-80, 120)).toBe(0);
  });
});

describe('Onboarding swipe', () => {
  afterEach(cleanup);

  it('advances to the next slide on a left swipe, not only via Далее', () => {
    const root = renderOnboarding();
    expect(screen.getByText('mobile.onboarding.s1.a')).toBeTruthy();

    swipe(root, 220, 80);

    expect(screen.getByText('mobile.onboarding.s2.a')).toBeTruthy();
    expect(screen.queryByText('mobile.onboarding.s1.a')).toBeNull();
  });

  it('goes back to the previous slide on a right swipe', () => {
    const root = renderOnboarding();
    swipe(root, 220, 80);
    swipe(root, 80, 220);

    expect(screen.getByText('mobile.onboarding.s1.a')).toBeTruthy();
  });

  it('does not change slide on a vertical swipe', () => {
    const root = renderOnboarding();
    swipe(root, 200, 190, 80, 220);

    expect(screen.getByText('mobile.onboarding.s1.a')).toBeTruthy();
  });

  it('still advances via the Далее button', () => {
    renderOnboarding();
    fireEvent.click(screen.getByRole('button', { name: 'common.next' }));

    expect(screen.getByText('mobile.onboarding.s2.a')).toBeTruthy();
  });
});
