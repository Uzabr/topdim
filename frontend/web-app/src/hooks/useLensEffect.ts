import { useEffect } from 'react';

/**
 * «Лупа» мобильной ленты: карточки, уходящие от фокусной линии (44% экрана),
 * уменьшаются и тускнеют (design_handoff_sizbiz → «Мобилка - 2 Главная»).
 *
 * Работает по [data-lens] и пишет стиль напрямую — на каждый кадр скролла
 * React-состояние было бы слишком дорого.
 */
export function useLensEffect(enabled: boolean): void {
  useEffect(() => {
    if (!enabled) return;
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;

    let frame = 0;

    const update = () => {
      frame = 0;
      const h = window.innerHeight;
      const focal = h * 0.44;

      document.querySelectorAll<HTMLElement>('[data-lens]').forEach((node) => {
        const rect = node.getBoundingClientRect();
        const offset = (rect.top + rect.height / 2 - focal) / h;
        const scale = Math.max(0.82, Math.min(1, 1.02 - Math.abs(offset) * 0.32));
        node.style.transform = `scale(${scale.toFixed(3)})`;
        node.style.opacity = String(Math.max(0.55, 1 - Math.abs(offset) * 0.55));
      });
    };

    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update);
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    update();

    return () => {
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onScroll);
      if (frame) cancelAnimationFrame(frame);
    };
  }, [enabled]);
}
