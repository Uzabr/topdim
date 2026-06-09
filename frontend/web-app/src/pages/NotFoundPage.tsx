import { Link } from 'react-router-dom';
import { Home } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useLocalePath } from '../hooks/useLocalePath';
import './NotFoundPage.css';

export default function NotFoundPage() {
  const { t } = useTranslation();
  const lp = useLocalePath();
  return (
    <div className="not-found-page container">
      <div className="not-found-content surface-card">
        <div className="error-code text-gradient">404</div>
        <h1 className="page-title">{t('notFound.title')}</h1>
        <p className="section-copy">{t('notFound.desc')}</p>
        <Link to={lp('/')} className="primary-button">
          {t('notFound.home')}
          <Home size={18} />
        </Link>
      </div>
    </div>
  );
}
