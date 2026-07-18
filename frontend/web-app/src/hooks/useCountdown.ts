import { useEffect, useState } from 'react';

const SECOND = 1000;
const DAY = 86_400_000;

export interface Countdown {
  /** Осталось миллисекунд (0, если срок вышел). */
  msLeft: number;
  /** Меньше суток — можно показывать HH:MM:SS. */
  isUrgent: boolean;
  /** «06:12:44», tabular-nums на стороне CSS. */
  clock: string;
}

const pad = (n: number) => String(n).padStart(2, '0');

/**
 * Тик раз в секунду до заданной даты. Живой таймер купона дня и горящего тайла.
 * Пока до срока больше суток, тикать незачем — обновляемся раз в минуту.
 */
export function useCountdown(until?: string): Countdown | null {
  const target = until ? new Date(until).getTime() : NaN;
  const [now, setNow] = useState(() => Date.now());

  const msLeft = Number.isNaN(target) ? 0 : Math.max(0, target - now);
  const isUrgent = msLeft > 0 && msLeft < DAY;

  useEffect(() => {
    if (Number.isNaN(target)) return;
    const interval = setInterval(() => setNow(Date.now()), isUrgent ? SECOND : 60 * SECOND);
    return () => clearInterval(interval);
  }, [target, isUrgent]);

  if (Number.isNaN(target)) return null;

  const total = Math.floor(msLeft / SECOND);
  const clock = `${pad(Math.floor(total / 3600))}:${pad(Math.floor(total / 60) % 60)}:${pad(total % 60)}`;

  return { msLeft, isUrgent, clock };
}
