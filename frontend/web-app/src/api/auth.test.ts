import { describe, expect, it, vi } from 'vitest';
import apiClient from './client';
import { authApi } from './auth';

describe('auth API session metadata', () => {
  it('sends the captured logout JWT and stale generation without refresh fallback', async () => {
    const signal = new AbortController().signal;
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: {
        success: true,
        data: null,
        timestamp: '2026-07-25T10:00:00Z',
      },
    });

    await (authApi.logout as unknown as (context: {
      accessToken: string;
      sessionGeneration: number;
      signal: globalThis.AbortSignal;
    }) => Promise<unknown>)({
      accessToken: 'token-a',
      sessionGeneration: 17,
      signal: signal as globalThis.AbortSignal,
    });

    expect(post).toHaveBeenCalledWith(
      '/api/v1/auth/logout',
      undefined,
      {
        _sessionGeneration: 17,
        _skipAuthRefresh: true,
        headers: { Authorization: 'Bearer token-a' },
        signal,
        timeout: 5_000,
      },
    );
  });

  // Контракт с бэкендом: GoogleAuthRequest (identity-service) требует поле
  // именно `idToken` (@NotBlank) — см. dto/GoogleAuthRequest.java.
  it('sends the Google ID-token to /api/v1/auth/google as { idToken }', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: {
        success: true,
        data: null,
        timestamp: '2026-08-04T10:00:00Z',
      },
    });

    await authApi.googleAuth('fake-id-token');

    expect(post).toHaveBeenCalledWith(
      '/api/v1/auth/google',
      { idToken: 'fake-id-token' },
      expect.objectContaining({ _skipAuthRefresh: true }),
    );
  });
});
