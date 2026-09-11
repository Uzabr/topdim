import { describe, expect, it, vi } from 'vitest';
import { withMinDelay } from './withMinDelay';

describe('withMinDelay', () => {
  it('does not resolve before the minimum delay even if work is already done', async () => {
    vi.useFakeTimers();
    const pending = withMinDelay(Promise.resolve('ok'), 500);
    let settled = '';
    void pending.then((value) => {
      settled = value;
    });

    await vi.advanceTimersByTimeAsync(499);
    expect(settled).toBe('');

    await vi.advanceTimersByTimeAsync(1);
    expect(await pending).toBe('ok');
    vi.useRealTimers();
  });

  it('still waits on failure so a loader does not flash off immediately', async () => {
    vi.useFakeTimers();
    const pending = withMinDelay(Promise.reject(new Error('nope')), 500);
    const result = pending.then(
      () => 'resolved',
      () => 'rejected',
    );

    await vi.advanceTimersByTimeAsync(499);
    let status = 'pending';
    void result.then((value) => {
      status = value;
    });
    await Promise.resolve();
    expect(status).toBe('pending');

    await vi.advanceTimersByTimeAsync(1);
    await expect(pending).rejects.toThrow('nope');
    vi.useRealTimers();
  });
});
