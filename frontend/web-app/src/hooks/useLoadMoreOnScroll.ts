import { useEffect, useRef } from 'react';

/**
 * Догружает следующую страницу, когда sentinel появляется во viewport
 * (с запасом rootMargin, чтобы запрос ушёл чуть раньше конца списка).
 */
export function useLoadMoreOnScroll(
  enabled: boolean,
  onLoadMore: () => void,
) {
  const ref = useRef<HTMLDivElement>(null);
  const onLoadMoreRef = useRef(onLoadMore);
  onLoadMoreRef.current = onLoadMore;

  useEffect(() => {
    if (!enabled) return;
    const node = ref.current;
    if (!node) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) onLoadMoreRef.current();
      },
      { root: null, rootMargin: '240px', threshold: 0 },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [enabled]);

  return ref;
}
