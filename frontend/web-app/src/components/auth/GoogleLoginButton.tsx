import { useEffect, useRef } from 'react';

interface GoogleCredentialResponse {
  /** ID-token (JWT), который отправляется на бэкенд как GoogleAuthRequest.idToken. */
  credential: string;
}

interface GoogleAccountsId {
  initialize: (config: {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
  }) => void;
  renderButton: (
    parent: HTMLElement,
    options: {
      type?: 'standard' | 'icon';
      theme?: 'outline' | 'filled_blue' | 'filled_black';
      size?: 'large' | 'medium' | 'small';
      shape?: 'rectangular' | 'pill' | 'circle' | 'square';
      width?: number;
    },
  ) => void;
}

declare global {
  interface Window {
    google?: {
      accounts: {
        id: GoogleAccountsId;
      };
    };
  }
}

interface GoogleLoginButtonProps {
  /**
   * Публичный OAuth client_id (VITE_GOOGLE_CLIENT_ID, тот же аудиенс, что
   * бэкенд проверяет как `aud`). Пусто → кнопка не рендерится и скрипт не
   * грузится — безопасная деградация вместо падения (напр. на dev без конфига).
   */
  clientId: string;
  onAuth: (idToken: string) => void;
}

const GSI_SCRIPT_SRC = 'https://accounts.google.com/gsi/client';

let gsiScriptPromise: Promise<void> | null = null;

/** Подгружает GIS-скрипт единожды на страницу (переиспользуется между рендерами). */
function loadGsiScript(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();
  if (gsiScriptPromise) return gsiScriptPromise;

  gsiScriptPromise = new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(
      `script[src="${GSI_SCRIPT_SRC}"]`,
    );
    if (existing) {
      existing.addEventListener('load', () => resolve());
      existing.addEventListener('error', () => reject(new Error('gsi script failed to load')));
      return;
    }
    const script = document.createElement('script');
    script.src = GSI_SCRIPT_SRC;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('gsi script failed to load'));
    document.head.appendChild(script);
  });
  return gsiScriptPromise;
}

/**
 * Кнопка входа через Google Identity Services (GIS).
 *
 * Динамически подгружает accounts.google.com/gsi/client (образец —
 * TelegramLoginButton), инициализирует `google.accounts.id` и рендерит
 * официальную кнопку Google в контейнер. При успешном входе GIS вызывает
 * callback с {@code { credential }} — это ID-token, который передаётся
 * наружу через {@code onAuth(idToken)}.
 */
export default function GoogleLoginButton({ clientId, onAuth }: GoogleLoginButtonProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const onAuthRef = useRef(onAuth);

  useEffect(() => {
    onAuthRef.current = onAuth;
  }, [onAuth]);

  useEffect(() => {
    if (!clientId) return;
    let cancelled = false;

    loadGsiScript()
      .then(() => {
        const container = containerRef.current;
        if (cancelled || !container || !window.google) return;
        const idApi = window.google.accounts.id as GoogleAccountsId & {
          __initializedClientId?: string;
        };
        // initialize() — глобальный на страницу; зовём один раз на client_id,
        // иначе GSI пишет в консоль "initialize() called multiple times" при
        // повторных маунтах карточки (флаг храним на самом объекте GIS).
        if (idApi.__initializedClientId !== clientId) {
          idApi.initialize({
            client_id: clientId,
            callback: (response) => onAuthRef.current(response.credential),
          });
          idApi.__initializedClientId = clientId;
        }
        // Ширина официальной кнопки — под контейнер (GSI ограничивает 400px).
        // Жёсткие 280px обрезались в узкой ячейке → «текст не помещается».
        const width = Math.min(400, Math.max(200, Math.round(container.offsetWidth) || 300));
        container.innerHTML = '';
        idApi.renderButton(container, {
          type: 'standard',
          theme: 'outline',
          size: 'large',
          shape: 'pill',
          width,
        });
      })
      .catch(() => {
        // Скрипт заблокирован / сеть недоступна — деградируем молча, не роняем страницу.
      });

    return () => {
      cancelled = true;
    };
  }, [clientId]);

  // Client-id не сконфигурирован (напр. dev-сборка без VITE_GOOGLE_CLIENT_ID) — не рендерим.
  if (!clientId) return null;

  return <div ref={containerRef} className="google-login-widget" />;
}
