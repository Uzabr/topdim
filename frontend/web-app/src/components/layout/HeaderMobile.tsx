import { useEffect, useRef, useState } from 'react';
import { LayoutGrid, User } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../../store/authStore';
import { useLocalePath } from '../../hooks/useLocalePath';
import CatalogSheet from '../mobile/CatalogSheet';
import LoginModal from '../auth/LoginModal';
import UserAvatar from '../ui/UserAvatar';
import Logo from './Logo';
import './HeaderMobile.css';

/**
 * Мобильная шапка: лого-таблетка слева, справа полупрозрачная таблетка с
 * каталогом и аватаром. Гость жмёт аватар → вход; залогинен → мини-шторка
 * (язык, настройки, выйти). Референс: «Мобилка - 2 Главная».
 */
export default function HeaderMobile() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const lp = useLocalePath();
  const { isAuthenticated, user, logout } = useAuthStore();

  const [catalogOpen, setCatalogOpen] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const accountRef = useRef<HTMLDivElement>(null);

  const lang = i18n.language?.substring(0, 2) === 'uz' ? 'uz' : 'ru';

  useEffect(() => {
    if (!accountOpen) return;
    const onDown = (e: MouseEvent) => {
      if (!accountRef.current?.contains(e.target as Node)) setAccountOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [accountOpen]);

  const switchLang = (next: 'ru' | 'uz') => {
    if (next === lang) return;
    i18n.changeLanguage(next);
    localStorage.setItem('language', next);
    const path = location.pathname.replace(/^\/(ru|uz)/, '');
    navigate(`/${next}${path || '/'}${location.search}`, { replace: true });
  };

  return (
    <>
      <header className="mhdr">
        <Logo size="md" />

        <div className="mhdr__pill" ref={accountRef}>
          <button
            type="button"
            className="mhdr__icon"
            onClick={() => setCatalogOpen(true)}
            aria-label={t('header.catalog')}
          >
            <LayoutGrid size={16} strokeWidth={1.9} />
          </button>

          <button
            type="button"
            className={`mhdr__avatar${isAuthenticated ? ' mhdr__avatar--auth' : ''}`}
            onClick={() => (isAuthenticated ? setAccountOpen((v) => !v) : setLoginOpen(true))}
            aria-label={t('header.profile')}
          >
            {isAuthenticated ? (
              <UserAvatar avatarUrl={user?.avatarUrl} firstName={user?.firstName} />
            ) : (
              <User size={16} strokeWidth={1.9} />
            )}
          </button>

          {accountOpen && isAuthenticated && (
            <div className="macc">
              <div className="macc__head">
                <span className="macc__name">{user?.firstName}</span>
                {user?.phone && <span className="macc__phone">{user.phone}</span>}
              </div>

              <div className="macc__row">
                <span className="macc__label">{t('header.language')}</span>
                <span className="macc__langs">
                  {(['ru', 'uz'] as const).map((code) => (
                    <button
                      key={code}
                      type="button"
                      className={`macc__lang${lang === code ? ' macc__lang--active' : ''}`}
                      onClick={() => switchLang(code)}
                    >
                      {code}
                    </button>
                  ))}
                </span>
              </div>

              <Link
                to={`${lp('/profile')}?tab=settings`}
                className="macc__item"
                onClick={() => setAccountOpen(false)}
              >
                {t('profile.tabs.settings')}
              </Link>

              <button
                type="button"
                className="macc__item macc__item--muted"
                onClick={() => {
                  setAccountOpen(false);
                  logout();
                }}
              >
                {t('profile.logout')}
              </button>
            </div>
          )}
        </div>
      </header>

      {catalogOpen && <CatalogSheet onClose={() => setCatalogOpen(false)} />}
      {loginOpen && <LoginModal onClose={() => setLoginOpen(false)} />}
    </>
  );
}
