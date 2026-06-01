import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Cookie, X } from 'lucide-react';
import { useLocalePath } from '../../hooks/useLocalePath';
import './CookieConsent.css';

const COOKIE_KEY = 'topdim_cookie_consent';

export default function CookieConsent() {
  const { t } = useTranslation();
  const lp = useLocalePath();
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
          <p className="cookie-consent__title">{t('cookie.title')}</p>
          <p className="cookie-consent__desc">
            {t('cookie.desc')}{' '}
            <Link to={lp('/privacy')}>{t('cookie.privacyLink')}</Link>.
          </p>
        </div>
        <div className="cookie-consent__actions">
          <button
            className="cookie-consent__btn cookie-consent__btn--accept"
            onClick={() => dismiss(true)}
          >
            {t('cookie.accept')}
          </button>
          <button
            className="cookie-consent__btn cookie-consent__btn--decline"
            onClick={() => dismiss(false)}
          >
            {t('cookie.decline')}
          </button>
        </div>
        <button
          className="cookie-consent__close"
          onClick={() => dismiss(false)}
          aria-label={t('common.close')}
        >
          <X size={18} />
        </button>
      </div>
    </div>
  );
}
