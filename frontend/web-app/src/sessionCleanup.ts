import { clearSessionQueryData } from './queryClient';

type SessionReset = () => void;

const sessionResets = new Set<SessionReset>();

export function registerSessionReset(reset: SessionReset) {
  sessionResets.add(reset);
  return () => sessionResets.delete(reset);
}

export function invalidateClientSession() {
  clearSessionQueryData();
  localStorage.removeItem('accessToken');
  localStorage.removeItem('user');
  for (const reset of sessionResets) {
    reset();
  }
}
