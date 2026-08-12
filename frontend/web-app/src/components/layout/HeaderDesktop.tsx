import { useEffect, useState } from 'react';
import { ArrowLeft, Heart, MapPin, Search, ShoppingBag } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { switchLanguage } from '../../i18n';
import { useAuthStore } from '../../store/authStore';
import { useCartStore } from '../../store/cartStore';
import { useFavoritesStore } from '../../store/favoritesStore';
import { useAddressStore } from '../../store/addressStore';
import { useLocalePath } from '../../hooks/useLocalePath';
import Logo from './Logo';
import SearchOverlay from './SearchOverlay';
import LoginModal from '../auth/LoginModal';
import UserAvatar from '../ui/UserAvatar';
import './HeaderDesktop.css';

/** Ниже этого сдвига шапка светлая, выше — тёмная полупрозрачная. */
const SCROLL_THRESHOLD = 80;

/** Капля растягивается в полёте ровно столько, сколько длится переезд. */
const STRETCH_MS = 320;
const GULP_MS = 260;
const BURST_MS = 750;
const PLACEHOLDER_MS = 2800;

type AddressMode = 'idle' | 'wait' | 'set';

/**
 * Десктопная шапка (≥768px), референс: design_handoff_sizbiz/«Шапка - демо анимаций.dc.html».
 * Три плавающие таблетки: [адрес] [лого · каталог · избранное · поиск · корзина] [войти].
 *
 * Капля корзины: на /cart и /favorites круглая кнопка перетекает по таблетке влево
 * и становится «← Назад» — освободившееся место схлопывают спейсеры-слоты.
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
  const [loginOpen, setLoginOpen] = useState(false);
  const [editingAddress, setEditingAddress] = useState(false);
  const [draft, setDraft] = useState('');

  const mode: AddressMode = editingAddress ? 'wait' : address ? 'set' : 'idle';
  const lang = i18n.language?.substring(0, 2) === 'uz' ? 'uz' : 'ru';
  const onFavorites = location.pathname.endsWith('/favorites');
  const onCart = location.pathname.endsWith('/cart');

  // ── Капля: слева на корзине/избранном, иначе справа ──
  const dropLeft = onCart || onFavorites;
  const [prevDropLeft, setPrevDropLeft] = useState(dropLeft);
  const [stretch, setStretch] = useState(false);
  if (prevDropLeft !== dropLeft) {
    setPrevDropLeft(dropLeft);
    setStretch(true);
  }

  // ── «Глоток» счётчика корзины и всплеск сердец: реагируем на рост числа ──
  const [prevItems, setPrevItems] = useState(totalItems);
  const [gulp, setGulp] = useState(false);
  if (prevItems !== totalItems) {
    setPrevItems(totalItems);
    if (totalItems > prevItems) setGulp(true);
  }

  const favCount = favoriteIds.length;
  const [prevFav, setPrevFav] = useState(favCount);
  const [burst, setBurst] = useState(false);
  if (prevFav !== favCount) {
    setPrevFav(favCount);
    if (favCount > prevFav) setBurst(true);
  }

  const [phIdx, setPhIdx] = useState(0);
  const [phShow, setPhShow] = useState(true);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > SCROLL_THRESHOLD);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    if (!stretch) return;
    const id = setTimeout(() => setStretch(false), STRETCH_MS);
    return () => clearTimeout(id);
  }, [stretch]);

  useEffect(() => {
    if (!gulp) return;
    const id = setTimeout(() => setGulp(false), GULP_MS);
    return () => clearTimeout(id);
  }, [gulp]);

  useEffect(() => {
    if (!burst) return;
    const id = setTimeout(() => setBurst(false), BURST_MS);
    return () => clearTimeout(id);
  }, [burst]);

  // Плейсхолдер поиска циклически меняет подсказки (fade + slide).
  const suggestions = t('header.searchSuggestions', { returnObjects: true }) as string[];
  const phrases = [t('header.searchPlaceholder'), ...(Array.isArray(suggestions) ? suggestions : [])];

  useEffect(() => {
    const id = setInterval(() => {
      setPhShow(false);
      setTimeout(() => {
        setPhIdx((i) => (i + 1) % phrases.length);
        setPhShow(true);
      }, 260);
    }, PLACEHOLDER_MS);
    return () => clearInterval(id);
  }, [phrases.length]);

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
    void switchLanguage(next);
    const pathWithoutLang = location.pathname.replace(/^\/(ru|uz)/, '');
    navigate(`/${next}${pathWithoutLang || '/'}${location.search}`, { replace: true });
  };

  const dropClick = () => {
    if (dropLeft) navigate(lp('/'));
    else navigate(lp('/cart'));
  };

  return (
    <>
      <header className={`hdr${scrolled ? ' hdr--scrolled' : ''}`}>
        {/* ═══ Лого: отдельная жёлтая таблетка у левого края (README §Шапка) ═══ */}
        <Logo className="hdr__logo" />

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

        {/* Спейсер: центральная группа встаёт строго по центру между лого/адресом и «Войти» */}
        <span className="hdr__spacer" aria-hidden="true" />

        {/* ═══ Центральная группа (короткая, max 560px, по центру) ═══ */}
        <div className="hdr__pill hdr__center">
          {/* Слот, в который «переливается» капля на корзине/избранном */}
          <span className="hdr__slot" style={{ width: dropLeft ? 48 : 0 }} aria-hidden="true" />

          <Link to={lp('/coupons')} className="hdr__icon hdr__burger" aria-label={t('header.catalog')}>
            <span className="hdr__bar" />
            <span className="hdr__bar" />
            <span className="hdr__bar" />
          </Link>

          <Link
            to={lp('/favorites')}
            id="fav-btn"
            className={`hdr__icon hdr__fav${onFavorites ? ' hdr__icon--active' : ''}${burst ? ' hdr__fav--pop' : ''}`}
            aria-label={t('nav.favorites')}
          >
            <Heart size={17} fill={onFavorites || favCount > 0 ? 'currentColor' : 'none'} />
            {favCount > 0 && <span className="hdr__icon-count">{favCount}</span>}
            {burst && (
              <span className="hdr__hearts" aria-hidden="true">
                <span className="hdr__heart" />
                <span className="hdr__heart" />
                <span className="hdr__heart" />
              </span>
            )}
          </Link>

          <button type="button" className="hdr__search" onClick={() => setSearchOpen(true)}>
            <Search size={16} />
            <span className={`hdr__ph${phShow ? '' : ' hdr__ph--out'}`}>{phrases[phIdx]}</span>
          </button>

          <span className="hdr__slot" style={{ width: dropLeft ? 0 : 48 }} aria-hidden="true" />

          {/* ═══ Капля: корзина ⇄ назад ═══ */}
          <button
            type="button"
            id="cart-drop"
            className={`hdr__drop${stretch ? ' hdr__drop--stretch' : ''}${gulp ? ' hdr__drop--gulp' : ''}`}
            style={{ left: dropLeft ? 8 : 'calc(100% - 48px)' }}
            onClick={dropClick}
            aria-label={dropLeft ? t('common.back') : t('cart.title')}
          >
            {dropLeft ? <ArrowLeft size={17} /> : <ShoppingBag size={17} />}
            {!dropLeft && totalItems > 0 && (
              <span className={`hdr__cart-badge${gulp ? ' hdr__cart-badge--gulp' : ''}`}>
                {totalItems}
              </span>
            )}
          </button>
        </div>

        {/* Спейсер справа от центральной группы */}
        <span className="hdr__spacer" aria-hidden="true" />

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
              <UserAvatar avatarUrl={user?.avatarUrl} firstName={user?.firstName} />
            </Link>
          ) : (
            <button type="button" className="hdr__login" onClick={() => setLoginOpen(true)}>
              {t('header.login')}
              <span className="hdr__login-arrow" aria-hidden="true">
                →
              </span>
            </button>
          )}
        </div>
      </header>

      {searchOpen && <SearchOverlay onClose={() => setSearchOpen(false)} />}
      {loginOpen && <LoginModal onClose={() => setLoginOpen(false)} />}
    </>
  );
}
