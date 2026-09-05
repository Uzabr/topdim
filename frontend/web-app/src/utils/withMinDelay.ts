/** Минимальное время показа лоадера при догрузке следующей страницы (infinite scroll). */
export const INFINITE_SCROLL_LOADER_MS = 500;

/**
 * Не отдаёт результат раньше `ms`, даже если `promise` уже готов.
 * Нужно, чтобы спиннер на мобилке не мигал при быстром ответе API.
 */
export async function withMinDelay<T>(
  promise: Promise<T>,
  ms: number = INFINITE_SCROLL_LOADER_MS,
): Promise<T> {
  const delay = new Promise<void>((resolve) => {
    setTimeout(resolve, ms);
  });
  try {
    return await promise;
  } finally {
    await delay;
  }
}
