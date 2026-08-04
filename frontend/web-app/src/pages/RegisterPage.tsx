import { useNavigate } from 'react-router-dom';
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
  const lp = useLocalePath();

  return (
    <div className="login-page">
      <Logo size="md" />
      <LoginCard onSuccess={() => navigate(lp('/'))} initialMode="register" />
    </div>
  );
}
