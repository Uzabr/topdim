// @vitest-environment jsdom
import axios, {
  AxiosError,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  advanceSessionGeneration,
  captureSessionGeneration,
  invalidateClientSession,
} from '../sessionCleanup';
import apiClient from './client';

interface SessionRequestConfig extends InternalAxiosRequestConfig {
  _sessionGeneration?: number;
  _skipAuthRefresh?: boolean;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((complete, fail) => {
    resolve = complete;
    reject = fail;
  });
  return { promise, resolve, reject };
}

function successResponse(
  config: InternalAxiosRequestConfig,
): AxiosResponse {
  return {
    data: {},
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  };
}

function unauthorized(config: InternalAxiosRequestConfig) {
  return new AxiosError(
    'Unauthorized',
    'ERR_BAD_REQUEST',
    config,
    undefined,
    {
      data: {},
      status: 401,
      statusText: 'Unauthorized',
      headers: {},
      config,
    },
  );
}

describe('API session generation isolation', () => {
  beforeEach(() => {
    invalidateClientSession();
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('stamps the request synchronously with the account that dispatched it', async () => {
    const accountAGeneration = advanceSessionGeneration();
    localStorage.setItem('accessToken', 'token-a');
    const observedConfig = deferred<InternalAxiosRequestConfig>();

    const request = apiClient.get('/session-stamp', {
      adapter: async (config) => {
        observedConfig.resolve(config);
        return successResponse(config);
      },
    });

    invalidateClientSession();
    advanceSessionGeneration();
    localStorage.setItem('accessToken', 'token-b');

    const config = await observedConfig.promise as SessionRequestConfig;
    await request;

    expect(config.headers.Authorization).toBe('Bearer token-a');
    expect(config._sessionGeneration).toBe(accountAGeneration);
  });

  it('does not let a late account A refresh failure invalidate account B', async () => {
    advanceSessionGeneration();
    localStorage.setItem('accessToken', 'token-a');
    localStorage.setItem('user', JSON.stringify({ id: 1 }));
    const lateRefresh = deferred<Awaited<ReturnType<typeof axios.post>>>();
    const refreshSpy = vi.spyOn(axios, 'post').mockReturnValue(lateRefresh.promise);

    const requestResult = apiClient.get('/private-a', {
      adapter: async (config) => {
        throw unauthorized(config);
      },
    }).catch((error: unknown) => error);

    await vi.waitFor(() => expect(refreshSpy).toHaveBeenCalledOnce());

    advanceSessionGeneration();
    localStorage.setItem('accessToken', 'token-b');
    localStorage.setItem('user', JSON.stringify({ id: 2 }));

    lateRefresh.reject(new Error('late account A refresh failed'));
    await requestResult;

    expect(localStorage.getItem('accessToken')).toBe('token-b');
    expect(JSON.parse(localStorage.getItem('user') ?? '{}')).toEqual({ id: 2 });
  });

  it('still refreshes and retries a 401 for the current session', async () => {
    advanceSessionGeneration();
    localStorage.setItem('accessToken', 'old-token');
    vi.spyOn(axios, 'post').mockResolvedValue({
      data: {
        data: {
          accessToken: 'refreshed-token',
          tokenType: 'Bearer',
          expiresIn: 900,
          user: {
            id: 7,
            email: 'current@example.com',
            firstName: 'Current',
            role: 'USER',
          },
        },
      },
    } as Awaited<ReturnType<typeof axios.post>>);
    let attempts = 0;

    await apiClient.get('/current-session', {
      adapter: async (config) => {
        attempts += 1;
        if (attempts === 1) {
          throw unauthorized(config);
        }
        return successResponse(config);
      },
    });

    expect(attempts).toBe(2);
    expect(axios.post).toHaveBeenCalledOnce();
    expect(localStorage.getItem('accessToken')).toBe('refreshed-token');
  });

  it('does not recursively refresh an authentication endpoint marked to skip refresh', async () => {
    localStorage.setItem('accessToken', 'current-token');
    const refreshSpy = vi.spyOn(axios, 'post');
    const config: SessionRequestConfig = {
      _skipAuthRefresh: true,
      adapter: async (requestConfig) => {
        throw unauthorized(requestConfig);
      },
      headers: new axios.AxiosHeaders(),
    };

    const result = await apiClient.get('/auth-endpoint', config)
      .catch((error: unknown) => error);

    expect(result).toBeInstanceOf(AxiosError);
    expect(refreshSpy).not.toHaveBeenCalled();
    expect(localStorage.getItem('accessToken')).toBe('current-token');
  });

  it('does not refresh a tokenless 401 after logout', async () => {
    const refreshSpy = vi.spyOn(axios, 'post').mockRejectedValue(
      new Error('refresh must not be called'),
    );

    const result = await apiClient.get('/public-after-logout', {
      adapter: async (config) => {
        throw unauthorized(config);
      },
    }).catch((error: unknown) => error);

    expect(result).toBeInstanceOf(AxiosError);
    expect(refreshSpy).not.toHaveBeenCalled();
    expect(localStorage.getItem('accessToken')).toBeNull();
  });

  it('aborts an in-flight refresh when its session is invalidated', async () => {
    localStorage.setItem('accessToken', 'token-a');
    localStorage.setItem('user', JSON.stringify({ id: 1 }));
    let refreshSignal: AbortSignal | undefined;
    const refreshSpy = vi.spyOn(axios, 'post').mockImplementation(
      ((_url, _data, config?: { signal?: AbortSignal }) =>
        new Promise((_, reject) => {
          refreshSignal = config?.signal;
          config?.signal?.addEventListener('abort', () => {
            reject(new axios.CanceledError('refresh aborted'));
          });
        })) as typeof axios.post,
    );

    const requestResult = apiClient.get('/private-a', {
      adapter: async (config) => {
        throw unauthorized(config);
      },
    }).catch((error: unknown) => error);

    await vi.waitFor(() => expect(refreshSpy).toHaveBeenCalledOnce());
    expect(refreshSignal).toBeInstanceOf(AbortSignal);

    invalidateClientSession();

    expect(refreshSignal?.aborted).toBe(true);
    await requestResult;
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
  });

  it('invalidates the current session when its refresh genuinely fails', async () => {
    localStorage.setItem('user', JSON.stringify({ id: 7 }));
    const generation = captureSessionGeneration();
    vi.spyOn(axios, 'post').mockRejectedValue(
      new Error('current refresh failed'),
    );

    await apiClient.get('/current-private', {
      headers: { Authorization: 'Bearer expired-token' },
      adapter: async (config) => {
        throw unauthorized(config);
      },
    }).catch((error: unknown) => error);

    expect(captureSessionGeneration()).toBe(generation + 1);
    expect(localStorage.getItem('user')).toBeNull();
  });

  it('treats a transport-deadline abort as refresh failure, not supersession', async () => {
    vi.useFakeTimers();
    try {
      localStorage.setItem('user', JSON.stringify({ id: 7 }));
      const generation = captureSessionGeneration();
      vi.spyOn(axios, 'post').mockImplementation(
        ((_url, _data, config?: { signal?: AbortSignal }) =>
          new Promise((_, reject) => {
            config?.signal?.addEventListener('abort', () => {
              reject(new axios.CanceledError('refresh deadline'));
            });
          })) as typeof axios.post,
      );

      const request = apiClient.get('/deadline-private', {
        headers: { Authorization: 'Bearer expired-token' },
        adapter: async (config) => {
          throw unauthorized(config);
        },
      }).catch((error: unknown) => error);
      await vi.advanceTimersByTimeAsync(0);

      await vi.advanceTimersByTimeAsync(15_000);
      await request;

      expect(captureSessionGeneration()).toBe(generation + 1);
      expect(localStorage.getItem('user')).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });
});
