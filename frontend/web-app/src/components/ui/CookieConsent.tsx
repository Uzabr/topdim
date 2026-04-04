import { useState, useEffect } from 'react';
import { Cookie, X } from 'lucide-react';
import './CookieConsent.css';

const COOKIE_KEY = 'topdim_cookie_consent';

export default function CookieConsent() {
  const [visible, setVisible] = useState(false);
  const [animatingOut, setAnimatingOut] = useState(false);

  useEffect(() => {
    const consent = localStorage.getItem(COOKIE_KEY);
    if (!consent) {
      const timer = setTimeout(() => setVisible(true), 1200);
      return () => clearTimeout(timer);
    }
  }, []);

  const dismiss = (accepted: boolean) => {
    setAnimatingOut(true);
    localStorage.setItem(COOKIE_KEY, accepted ? 'accepted' : 'declined');
    setTimeout(() => setVisible(false), 400);
  };

  if (!visible) return null;

  return (
    <div className={`cookie-consent ${animatingOut ? 'cookie-consent--out' : ''}`}>
      <div className="cookie-consent__inner">
        <div className="cookie-consent__icon">
          <Cookie size={24} />
        </div>
        <div className="cookie-consent__text">
          <p className="cookie-consent__title">Мы используем файлы cookie</p>
          <p className="cookie-consent__desc">
            Для улучшения работы сайта и персонализации контента мы используем cookie.
            Продолжая использование сайта, вы соглашаетесь с нашей{' '}
            <a href="/privacy">политикой конфиденциальности</a>.
          </p>
        </div>
        <div className="cookie-consent__actions">
          <button
            className="cookie-consent__btn cookie-consent__btn--accept"
            onClick={() => dismiss(true)}
          >
            Принять все
          </button>
          <button
            className="cookie-consent__btn cookie-consent__btn--decline"
            onClick={() => dismiss(false)}
          >
            Отклонить
          </button>
        </div>
        <button
          className="cookie-consent__close"
          onClick={() => dismiss(false)}
          aria-label="Закрыть"
        >
          <X size={18} />
        </button>
      </div>
    </div>
  );
}
