import { useState, type ReactNode } from 'react';
import LoginModal from './LoginModal';
import './GuestAuthPrompt.css';

interface GuestAuthPromptProps {
  icon: ReactNode;
  title: string;
  description: string;
  loginLabel: string;
  /** desktop — как empty на десктопе; mobile — как empty в мобильных экранах. */
  variant?: 'desktop' | 'mobile';
}

/**
 * Экран для гостя на корзине / избранном / профиле:
 * остаёмся на странице и предлагаем войти модалкой (без редиректа на /login).
 * Стили совпадают с empty-state соответствующих страниц.
 */
export default function GuestAuthPrompt({
  icon,
  title,
  description,
  loginLabel,
  variant = 'desktop',
}: GuestAuthPromptProps) {
  const [loginOpen, setLoginOpen] = useState(false);

  return (
    <>
      <div className={`guest-auth guest-auth--${variant}`}>
        <span className="guest-auth__icon">{icon}</span>
        <h1 className="guest-auth__title">{title}</h1>
        <p className="guest-auth__text">{description}</p>
        <button
          type="button"
          className={variant === 'desktop' ? 'primary-button' : 'guest-auth__btn'}
          onClick={() => setLoginOpen(true)}
        >
          {loginLabel}
        </button>
      </div>

      {loginOpen && <LoginModal onClose={() => setLoginOpen(false)} />}
    </>
  );
}
