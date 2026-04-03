import { Link } from 'react-router-dom';
import { Home } from 'lucide-react';
import './NotFoundPage.css';

export default function NotFoundPage() {
  return (
    <div className="not-found-page container">
      <div className="not-found-content surface-card">
        <div className="error-code text-gradient">404</div>
        <h1 className="page-title">Упс! Страница не найдена</h1>
        <p className="section-copy">
          Кажется, вы перешли по неверной ссылке или эта страница больше не существует.
        </p>
        <Link to="/" className="primary-button">
          На главную
          <Home size={18} />
        </Link>
      </div>
    </div>
  );
}
