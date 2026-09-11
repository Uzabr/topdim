import { useEffect } from 'react';

/**
 * Счётчик вложенных оверлеев: каталог + логин не должны отпускать скролл,
 * пока открыто хотя бы одно окно.
 */
let lockCount = 0;
let saved = {
  htmlOverflow: '',
  bodyOverflow: '',
  bodyPosition: '',
  bodyTop: '',
  bodyLeft: '',
  bodyRight: '',
  bodyWidth: '',
  scrollY: 0,
};

function applyLock() {
  saved = {
    htmlOverflow: document.documentElement.style.overflow,
    bodyOverflow: document.body.style.overflow,
    bodyPosition: document.body.style.position,
    bodyTop: document.body.style.top,
    bodyLeft: document.body.style.left,
    bodyRight: document.body.style.right,
    bodyWidth: document.body.style.width,
    scrollY: window.scrollY,
  };

  document.documentElement.style.overflow = 'hidden';
  document.body.style.overflow = 'hidden';
  // iOS Safari скроллит документ сквозь position:fixed оверлей, пока body
  // в потоке. Фиксируем body на текущем scrollY — страница под окном стоит.
  document.body.style.position = 'fixed';
  document.body.style.top = `-${saved.scrollY}px`;
  document.body.style.left = '0';
  document.body.style.right = '0';
  document.body.style.width = '100%';
}

function releaseLock() {
  document.documentElement.style.overflow = saved.htmlOverflow;
  document.body.style.overflow = saved.bodyOverflow;
  document.body.style.position = saved.bodyPosition;
  document.body.style.top = saved.bodyTop;
  document.body.style.left = saved.bodyLeft;
  document.body.style.right = saved.bodyRight;
  document.body.style.width = saved.bodyWidth;
  window.scrollTo(0, saved.scrollY);
}

/**
 * Пока хук активен, страница под оверлеем не скроллится.
 * Внутри окна скролл остаётся (overflow у шторки/дровера).
 */
export function useScrollLock(locked = true) {
  useEffect(() => {
    if (!locked) return;

    if (lockCount === 0) applyLock();
    lockCount += 1;

    return () => {
      lockCount -= 1;
      if (lockCount === 0) releaseLock();
    };
  }, [locked]);
}

/** Только для тестов: сбросить модульный счётчик между кейсами. */
export function resetScrollLockForTests() {
  lockCount = 0;
  saved = {
    htmlOverflow: '',
    bodyOverflow: '',
    bodyPosition: '',
    bodyTop: '',
    bodyLeft: '',
    bodyRight: '',
    bodyWidth: '',
    scrollY: 0,
  };
  document.documentElement.style.overflow = '';
  document.body.style.overflow = '';
  document.body.style.position = '';
  document.body.style.top = '';
  document.body.style.left = '';
  document.body.style.right = '';
  document.body.style.width = '';
}
