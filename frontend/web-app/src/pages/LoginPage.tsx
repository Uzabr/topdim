import { useLocation, useNavigate } from 'react-router-dom';
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
  const location = useLocation();
  const lp = useLocalePath();
  // Куда вернуть пользователя после входа: страница-инициатор редиректа
  // (например, гость-гейт checkout) или, по умолчанию, главная.
  const from = (location.state as { from?: string } | null)?.from ?? lp('/');

  return (
    <div className="login-page">
      <Logo size="md" />
      <LoginCard onSuccess={() => navigate(from)} />
    </div>
  );
}
