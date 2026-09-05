// @vitest-environment jsdom
import { cleanup, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useLoadMoreOnScroll } from './useLoadMoreOnScroll';

type ObserverInstance = {
  callback: IntersectionObserverCallback;
  observe: ReturnType<typeof vi.fn>;
  disconnect: ReturnType<typeof vi.fn>;
};

let latest: ObserverInstance | null = null;

function Probe({ enabled, onLoadMore }: { enabled: boolean; onLoadMore: () => void }) {
  const ref = useLoadMoreOnScroll(enabled, onLoadMore);
  return <div ref={ref} data-testid="sentinel" />;
}

describe('useLoadMoreOnScroll', () => {
  beforeEach(() => {
    latest = null;
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        observe = vi.fn();
        disconnect = vi.fn();
        unobserve = vi.fn();
        constructor(callback: IntersectionObserverCallback) {
          latest = {
            callback,
            observe: this.observe,
            disconnect: this.disconnect,
          };
        }
      },
    );
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('calls onLoadMore when the sentinel intersects and loading is enabled', () => {
    const onLoadMore = vi.fn();
    render(<Probe enabled onLoadMore={onLoadMore} />);

    expect(latest?.observe).toHaveBeenCalledOnce();

    latest?.callback(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      latest as unknown as IntersectionObserver,
    );

    expect(onLoadMore).toHaveBeenCalledOnce();
  });

  it('does not observe when loading is disabled', () => {
    render(<Probe enabled={false} onLoadMore={vi.fn()} />);
    expect(latest).toBeNull();
  });

  it('does not load more when the sentinel is not intersecting', () => {
    const onLoadMore = vi.fn();
    render(<Probe enabled onLoadMore={onLoadMore} />);

    latest?.callback(
      [{ isIntersecting: false } as IntersectionObserverEntry],
      latest as unknown as IntersectionObserver,
    );

    expect(onLoadMore).not.toHaveBeenCalled();
  });

  it('disconnects the observer on unmount', () => {
    const { unmount } = render(<Probe enabled onLoadMore={vi.fn()} />);
    const disconnect = latest?.disconnect;
    unmount();
    expect(disconnect).toHaveBeenCalledOnce();
  });
});
