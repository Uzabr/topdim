import { useEffect, useState } from 'react';
import { Heart, MapPin, Menu, Search, ShoppingBag } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../../store/authStore';
import { useCartStore } from '../../store/cartStore';
import { useFavoritesStore } from '../../store/favoritesStore';
import { useAddressStore } from '../../store/addressStore';
import { useLocalePath } from '../../hooks/useLocalePath';
import Logo from './Logo';
import SearchOverlay from './SearchOverlay';
import './HeaderDesktop.css';

/** Ниже этого сдвига шапка светлая, выше — тёмная полупрозрачная. */
const SCROLL_THRESHOLD = 80;

type AddressMode = 'idle' | 'wait' | 'set';

/**
 * Десктопная шапка (≥768px), референс: design_handoff_sizbiz/«Шапка - демо анимаций.dc.html».
 * Три плавающие таблетки: [адрес] [лого · каталог · избранное · поиск · корзина] [войти].
 * Капля корзины, воронка избранного и микро-моушн — шаг 6.
 */
export default function HeaderDesktop() {
  const { t, i18n } = useTranslation();
  const lp = useLocalePath();
  const location = useLocation();
  const navigate = useNavigate();

  const { totalItems } = useCartStore();
  const { favoriteIds } = useFavoritesStore();
  const { isAuthenticated, user } = useAuthStore();
  const { address, setAddress, clearAddress } = useAddressStore();

  const [scrolled, setScrolled] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [editingAddress, setEditingAddress] = useState(false);
  const [draft, setDraft] = useState('');

  const mode: AddressMode = editingAddress ? 'wait' : address ? 'set' : 'idle';
  const lang = i18n.language?.substring(0, 2) === 'uz' ? 'uz' : 'ru';
  const onFavorites = location.pathname.endsWith('/favorites');

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > SCROLL_THRESHOLD);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  const startEditing = () => {
    setDraft(address);
    setEditingAddress(true);
  };

  const commitAddress = () => {
    if (draft.trim()) setAddress(draft);
    else clearAddress();
    setEditingAddress(false);
  };

  const switchLang = (next: 'ru' | 'uz') => {
    if (next === lang) return;
    i18n.changeLanguage(next);
    localStorage.setItem('language', next);
    const pathWithoutLang = location.pathname.replace(/^\/(ru|uz)/, '');
    navigate(`/${next}${pathWithoutLang || '/'}${location.search}`, { replace: true });
  };

  return (
    <>
      <header className={`hdr${scrolled ? ' hdr--scrolled' : ''}`}>
        {/* ═══ Адрес: «нетерпеливый пин» ═══ */}
        <div className="hdr__pill hdr__address">
          {mode === 'idle' && (
            <>
              <span className="hdr__address-label">{t('header.addressPrompt')}</span>
              <button type="button" className="hdr__address-btn" onClick={startEditing}>
                {t('header.addressAction')}
              </button>
            </>
          )}

          {mode === 'wait' && (
            <>
              <input
                autoFocus
                className="hdr__address-input"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') commitAddress();
                  if (e.key === 'Escape') setEditingAddress(false);
                }}
                placeholder={t('header.addressPlaceholder')}
              />
              <button
                type="button"
                className={`hdr__pin${draft.trim() ? '' : ' hdr__pin--hop'}`}
                onClick={commitAddress}
                aria-label={t('header.addressAction')}
              >
                <MapPin size={16} />
              </button>
            </>
          )}

          {mode === 'set' && (
            <>
              <button type="button" className="hdr__address-value" onClick={startEditing}>
                {address}
              </button>
              <span className="hdr__pin" aria-hidden="true">
                <MapPin size={16} />
              </span>
            </>
          )}
        </div>

        {/* ═══ Центральная группа ═══ */}
        <div className="hdr__pill hdr__center">
          <Logo />

          <Link to={lp('/coupons')} className="hdr__icon" aria-label={t('header.catalog')}>
            <Menu size={18} />
          </Link>

          <Link
            to={lp('/favorites')}
            className={`hdr__icon${onFavorites ? ' hdr__icon--active' : ''}`}
            aria-label={t('nav.favorites')}
          >
            <Heart size={17} fill={onFavorites ? 'currentColor' : 'none'} />
            {favoriteIds.length > 0 && <span className="hdr__icon-count">{favoriteIds.length}</span>}
          </Link>

          <button type="button" className="hdr__search" onClick={() => setSearchOpen(true)}>
            <Search size={16} />
            <span>{t('header.searchPlaceholder')}</span>
          </button>

          <Link to={lp('/cart')} className="hdr__cart" aria-label={t('cart.title')}>
            <ShoppingBag size={17} />
            {totalItems > 0 && <span className="hdr__cart-badge">{totalItems}</span>}
          </Link>
        </div>

        {/* ═══ Язык + вход ═══ */}
        <div className="hdr__pill hdr__auth">
          <div className="hdr__lang" role="group" aria-label={t('header.language')}>
            {(['ru', 'uz'] as const).map((code) => (
              <button
                key={code}
                type="button"
                className={`hdr__lang-btn${lang === code ? ' hdr__lang-btn--active' : ''}`}
                onClick={() => switchLang(code)}
              >
                {code.toUpperCase()}
              </button>
            ))}
          </div>

          {isAuthenticated ? (
            <Link to={lp('/profile')} className="hdr__avatar" aria-label={t('header.profile')}>
              {user?.firstName?.charAt(0)?.toUpperCase() ?? '?'}
            </Link>
          ) : (
            <Link to={lp('/login')} className="hdr__login">
              {t('header.login')}
            </Link>
          )}
        </div>
      </header>

      {searchOpen && <SearchOverlay onClose={() => setSearchOpen(false)} />}
    </>
  );
}
