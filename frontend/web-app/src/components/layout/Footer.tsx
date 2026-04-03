import { Instagram, TextIcon as Telegram, Mail, MapPin } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import './Footer.css';

export default function Footer() {
  const { t } = useTranslation();
  return (
    <footer className="app-footer">
      <div className="container footer-content">
        <div className="footer-brand">
          <h2 className="footer-logo text-gradient">TopDim</h2>
          <p className="footer-desc">
            {t('footer.desc')}
          </p>
          <div className="footer-socials">
            <a href="#" aria-label="Instagram"><Instagram size={20} /></a>
            <a href="#" aria-label="Telegram"><Telegram size={20} /></a>
          </div>
        </div>
        
        <div className="footer-links">
          <div className="footer-column">
            <h3>{t('footer.contacts')}</h3>
            <ul>
              <li><Mail size={16} /> info@topdim.uz</li>
              <li><MapPin size={16} /> г. Ташкент</li>
            </ul>
          </div>
          <div className="footer-column">
            <h3>{t('footer.info')}</h3>
            <ul>
              <li><a href="#">{t('footer.faq')}</a></li>
              <li><a href="#">{t('footer.terms')}</a></li>
              <li><a href="#">{t('footer.business')}</a></li>
            </ul>
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
