function isEditableTarget(target: EventTarget | null): boolean {
  return target instanceof Element && Boolean(
    target.closest('input, textarea, select, [contenteditable="true"]'),
  );
}

/**
 * Блокирует copy/cut текста страницы. В полях ввода (поиск, телефон, OTP) не мешает.
 * Это защита от случайного копирования, не от DevTools.
 */
export function guardContentCopy(): void {
  const block = (event: Event) => {
    if (isEditableTarget(event.target)) return;
    event.preventDefault();
  };

  document.addEventListener('copy', block);
  document.addEventListener('cut', block);
}
