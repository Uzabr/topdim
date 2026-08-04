import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { CheckCircle2, KeyRound } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { authApi } from '../api/auth';
import Logo from '../components/layout/Logo';
import { useLocalePath } from '../hooks/useLocalePath';
import { isStrongPassword } from '../utils/password';
import './ResetPasswordPage.css';

type ResetStatus = 'idle' | 'submitting' | 'success' | 'error';

/**
 * Deep-link /:lang/reset-password?token=... (T9) — переход по ссылке из письма
 * сброса пароля. Токен читается из URL (по образцу EmailConfirmationPage),
 * пользователь вводит новый пароль и подтверждение.
 */
export default function ResetPasswordPage() {
  const { t } = useTranslation();
  const lp = useLocalePath();
  const [searchParams] = useSearchParams();
  const token = useMemo(() => searchParams.get('token')?.trim() ?? '', [searchParams]);

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [status, setStatus] = useState<ResetStatus>('idle');
  const [message, setMessage] = useState('');
  const [fieldError, setFieldError] = useState('');

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setFieldError('');
    setMessage('');

    if (!isStrongPassword(newPassword)) {
      setFieldError(t('resetPasswordPage.weak'));
      return;
    }
    if (newPassword !== confirmPassword) {
      setFieldError(t('resetPasswordPage.mismatch'));
      return;
    }

    setStatus('submitting');
    try {
      await authApi.confirmPasswordReset({ token, newPassword, confirmPassword });
      setStatus('success');
      setMessage(t('resetPasswordPage.success'));
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      setStatus('error');
      setMessage(e.response?.data?.message || t('resetPasswordPage.invalidToken'));
    }
  };

  if (!token) {
    return (
      <div className="reset-password-page">
        <Logo size="md" />
        <section className="reset-password-card">
          <KeyRound className="reset-password-card__icon" size={44} />
          <h1>{t('resetPasswordPage.title')}</h1>
          <p className="reset-password-card__message reset-password-card__message--error" role="alert">
            {t('resetPasswordPage.missingToken')}
          </p>
          <Link className="reset-password-card__link" to={lp('/login')}>
            {t('resetPasswordPage.toLogin')}
          </Link>
        </section>
      </div>
    );
  }

  return (
    <div className="reset-password-page">
      <Logo size="md" />
      <section className="reset-password-card">
        {status === 'success' ? (
          <CheckCircle2 className="reset-password-card__icon reset-password-card__icon--success" size={44} />
        ) : (
          <KeyRound className="reset-password-card__icon" size={44} />
        )}
        <h1>{t('resetPasswordPage.title')}</h1>
        <p className="reset-password-card__hint">{t('resetPasswordPage.hint')}</p>

        {status !== 'success' && (
          <form className="reset-password-card__form" onSubmit={submit}>
            <label>
              <span>{t('resetPasswordPage.newPassword')}</span>
              <input
                type="password"
                autoComplete="new-password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                disabled={status === 'submitting'}
                autoFocus
              />
            </label>
            <label>
              <span>{t('resetPasswordPage.confirmPassword')}</span>
              <input
                type="password"
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                disabled={status === 'submitting'}
              />
            </label>
            <p className="reset-password-card__hint">{t('resetPasswordPage.passwordHint')}</p>

            {fieldError && (
              <p className="reset-password-card__message reset-password-card__message--error" role="alert">
                {fieldError}
              </p>
            )}

            <button type="submit" disabled={status === 'submitting'}>
              {status === 'submitting'
                ? t('resetPasswordPage.submitting')
                : t('resetPasswordPage.submit')}
            </button>
          </form>
        )}

        {message && (
          <p
            className={`reset-password-card__message reset-password-card__message--${status}`}
            role={status === 'error' ? 'alert' : 'status'}
          >
            {message}
          </p>
        )}

        {status === 'success' && (
          <Link className="reset-password-card__link" to={lp('/login')}>
            {t('resetPasswordPage.toLogin')}
          </Link>
        )}
      </section>
    </div>
  );
}
