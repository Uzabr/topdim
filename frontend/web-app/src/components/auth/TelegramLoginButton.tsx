import { useEffect, useRef } from 'react';
import type { TelegramAuthPayload } from '../../api/auth';

interface TelegramLoginButtonProps {
  /** Username бота без @ (напр. sizbiz_uz_bot). У бота должен быть выставлен /setdomain. */
  botUsername: string;
  onAuth: (user: TelegramAuthPayload) => void;
  size?: 'large' | 'medium' | 'small';
  cornerRadius?: number;
}

/**
 * Официальный Telegram Login Widget.
 *
 * Внедряет telegram.org/js/telegram-widget.js в контейнер; при успешной
 * авторизации Telegram вызывает {@code data-onauth} → {@code onAuth(user)}.
 * Работает только на домене, заданном боту через @BotFather /setdomain
 * (у нас — sizbiz.uz). На localhost виджет не отдаёт подпись.
 */
export default function TelegramLoginButton({
  botUsername,
  onAuth,
  size = 'large',
  cornerRadius = 12,
}: TelegramLoginButtonProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const onAuthRef = useRef(onAuth);

  useEffect(() => {
    onAuthRef.current = onAuth;
  }, [onAuth]);

  useEffect(() => {
    // Уникальное имя глобального колбэка — виджет вызывает его по имени из data-onauth.
    const callbackName = `onTelegramAuth_${Math.random().toString(36).slice(2)}`;
    const w = window as unknown as Record<string, unknown>;
    w[callbackName] = (user: TelegramAuthPayload) => onAuthRef.current(user);

    const script = document.createElement('script');
    script.src = 'https://telegram.org/js/telegram-widget.js?22';
    script.async = true;
    script.setAttribute('data-telegram-login', botUsername);
    script.setAttribute('data-size', size);
    script.setAttribute('data-radius', String(cornerRadius));
    script.setAttribute('data-request-access', 'write');
    script.setAttribute('data-onauth', `${callbackName}(user)`);

    const container = containerRef.current;
    container?.appendChild(script);

    return () => {
      delete w[callbackName];
      if (container) container.innerHTML = '';
    };
  }, [botUsername, size, cornerRadius]);

  return <div ref={containerRef} className="tg-login-widget" />;
}
