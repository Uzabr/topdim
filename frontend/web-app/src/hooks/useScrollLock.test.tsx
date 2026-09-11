// @vitest-environment jsdom
import { cleanup, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetScrollLockForTests, useScrollLock } from './useScrollLock';

function Lock({ on }: { on?: boolean }) {
  useScrollLock(on ?? true);
  return <div>locked</div>;
}

describe('useScrollLock', () => {
  beforeEach(() => {
    resetScrollLockForTests();
    vi.spyOn(window, 'scrollTo').mockImplementation((...args: unknown[]) => {
      const y =
        typeof args[0] === 'number'
          ? args[1]
          : (args[0] as { top?: number } | undefined)?.top;
      if (typeof y === 'number') {
        Object.defineProperty(window, 'scrollY', { value: y, configurable: true });
      }
    });
    Object.defineProperty(window, 'scrollY', { value: 240, configurable: true });
  });

  afterEach(() => {
    cleanup();
    resetScrollLockForTests();
    vi.restoreAllMocks();
  });

  it('freezes the page under an overlay and restores scroll on close', () => {
    const { unmount } = render(<Lock />);

    expect(document.documentElement.style.overflow).toBe('hidden');
    expect(document.body.style.overflow).toBe('hidden');
    expect(document.body.style.position).toBe('fixed');
    expect(document.body.style.top).toBe('-240px');

    unmount();

    expect(document.body.style.position).toBe('');
    expect(document.body.style.overflow).toBe('');
    expect(window.scrollTo).toHaveBeenCalledWith(0, 240);
  });

  it('keeps the lock while nested overlays are open', () => {
    const { rerender, unmount } = render(
      <>
        <Lock />
        <Lock />
      </>,
    );

    expect(document.body.style.position).toBe('fixed');

    rerender(<Lock />);
    expect(document.body.style.position).toBe('fixed');

    unmount();
    expect(document.body.style.position).toBe('');
  });

  it('does not lock when locked=false', () => {
    render(<Lock on={false} />);
    expect(document.body.style.position).toBe('');
    expect(document.body.style.overflow).toBe('');
  });
});
