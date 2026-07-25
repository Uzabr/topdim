import { clearSessionQueryData } from './queryClient';

type SessionReset = () => void;

export const SESSION_AUTH_TRANSPORT_TIMEOUT_MS = 15_000;
export const SESSION_LOGOUT_TRANSPORT_TIMEOUT_MS = 5_000;

export class SessionAuthenticationTransportTimeoutError extends Error {
  constructor() {
    super('Session authentication transport timed out');
    this.name = 'SessionAuthenticationTransportTimeoutError';
  }
}

const sessionResets = new Set<SessionReset>();
let sessionGeneration = 0;
let authenticationTransportTail: Promise<void> = Promise.resolve();
let activeAuthenticationTransport: {
  controller: AbortController;
  abortable: boolean;
} | null = null;

export function advanceSessionGeneration() {
  sessionGeneration += 1;
  return sessionGeneration;
}

export function captureSessionGeneration() {
  return sessionGeneration;
}

export function isSessionGenerationCurrent(generation: number) {
  return generation === sessionGeneration;
}

export function abortSessionAuthenticationTransport() {
  if (activeAuthenticationTransport?.abortable) {
    activeAuthenticationTransport.controller.abort();
  }
}

export function runSessionAuthenticationTransport<T>(
  operation: (signal: AbortSignal) => T | Promise<T>,
  options: {
    abortable?: boolean;
    timeoutMs?: number;
  } = {},
): Promise<T> {
  const run = async () => {
    const controller = new AbortController();
    const transport = {
      controller,
      abortable: options.abortable ?? true,
    };
    activeAuthenticationTransport = transport;
    let timeoutId: ReturnType<typeof setTimeout> | undefined;
    try {
      const operationResult = Promise.resolve(operation(controller.signal));
      if (options.timeoutMs == null) {
        return await operationResult;
      }
      const timeout = new Promise<never>((_resolve, reject) => {
        timeoutId = setTimeout(() => {
          const timeoutError =
            new SessionAuthenticationTransportTimeoutError();
          controller.abort(timeoutError);
          reject(timeoutError);
        }, options.timeoutMs);
      });
      return await Promise.race([operationResult, timeout]);
    } finally {
      if (timeoutId != null) {
        clearTimeout(timeoutId);
      }
      if (activeAuthenticationTransport === transport) {
        activeAuthenticationTransport = null;
      }
    }
  };
  const result = authenticationTransportTail.then(run, run);
  authenticationTransportTail = result.then(
    () => undefined,
    () => undefined,
  );
  return result;
}

export function registerSessionReset(reset: SessionReset) {
  sessionResets.add(reset);
  return () => sessionResets.delete(reset);
}

export function establishAuthenticatedSession(
  replacingAuthenticatedSession: boolean,
  applySession: () => void,
) {
  abortSessionAuthenticationTransport();
  const generation = advanceSessionGeneration();
  clearSessionQueryData();
  if (replacingAuthenticatedSession) {
    for (const reset of sessionResets) {
      reset();
    }
  }
  applySession();
  return {
    generation,
    replacedAuthenticatedSession: replacingAuthenticatedSession,
  };
}

export function invalidateClientSession() {
  abortSessionAuthenticationTransport();
  advanceSessionGeneration();
  clearSessionQueryData();
  localStorage.removeItem('accessToken');
  localStorage.removeItem('user');
  for (const reset of sessionResets) {
    reset();
  }
}
