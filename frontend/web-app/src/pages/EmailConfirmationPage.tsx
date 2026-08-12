import { useCallback, useEffect, useRef, useState } from 'react';
import { CheckCircle2, MailCheck } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { authApi } from '../api/auth';
import Logo from '../components/layout/Logo';
import { useLocalePath } from '../hooks/useLocalePath';
import {
  captureSessionGeneration,
  isSessionGenerationCurrent,
} from '../sessionCleanup';
import { useAuthStore } from '../store/authStore';
import './EmailConfirmationPage.css';

type ConfirmationStatus = 'idle' | 'confirming' | 'success' | 'error';

export default function EmailConfirmationPage() {
  const { t } = useTranslation();
  const lp = useLocalePath();
  const [searchParams] = useSearchParams();
  const queryToken = searchParams.get('token')?.trim() ?? '';
  const [status, setStatus] = useState<ConfirmationStatus>('idle');
  const [message, setMessage] = useState('');
  const processedToken = useRef('');
  const { isAuthenticated, refreshProfile } = useAuthStore();

  const confirm = useCallback(
    async (confirmationToken: string) => {
      const normalizedToken = confirmationToken.trim();
      if (!normalizedToken) {
        setStatus('error');
        setMessage(t('profile.emailConfirmation.missingToken'));
        return;
      }

      const sessionGeneration = captureSessionGeneration();
      setStatus('confirming');
      setMessage('');
      try {
        await authApi.confirmEmail(normalizedToken);
        if (!isSessionGenerationCurrent(sessionGeneration)) return;
        if (isAuthenticated) {
          await refreshProfile();
          if (!isSessionGenerationCurrent(sessionGeneration)) return;
        }
        setStatus('success');
        setMessage(t('profile.emailConfirmation.success'));
      } catch (err: unknown) {
        if (!isSessionGenerationCurrent(sessionGeneration)) return;
        const e = err as { response?: { data?: { message?: string } } };
        setStatus('error');
        setMessage(e.response?.data?.message || t('profile.emailConfirmation.error'));
      }
    },
    [isAuthenticated, refreshProfile, t],
  );

  useEffect(() => {
    if (!queryToken || processedToken.current === queryToken) return;
    const timer = window.setTimeout(() => {
      if (processedToken.current === queryToken) return;
      processedToken.current = queryToken;
      void confirm(queryToken);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [confirm, queryToken]);

  if (!queryToken) {
    return (
      <div className="email-confirm-page">
        <Logo size="md" />
        <section className="email-confirm-card">
          <MailCheck className="email-confirm-card__icon" size={44} />
          <h1>{t('profile.emailConfirmation.title')}</h1>
          <p className="email-confirm-card__message email-confirm-card__message--error" role="alert">
            {t('profile.emailConfirmation.missingToken')}
          </p>
          <Link className="email-confirm-card__link" to={lp(isAuthenticated ? '/profile?tab=settings' : '/login')}>
            {isAuthenticated
              ? t('profile.emailConfirmation.toProfile')
              : t('profile.emailConfirmation.toLogin')}
          </Link>
        </section>
      </div>
    );
  }

  return (
    <div className="email-confirm-page">
      <Logo size="md" />
      <section className="email-confirm-card">
        {status === 'success' ? (
          <CheckCircle2 className="email-confirm-card__icon email-confirm-card__icon--success" size={44} />
        ) : (
          <MailCheck className="email-confirm-card__icon" size={44} />
        )}
        <h1>{t('profile.emailConfirmation.title')}</h1>

        {status !== 'success' && status !== 'error' && (
          <p className="email-confirm-card__hint">{t('profile.emailConfirmation.confirming')}</p>
        )}

        {message && (
          <p
            className={`email-confirm-card__message email-confirm-card__message--${status}`}
            role={status === 'error' ? 'alert' : 'status'}
          >
            {message}
          </p>
        )}

        {status === 'success' && (
          <Link className="email-confirm-card__link" to={lp(isAuthenticated ? '/profile?tab=settings' : '/login')}>
            {isAuthenticated
              ? t('profile.emailConfirmation.toProfile')
              : t('profile.emailConfirmation.toLogin')}
          </Link>
        )}
      </section>
    </div>
  );
}
