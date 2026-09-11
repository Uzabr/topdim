// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import BottomNav from './BottomNav';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'ru' },
  }),
}));

vi.mock('../../utils/playDrop', () => ({ playDrop: vi.fn() }));

function renderNav(path = '/ru') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <BottomNav />
      <Routes>
        <Route path="/:lang" element={<div>home</div>} />
        <Route path="/:lang/favorites" element={<div>fav</div>} />
        <Route path="/:lang/cart" element={<div>cart</div>} />
        <Route path="/:lang/profile" element={<div>profile</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function blobLeft(): string {
  return (document.querySelector('.mnav__blob') as HTMLElement).style.left;
}

describe('BottomNav drop', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('moves the drop to the tapped tab and does not bounce back to the previous one', () => {
    renderNav();
    expect(blobLeft()).toBe('12px');

    fireEvent.click(screen.getByRole('button', { name: 'bottomNav.favorites' }));

    act(() => {
      vi.advanceTimersByTime(150);
    });
    expect(blobLeft()).toBe('70px');

    act(() => {
      vi.advanceTimersByTime(280 + 240);
    });
    expect(blobLeft()).toBe('70px');
    expect(screen.getByRole('button', { name: 'bottomNav.favorites' }).getAttribute('aria-current')).toBe(
      'page',
    );
  });
});
