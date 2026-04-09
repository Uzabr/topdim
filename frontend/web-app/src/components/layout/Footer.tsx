import { Instagram, Send, Mail, MapPin, Phone, Smartphone } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useLocalePath } from '../../hooks/useLocalePath';
import './Footer.css';

export default function Footer() {
  const { t } = useTranslation();
  const lp = useLocalePath();
  return (
    <footer className="app-footer">
      <div className="container footer-content">
        {/* Brand */}
        <div className="footer-column footer-brand">
          <h2 className="footer-logo text-gradient">TopDim</h2>
          <p className="footer-desc">{t('footer.desc')}</p>
          <div className="footer-socials">
            <a href="https://instagram.com" target="_blank" rel="noopener noreferrer" aria-label="Instagram">
              <Instagram size={20} />
            </a>
            <a href="https://t.me" target="_blank" rel="noopener noreferrer" aria-label="Telegram">
              <Send size={20} />
            </a>
          </div>
        </div>

        {/* User links */}
        <div className="footer-column">
          <h3>Пользователям</h3>
          <ul>
            <li><Link to={lp('/coupons')}>Каталог купонов</Link></li>
            <li><Link to={lp('/favorites')}>Избранное</Link></li>
            <li><Link to={lp('/bazaar')}>Онлайн базар</Link></li>
            <li><Link to={lp('/profile')}>Мои купоны</Link></li>
          </ul>
        </div>

        {/* Partners */}
        <div className="footer-column">
          <h3>Партнёрам</h3>
          <ul>
            <li><Link to={lp('/partners')}>{t('footer.business')}</Link></li>
            <li><Link to={lp('/faq')}>{t('footer.faq')}</Link></li>
            <li><Link to={lp('/terms')}>{t('footer.terms')}</Link></li>
            <li><Link to={lp('/privacy')}>Политика конфиденциальности</Link></li>
          </ul>
        </div>

        {/* Contacts + App */}
        <div className="footer-column">
          <h3>{t('footer.contacts')}</h3>
          <ul>
            <li>
              <a href="mailto:info@topdim.uz" style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'inherit' }}>
                <Mail size={15} /> info@topdim.uz
              </a>
            </li>
            <li>
              <a href="tel:+998712000000" style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'inherit' }}>
                <Phone size={15} /> +998 71 200 00 00
              </a>
            </li>
            <li><MapPin size={15} /> г. Ташкент</li>
          </ul>
          <div className="footer-apps">
            <h4>Мобильное приложение</h4>
            <div className="footer-app-badges">
              <button className="footer-app-badge footer-app-badge--disabled" disabled>
                <Smartphone size={18} />
                <div>
                  <span className="footer-app-badge__label">Скоро в</span>
                  <span className="footer-app-badge__store">App Store</span>
                </div>
              </button>
              <button className="footer-app-badge footer-app-badge--disabled" disabled>
                <Smartphone size={18} />
                <div>
                  <span className="footer-app-badge__label">Скоро в</span>
                  <span className="footer-app-badge__store">Google Play</span>
                </div>
              </button>
            </div>
          </div>
        </div>
      </div>

      <div className="footer-bottom">
        <div className="container">
          <p>&copy; {new Date().getFullYear()} {t('footer.rights')}</p>
        </div>
      </div>
    </footer>
  );
}
