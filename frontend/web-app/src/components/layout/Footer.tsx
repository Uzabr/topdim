import { Mail, MapPin, Phone, Smartphone } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useLocalePath } from '../../hooks/useLocalePath';
import { BAZAAR_NAV_ENABLED } from '../../config/features';
import Logo from './Logo';
import './Footer.css';

export default function Footer() {
  const { t } = useTranslation();
  const lp = useLocalePath();
  return (
    <footer className="app-footer">
      <div className="container">
      <div className="footer-content">
        {/* Brand */}
        <div className="footer-column footer-brand">
          <Logo size="md" className="footer-logo" />
          <p className="footer-desc">{t('footer.desc')}</p>
        </div>

        {/* User links */}
        <div className="footer-column">
          <h3>{t('footer.usersTitle')}</h3>
          <ul>
            <li><Link to={lp('/coupons')}>{t('footer.catalog')}</Link></li>
            <li><Link to={lp('/favorites')}>{t('footer.favorites')}</Link></li>
            {BAZAAR_NAV_ENABLED && (
              <li><Link to={lp('/bazaar')}>{t('footer.bazaar')}</Link></li>
            )}
            <li><Link to={lp('/profile')}>{t('footer.myCoupons')}</Link></li>
          </ul>
        </div>

        {/* Partners */}
        <div className="footer-column">
          <h3>{t('footer.partnersTitle')}</h3>
          <ul>
            <li><Link to={lp('/partners')}>{t('footer.business')}</Link></li>
            <li><Link to={lp('/faq')}>{t('footer.faq')}</Link></li>
            <li><Link to={lp('/terms')}>{t('footer.terms')}</Link></li>
            <li><Link to={lp('/privacy')}>{t('footer.privacy')}</Link></li>
          </ul>
        </div>

        {/* Contacts + App */}
        <div className="footer-column">
          <h3>{t('footer.contacts')}</h3>
          <ul>
            <li>
              <a href="mailto:info@sizbiz.uz" style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'inherit' }}>
                <Mail size={15} /> info@sizbiz.uz
              </a>
            </li>
            <li>
              <a href="tel:+998507256066" style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'inherit' }}>
                <Phone size={15} /> +998 50 725 60 66
              </a>
            </li>
            <li><MapPin size={15} /> {t('common.city')}</li>
          </ul>
          <div className="footer-apps">
            <h4>{t('footer.mobileApp')}</h4>
            <div className="footer-app-badges">
              <button className="footer-app-badge footer-app-badge--disabled" disabled>
                <Smartphone size={18} />
                <div>
                  <span className="footer-app-badge__label">{t('common.soonIn')}</span>
                  <span className="footer-app-badge__store">App Store</span>
                </div>
              </button>
              <button className="footer-app-badge footer-app-badge--disabled" disabled>
                <Smartphone size={18} />
                <div>
                  <span className="footer-app-badge__label">{t('common.soonIn')}</span>
                  <span className="footer-app-badge__store">Google Play</span>
                </div>
              </button>
            </div>
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
