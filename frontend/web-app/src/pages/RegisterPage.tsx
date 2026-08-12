import { useLocation, useNavigate } from 'react-router-dom';
import LoginCard from '../components/auth/LoginCard';
import Logo from '../components/layout/Logo';
import { useLocalePath } from '../hooks/useLocalePath';
import './LoginPage.css';

/**
 * Deep-link /:lang/register (T9) — та же карточка входа, что и на /login,
 * но сразу открытая в режиме регистрации (initialMode='register').
 * Отдельная страница нужна для прямых ссылок (например, из рекламы/письма).
 */
export default function RegisterPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const lp = useLocalePath();
  // Куда вернуть пользователя после регистрации: страница-инициатор редиректа
  // или, по умолчанию, главная.
  const from = (location.state as { from?: string } | null)?.from ?? lp('/');

  return (
    <div className="login-page">
      <Logo size="md" />
      <LoginCard onSuccess={() => navigate(from)} initialMode="register" />
    </div>
  );
}
