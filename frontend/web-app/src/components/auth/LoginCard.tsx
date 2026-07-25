import { useMemo, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { IMaskInput } from 'react-imask';
import { useTranslation } from 'react-i18next';
import { authApi } from '../../api/auth';
import type { TelegramAuthPayload } from '../../api/auth';
import { useAuthStore } from '../../store/authStore';
import { STRONG_PASSWORD_PATTERN } from '../../utils/password';
import TelegramLoginButton from './TelegramLoginButton';
import './LoginCard.css';

/** Username бота покупателей (@BotFather). Публичное значение. */
const TELEGRAM_BOT_USERNAME = 'sizbiz_uz_bot';

type Mode = 'login' | 'register' | 'resetRequest' | 'resetConfirm';

interface LoginCardProps {
  /** Вызывается после успешного входа/регистрации. */
  onSuccess: () => void;
}

/** Сообщение об ошибке от бэкенда: поля лежат в data, общий текст — в message. */
function serverMessage(err: unknown, fallback: string): string {
  const e = err as { response?: { data?: { message?: string; data?: Record<string, string> } } };
  const fields = e.response?.data?.data;
  return (fields && Object.values(fields)[0]) || e.response?.data?.message || fallback;
}

/**
 * «Вход за один тап» — референс: design_handoff_sizbiz/«Главная - образец.dc.html».
 *
 * Telegram, «по номеру телефона» и Google в бэкенде отсутствуют (или небезопасны:
 * /auth/guest выдаёт токен по одному номеру, без SMS-кода), поэтому они помечены
 * как «скоро» и не отправляют запросов. Рабочий путь — почта и пароль.
 */
export default function LoginCard({ onSuccess }: LoginCardProps) {
  const { t } = useTranslation();
  const { login, telegramLogin, register: registerUser, isLoading } = useAuthStore();

  const [mode, setMode] = useState<Mode>('login');
  const [emailOpen, setEmailOpen] = useState(false);
  const [serverError, setServerError] = useState('');
  const [notice, setNotice] = useState('');
  const [soon, setSoon] = useState('');

  const [resetEmail, setResetEmail] = useState('');
  const [resetToken, setResetToken] = useState('');
  const [resetPassword, setResetPassword] = useState('');
  const [resetBusy, setResetBusy] = useState(false);

  const loginSchema = useMemo(
    () =>
      z.object({
        email: z
          .string()
          .min(1, t('login.validation.emailRequired'))
          .email(t('login.validation.emailInvalid')),
        password: z.string().min(1, t('login.validation.passwordRequired')),
      }),
    [t],
  );

  const registerSchema = useMemo(
    () =>
      loginSchema.extend({
        firstName: z.string().min(2, t('login.validation.firstNameMin')),
        phone: z
          .string()
          .optional()
          .transform((v) => (v === '' ? undefined : v))
          .refine((v) => !v || /^\+998\d{9}$/.test(v), {
            message: t('login.validation.phoneFormat'),
          }),
        // В синхроне с backend @StrongPassword: 8–128, строчная + заглавная + цифра
        // + спецсимвол из @$!%*?&#^()-_=+. Блок-лист частых паролей — только на сервере.
        password: z
          .string()
          .min(8, t('login.validation.passwordMin'))
          .regex(
            STRONG_PASSWORD_PATTERN,
            t('login.validation.passwordWeak'),
          ),
      }),
    [loginSchema, t],
  );

  type FormData = { email: string; password: string; firstName?: string; phone?: string };

  const isRegister = mode === 'register';

  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors },
  } = useForm<FormData>({
    resolver: zodResolver(isRegister ? registerSchema : loginSchema),
    mode: 'onBlur',
  });

  const submit = async (data: FormData) => {
    setServerError('');
    try {
      let authenticated: boolean;
      if (isRegister) {
        authenticated = await registerUser({
          email: data.email,
          password: data.password,
          firstName: data.firstName ?? '',
          phone: data.phone,
        });
      } else {
        authenticated = await login({
          email: data.email,
          password: data.password,
        });
      }
      if (authenticated) onSuccess();
    } catch (err) {
      setServerError(serverMessage(err, t('login.serverError')));
    }
  };

  const switchMode = (next: Mode) => {
    setMode(next);
    setServerError('');
    setNotice('');
    reset();
    if (next === 'login' || next === 'register') setEmailOpen(true);
  };

  const showSoon = (provider: string) => {
    setSoon(t('login.soon', { provider }));
    setServerError('');
  };

  const handleTelegramAuth = async (user: TelegramAuthPayload) => {
    setServerError('');
    setSoon('');
    try {
      const authenticated = await telegramLogin(user);
      if (authenticated) onSuccess();
    } catch (err) {
      setServerError(serverMessage(err, t('login.serverError')));
    }
  };

  const requestReset = async () => {
    setServerError('');
    setResetBusy(true);
    try {
      await authApi.requestPasswordReset(resetEmail.trim());
      setNotice(t('login.resetSent'));
      setMode('resetConfirm');
    } catch (err) {
      setServerError(serverMessage(err, t('login.serverError')));
    } finally {
      setResetBusy(false);
    }
  };

  const confirmReset = async () => {
    setServerError('');
    setResetBusy(true);
    try {
      await authApi.confirmPasswordReset({
        token: resetToken.trim(),
        newPassword: resetPassword,
        confirmPassword: resetPassword,
      });
      setNotice(t('login.resetDone'));
      setMode('login');
      setEmailOpen(true);
    } catch (err) {
      setServerError(serverMessage(err, t('login.serverError')));
    } finally {
      setResetBusy(false);
    }
  };

  // ── Сброс пароля: отдельные экраны той же карточки ──
  if (mode === 'resetRequest' || mode === 'resetConfirm') {
    const request = mode === 'resetRequest';
    return (
      <div className="lcard">
        <h2 className="lcard__title">{t('login.resetTitle')}</h2>
        <p className="lcard__subtitle">
          {request ? t('login.resetHint') : t('login.resetConfirmHint')}
        </p>

        <div className="lcard__form">
          {request ? (
            <input
              className="lcard__input"
              type="email"
              autoFocus
              placeholder={t('login.email')}
              value={resetEmail}
              onChange={(e) => setResetEmail(e.target.value)}
            />
          ) : (
            <>
              <input
                className="lcard__input"
                autoFocus
                placeholder={t('login.resetToken')}
                value={resetToken}
                onChange={(e) => setResetToken(e.target.value)}
              />
              <input
                className="lcard__input"
                type="password"
                placeholder={t('login.newPassword')}
                value={resetPassword}
                onChange={(e) => setResetPassword(e.target.value)}
              />
              <p className="lcard__hint">{t('login.passwordHint')}</p>
            </>
          )}

          {notice && <p className="lcard__notice">{notice}</p>}
          {serverError && <p className="lcard__error">{serverError}</p>}

          <button
            type="button"
            className="lcard__submit"
            disabled={resetBusy || (request ? !resetEmail.trim() : !resetToken.trim() || !resetPassword)}
            onClick={request ? requestReset : confirmReset}
          >
            {resetBusy
              ? t('common.loading')
              : request
                ? t('login.resetSend')
                : t('login.resetSave')}
          </button>

          <button type="button" className="lcard__link" onClick={() => switchMode('login')}>
            {t('common.back')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="lcard">
      <h2 className="lcard__title">{t('login.oneTapTitle')}</h2>
      <p className="lcard__subtitle">{t('login.oneTapSubtitle')}</p>

      {/* Telegram Login Widget — реальный вход/регистрация через Telegram */}
      <div className="lcard__telegram-widget">
        <TelegramLoginButton botUsername={TELEGRAM_BOT_USERNAME} onAuth={handleTelegramAuth} />
      </div>

      {/* Вход по номеру: на бэкенде есть /auth/guest, но он выдаёт токен без SMS-кода —
          подключать нельзя, иначе чужой номер = чужие купоны. */}
      <button
        type="button"
        className="lcard__outline"
        onClick={() => showSoon(t('login.viaPhone'))}
        aria-disabled="true"
      >
        {t('login.viaPhone')}
        <span className="lcard__soon-tag">{t('common.soon')}</span>
      </button>

      <div className="lcard__divider">
        <span>{t('login.or')}</span>
      </div>

      <div className="lcard__providers">
        <button
          type="button"
          className="lcard__provider"
          onClick={() => showSoon('Google')}
          aria-disabled="true"
        >
          <span className="lcard__g-mark">G</span>
          Google
          <span className="lcard__soon-tag">{t('common.soon')}</span>
        </button>

        <button
          type="button"
          className={`lcard__provider${emailOpen ? ' lcard__provider--active' : ''}`}
          onClick={() => {
            setEmailOpen((v) => !v);
            setSoon('');
          }}
          aria-expanded={emailOpen}
        >
          {t('login.viaEmail')}
        </button>
      </div>

      {soon && <p className="lcard__notice">{soon}</p>}

      {emailOpen && (
        <form className="lcard__form" onSubmit={handleSubmit(submit)}>
          {isRegister && (
            <>
              <input
                className="lcard__input"
                placeholder={t('login.firstName')}
                {...register('firstName')}
              />
              {errors.firstName && (
                <span className="lcard__error">{errors.firstName.message?.toString()}</span>
              )}
            </>
          )}

          <input
            className="lcard__input"
            type="email"
            placeholder={t('login.email')}
            {...register('email')}
          />
          {errors.email && <span className="lcard__error">{errors.email.message?.toString()}</span>}

          {isRegister && (
            <>
              <Controller
                name="phone"
                control={control}
                render={({ field: { onChange, onBlur, value, ref } }) => (
                  <IMaskInput
                    className="lcard__input"
                    mask="+{998} 00 000-00-00"
                    placeholder={t('login.phonePlaceholder')}
                    value={value || ''}
                    onAccept={(val) => onChange(val.replace(/\s|-/g, ''))}
                    onBlur={onBlur}
                    inputRef={ref}
                  />
                )}
              />
              {errors.phone && (
                <span className="lcard__error">{errors.phone.message?.toString()}</span>
              )}
            </>
          )}

          <input
            className="lcard__input"
            type="password"
            placeholder={t('login.password')}
            {...register('password')}
          />
          {errors.password && (
            <span className="lcard__error">{errors.password.message?.toString()}</span>
          )}
          {isRegister && !errors.password && (
            <p className="lcard__hint">{t('login.passwordHint')}</p>
          )}

          {notice && <p className="lcard__notice">{notice}</p>}
          {serverError && <p className="lcard__error">{serverError}</p>}

          <button type="submit" className="lcard__submit" disabled={isLoading}>
            {isLoading
              ? t('common.loading')
              : isRegister
                ? t('login.submitRegister')
                : t('login.submitLogin')}
          </button>

          <div className="lcard__row">
            {isRegister ? (
              <button type="button" className="lcard__link" onClick={() => switchMode('login')}>
                {t('login.switchLogin')}
              </button>
            ) : (
              <button
                type="button"
                className="lcard__link"
                onClick={() => switchMode('resetRequest')}
              >
                {t('login.forgot')}
              </button>
            )}

            {!isRegister && (
              <button
                type="button"
                className="lcard__link lcard__link--strong"
                onClick={() => switchMode('register')}
              >
                {t('login.switchRegister')}
              </button>
            )}
          </div>
        </form>
      )}

      <p className="lcard__terms">{t('login.terms')}</p>
    </div>
  );
}
