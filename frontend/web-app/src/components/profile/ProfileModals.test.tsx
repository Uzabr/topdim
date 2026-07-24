// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { useState, type ComponentType } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { PurchasedCoupon } from '../../api/orders';
import { reviewsApi } from '../../api/reviews';
import ru from '../../locales/ru.json';
import uz from '../../locales/uz.json';
import ComplaintModal from './ComplaintModal';
import RefundRequestModal from './RefundRequestModal';
import ReviewModal from './ReviewModal';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'ru' },
  }),
}));

vi.mock('../../utils/format', () => ({
  formatDate: (value: string) => value,
}));

vi.mock('../../api/reviews', () => ({
  reviewsApi: {
    create: vi.fn(),
  },
}));

const coupon: PurchasedCoupon = {
  id: 1,
  couponOfferId: 10,
  couponOptionId: 100,
  couponTitle: 'Dinner',
  optionTitle: 'Dinner for two',
  couponCode: 'DINNER-1',
  qrToken: '',
  status: 'ACTIVE',
  merchantName: 'Merchant',
  purchasedAt: '2026-07-01T10:00:00Z',
  expiresAt: '2026-08-01T10:00:00Z',
  usedAt: '2026-07-20T10:00:00Z',
};

type DialogComponent = ComponentType<{
  coupon: PurchasedCoupon;
  onClose: () => void;
}>;

interface DialogCase {
  name: string;
  component: DialogComponent;
  title: string;
  labelledControls: Array<{ role: 'textbox' | 'combobox' | 'group'; name: string }>;
}

const dialogCases: DialogCase[] = [
  {
    name: 'refund request',
    component: RefundRequestModal,
    title: 'profile.refundModal.title',
    labelledControls: [
      { role: 'textbox', name: 'profile.refundModal.reasonLabel' },
    ],
  },
  {
    name: 'complaint',
    component: ComplaintModal,
    title: 'profile.complaintModal.title',
    labelledControls: [
      { role: 'combobox', name: 'profile.complaintModal.topicLabel' },
      { role: 'textbox', name: 'profile.complaintModal.descLabel' },
    ],
  },
  {
    name: 'review',
    component: ReviewModal,
    title: 'profile.review.title',
    labelledControls: [
      { role: 'group', name: 'profile.review.ratingRequired' },
      { role: 'textbox', name: 'profile.review.commentPlaceholder' },
    ],
  },
];

function DialogHarness({ component: Modal }: { component: DialogComponent }) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>open-dialog</button>
      {open && <Modal coupon={coupon} onClose={() => setOpen(false)} />}
    </>
  );
}

function openDialog(dialogCase: DialogCase) {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <DialogHarness component={dialogCase.component} />
    </QueryClientProvider>,
  );
  const trigger = screen.getByRole('button', { name: 'open-dialog' });
  trigger.focus();
  fireEvent.click(trigger);

  return {
    dialog: screen.getByRole('dialog', { name: dialogCase.title }),
    trigger,
  };
}

describe.each(dialogCases)('$name modal accessibility', (dialogCase) => {
  afterEach(cleanup);

  it('exposes dialog semantics, an accessible title, labelled fields, and close control', () => {
    const { dialog } = openDialog(dialogCase);

    expect(dialog.getAttribute('aria-modal')).toBe('true');
    for (const control of dialogCase.labelledControls) {
      expect(within(dialog).getByRole(control.role, { name: control.name })).toBeTruthy();
    }
    expect(within(dialog).getByRole('button', { name: 'common.close' })).toBeTruthy();
  });

  it('moves focus inside, traps Tab in both directions, closes on Escape, and restores focus', () => {
    const { dialog, trigger } = openDialog(dialogCase);
    const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(
      'button:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ));
    const first = focusable[0];
    const last = focusable.at(-1);

    expect(first).toBeDefined();
    expect(last).toBeDefined();
    expect(dialog.contains(document.activeElement)).toBe(true);

    last?.focus();
    fireEvent.keyDown(document, { key: 'Tab' });
    expect(document.activeElement).toBe(first);

    first?.focus();
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(last);

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(document.activeElement).toBe(trigger);
  });
});

describe('refund timing copy', () => {
  it('uses five working days in the FAQ and refund dialog', () => {
    expect(ru.profile.help.faq.noShow.a).toContain('5 рабочих дней');
    expect(ru.profile.refundModal.processingHint).toContain('5 рабочих дней');
    expect(uz.profile.help.faq.noShow.a).toContain('5 ish kunigacha');
    expect(uz.profile.refundModal.processingHint).toContain('5 ish kunigacha');
  });
});

describe('optional review comment', () => {
  afterEach(cleanup);

  it('omits a blank comment while submitting a rating-only review', async () => {
    vi.mocked(reviewsApi.create).mockResolvedValue(
      {} as Awaited<ReturnType<typeof reviewsApi.create>>,
    );
    openDialog(dialogCases[2]);

    fireEvent.click(screen.getByRole('button', { name: '5' }));
    fireEvent.change(screen.getByRole('textbox', {
      name: 'profile.review.commentPlaceholder',
    }), {
      target: { value: '   ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'profile.review.submit' }));

    await waitFor(() => expect(reviewsApi.create).toHaveBeenCalledWith({
      couponOfferId: 10,
      rating: 5,
      comment: undefined,
    }));
  });
});
