import { useNavigate } from 'react-router-dom';
import LoginCard from '../components/auth/LoginCard';
import Logo from '../components/layout/Logo';
import { useLocalePath } from '../hooks/useLocalePath';
import './LoginPage.css';

/**
 * Отдельная страница входа нужна для прямых ссылок и редиректов с защищённых
 * маршрутов. Из шапки вход открывается модалкой — карточка та же.
 */
export default function LoginPage() {
  const navigate = useNavigate();
  const lp = useLocalePath();

  return (
    <div className="login-page">
      <Logo size="md" />
      <LoginCard onSuccess={() => navigate(lp('/'))} />
    </div>
  );
}
