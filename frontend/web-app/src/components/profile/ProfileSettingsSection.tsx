import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import { switchLanguage } from '../../i18n';
import { IMaskInput } from 'react-imask';
import { authApi } from '../../api/auth';
import { mediaApi } from '../../api/media';
import {
  captureSessionGeneration,
  isSessionGenerationCurrent,
} from '../../sessionCleanup';
import { useAuthStore } from '../../store/authStore';
import { validateAvatarFile } from '../../utils/avatar';
import { isStrongPassword } from '../../utils/password';
import UserAvatar from '../ui/UserAvatar';
import NotificationsSection from './NotificationsSection';
import './ProfileSettingsSection.css';

type EditingField = 'name' | 'phone' | 'password' | 'email' | null;

/** Простая RFC-достаточная проверка формата — строгая валидация всё равно на бэкенде. */
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Тот же формат, что и в LoginCard (T4): маска "+{998} 00 000-00-00" → +998XXXXXXXXX. */
const PHONE_PATTERN = /^\+998\d{9}$/;

interface PasswordErrors {
  currentPassword?: string;
  newPassword?: string;
  confirmPassword?: string;
  general?: string;
}

/** Настройки — строки-карточки (design_handoff_sizbiz → «Профиль», таб «Настройки»). */
export default function ProfileSettingsSection() {
  const { t, i18n } = useTranslation();
  const { user, updateProfile, logout, refreshProfile } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();

  const [editing, setEditing] = useState<EditingField>(null);
  const [firstName, setFirstName] = useState(user?.firstName ?? '');
  const [lastName, setLastName] = useState(user?.lastName ?? '');
  const [phone, setPhone] = useState(user?.phone ?? '');
  const [phoneCode, setPhoneCode] = useState('');
  const [phoneOtpStep, setPhoneOtpStep] = useState<'phone' | 'code'>('phone');
  const [phoneBusy, setPhoneBusy] = useState(false);
  const [phoneOtpError, setPhoneOtpError] = useState('');
  const [phoneNotice, setPhoneNotice] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordErrors, setPasswordErrors] = useState<PasswordErrors>({});
  const [passwordNotice, setPasswordNotice] = useState('');
  const [avatarError, setAvatarError] = useState('');
  const [avatarNotice, setAvatarNotice] = useState('');
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [emailConfirming, setEmailConfirming] = useState(false);
  const [emailNotice, setEmailNotice] = useState('');
  const [emailError, setEmailError] = useState('');
  const [newEmail, setNewEmail] = useState('');
  const [emailChangeSubmitting, setEmailChangeSubmitting] = useState(false);
  const [emailChangeNotice, setEmailChangeNotice] = useState('');
  const [emailChangeError, setEmailChangeError] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  const lang = i18n.language?.substring(0, 2) === 'uz' ? 'uz' : 'ru';
  const fullName = [user?.firstName, user?.lastName].filter(Boolean).join(' ');

  const uploadAvatar = async (file?: File) => {
    if (!file) return;

    setAvatarError('');
    setAvatarNotice('');
    const validationError = validateAvatarFile(file);
    if (validationError) {
      setAvatarError(t(`profile.settings.avatar.${validationError}Error`));
      return;
    }

    const sessionGeneration = captureSessionGeneration();
    setAvatarUploading(true);
    try {
      const uploaded = await mediaApi.uploadFile(file);
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      await updateProfile({ avatarUrl: uploaded.url });
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      setAvatarNotice(t('profile.settings.avatar.success'));
    } catch (err: unknown) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const e = err as { response?: { data?: { message?: string } } };
      setAvatarError(e.response?.data?.message || t('profile.settings.avatar.error'));
    } finally {
      if (isSessionGenerationCurrent(sessionGeneration)) {
        setAvatarUploading(false);
      }
    }
  };

  const requestEmailConfirmation = async () => {
    const sessionGeneration = captureSessionGeneration();
    setEmailConfirming(true);
    setEmailNotice('');
    setEmailError('');
    try {
      await authApi.requestEmailConfirm();
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      setEmailNotice(t('profile.settings.email.sent'));
    } catch (err: unknown) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const e = err as { response?: { data?: { message?: string } } };
      setEmailError(e.response?.data?.message || t('profile.settings.email.error'));
    } finally {
      if (isSessionGenerationCurrent(sessionGeneration)) {
        setEmailConfirming(false);
      }
    }
  };

  const openEmailChange = () => {
    setEmailChangeNotice('');
    setEmailChangeError('');
    setNewEmail('');
    setEditing('email');
  };

  const cancelEmailChange = () => {
    setNewEmail('');
    setEmailChangeError('');
    setEditing(null);
  };

  const submitEmailChange = async () => {
    setEmailChangeError('');
    const trimmed = newEmail.trim();
    if (!EMAIL_PATTERN.test(trimmed)) {
      setEmailChangeError(t('profile.settings.email.changeInvalid'));
      return;
    }

    const sessionGeneration = captureSessionGeneration();
    setEmailChangeSubmitting(true);
    setEmailChangeNotice('');
    try {
      await authApi.requestEmailChange(trimmed);
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      setEmailChangeNotice(t('profile.settings.email.changeSent'));
      setNewEmail('');
      setEditing(null);
    } catch (err: unknown) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const e = err as { response?: { status?: number; data?: { message?: string } } };
      const fallback =
        e.response?.status === 409
          ? t('profile.settings.email.changeTaken')
          : t('profile.settings.email.changeError');
      setEmailChangeError(e.response?.data?.message || fallback);
    } finally {
      if (isSessionGenerationCurrent(sessionGeneration)) {
        setEmailChangeSubmitting(false);
      }
    }
  };

  const saveName = async () => {
    setError('');

    if (!firstName.trim()) {
      setError(t('profile.settings.validation.firstNameRequired'));
      return;
    }

    const sessionGeneration = captureSessionGeneration();
    setSaving(true);
    try {
      await updateProfile({ firstName: firstName.trim(), lastName: lastName.trim() });
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      setEditing(null);
    } catch (err: unknown) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const e = err as { response?: { data?: { message?: string } } };
      setError(e.response?.data?.message || t('profile.settings.error'));
    } finally {
      if (isSessionGenerationCurrent(sessionGeneration)) {
        setSaving(false);
      }
    }
  };

  const cancel = () => {
    setFirstName(user?.firstName ?? '');
    setLastName(user?.lastName ?? '');
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setPasswordErrors({});
    setError('');
    setEditing(null);
  };

  const openPhoneLink = () => {
    setPhone(user?.phone ?? '');
    setPhoneCode('');
    setPhoneOtpStep('phone');
    setPhoneOtpError('');
    setPhoneNotice('');
    setEditing('phone');
  };

  const cancelPhoneLink = () => {
    setPhone(user?.phone ?? '');
    setPhoneCode('');
    setPhoneOtpStep('phone');
    setPhoneOtpError('');
    setEditing(null);
  };

  const requestPhoneLinkOtp = async () => {
    setPhoneOtpError('');
    const trimmed = phone.trim();
    if (!PHONE_PATTERN.test(trimmed)) {
      setPhoneOtpError(t('profile.settings.phoneOtp.invalid'));
      return;
    }

    const sessionGeneration = captureSessionGeneration();
    setPhoneBusy(true);
    setPhoneNotice('');
    try {
      await authApi.requestPhoneOtp(trimmed);
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      setPhoneNotice(t('profile.settings.phoneOtp.codeSent'));
      setPhoneOtpStep('code');
    } catch (err: unknown) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const e = err as { response?: { data?: { message?: string } } };
      setPhoneOtpError(e.response?.data?.message || t('profile.settings.phoneOtp.requestError'));
    } finally {
      if (isSessionGenerationCurrent(sessionGeneration)) {
        setPhoneBusy(false);
      }
    }
  };

  /**
   * Успешный /auth/phone/link отвечает 200 без тела — обязательно перечитываем
   * профиль (refreshProfile), иначе UI покажет устаревшие phone/phoneVerified.
   */
  const submitPhoneLink = async () => {
    setPhoneOtpError('');
    const sessionGeneration = captureSessionGeneration();
    setPhoneBusy(true);
    try {
      await authApi.linkPhone({ phone: phone.trim(), code: phoneCode.trim() });
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      await refreshProfile();
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      setPhoneCode('');
      setPhoneOtpStep('phone');
      setEditing(null);
      setPhoneNotice(t('profile.settings.phoneOtp.success'));
    } catch (err: unknown) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const e = err as { response?: { status?: number; data?: { message?: string } } };
      const fallback =
        e.response?.status === 409
          ? t('profile.settings.phoneOtp.taken')
          : e.response?.status === 401
            ? t('profile.settings.phoneOtp.invalidCode')
            : t('profile.settings.phoneOtp.error');
      setPhoneOtpError(e.response?.data?.message || fallback);
    } finally {
      if (isSessionGenerationCurrent(sessionGeneration)) {
        setPhoneBusy(false);
      }
    }
  };

  const savePassword = async () => {
    const errors: PasswordErrors = {};
    if (!currentPassword) {
      errors.currentPassword = t('profile.settings.password.currentRequired');
    }
    if (!isStrongPassword(newPassword)) {
      errors.newPassword = t('profile.settings.password.weak');
    }
    if (confirmPassword !== newPassword) {
      errors.confirmPassword = t('profile.settings.password.mismatch');
    }
    if (Object.keys(errors).length > 0) {
      setPasswordErrors(errors);
      return;
    }

    const sessionGeneration = captureSessionGeneration();
    setSaving(true);
    setPasswordErrors({});
    setPasswordNotice('');
    try {
      await authApi.changePassword(currentPassword, newPassword);
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setEditing(null);
      setPasswordNotice(t('profile.settings.password.success'));
      logout();
      navigate(`/${lang}/login`, { replace: true });
    } catch (err: unknown) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const e = err as { response?: { status?: number; data?: { message?: string; data?: Record<string, string> } } };
      const fields = e.response?.data?.data;
      const message =
        (fields && Object.values(fields)[0]) ||
        e.response?.data?.message ||
        t('profile.settings.password.error');
      setPasswordErrors(
        e.response?.status === 401 ? { currentPassword: message } : { general: message },
      );
    } finally {
      if (isSessionGenerationCurrent(sessionGeneration)) {
        setSaving(false);
      }
    }
  };

  const switchLang = () => {
    const next = lang === 'ru' ? 'uz' : 'ru';
    void switchLanguage(next);
    const pathWithoutLang = location.pathname.replace(/^\/(ru|uz)/, '');
    navigate(`/${next}${pathWithoutLang || '/'}${location.search}`, { replace: true });
  };

  return (
    <div className="settings">
      {/* Аватар */}
      <div className="settings__row">
        <div className="settings__avatar-field">
          <UserAvatar
            avatarUrl={user?.avatarUrl}
            firstName={user?.firstName}
            className="settings__avatar"
          />
          <div>
            <p className="settings__label">{t('profile.settings.avatar.title')}</p>
            <p className="settings__value">{t('profile.settings.avatar.hint')}</p>
          </div>
        </div>
        <label className={`settings__btn${avatarUploading ? ' settings__btn--disabled' : ''}`}>
          {avatarUploading ? t('profile.settings.avatar.uploading') : t('profile.settings.avatar.choose')}
          <input
            className="settings__file-input"
            type="file"
            accept="image/*"
            disabled={avatarUploading}
            onChange={(event) => {
              void uploadAvatar(event.target.files?.[0]);
              event.target.value = '';
            }}
          />
        </label>
      </div>
      {avatarError && <p className="settings__error" role="alert">{avatarError}</p>}
      {avatarNotice && <p className="settings__success" role="status">{avatarNotice}</p>}

      {/* Email */}
      <div className="settings__row settings__row--stack">
        <div className="settings__field">
          <p className="settings__label">{t('profile.settings.email.title')}</p>
          {user?.emailPlaceholder ? (
            <>
              <p className="settings__value settings__value--warn">
                {t('profile.emailNotAdded')}
              </p>
              <p className="settings__hint">{t('profile.emailChannelHint')}</p>
            </>
          ) : (
            <div className="settings__email-value">
              <span className="settings__value">{user?.email}</span>
              <span
                className={`settings__badge${user?.emailVerified ? ' settings__badge--verified' : ''}`}
              >
                {user?.emailVerified
                  ? t('profile.settings.email.verified')
                  : t('profile.settings.email.unverified')}
              </span>
            </div>
          )}

          {editing === 'email' && (
            <div className="settings__password-form">
              <label className="settings__password-field">
                <span>{t('profile.settings.email.changeLabel')}</span>
                <input
                  className="settings__input"
                  type="email"
                  value={newEmail}
                  onChange={(e) => setNewEmail(e.target.value)}
                  placeholder={t('profile.settings.email.changePlaceholder')}
                  autoFocus
                />
              </label>
              {emailChangeError && (
                <small className="settings__field-error" role="alert">
                  {emailChangeError}
                </small>
              )}
              <div className="settings__edit-actions">
                <button
                  type="button"
                  className="settings__save"
                  disabled={emailChangeSubmitting}
                  onClick={submitEmailChange}
                >
                  {emailChangeSubmitting
                    ? t('profile.settings.email.changeSending')
                    : t('profile.settings.email.changeSubmit')}
                </button>
                <button type="button" className="settings__link" onClick={cancelEmailChange}>
                  {t('common.cancel')}
                </button>
              </div>
            </div>
          )}
        </div>

        {editing !== 'email' && (
          <div className="settings__edit-actions">
            {user?.emailPlaceholder ? (
              <button type="button" className="settings__btn" onClick={openEmailChange}>
                {t('profile.addEmail')}
              </button>
            ) : (
              <>
                {!user?.emailVerified && (
                  <>
                    <button
                      type="button"
                      className="settings__btn"
                      disabled={emailConfirming}
                      onClick={requestEmailConfirmation}
                    >
                      {emailConfirming
                        ? t('profile.settings.email.sending')
                        : t('profile.settings.email.confirm')}
                    </button>
                    <button
                      type="button"
                      className="settings__link"
                      onClick={() => navigate(`/${lang}/confirm-email`)}
                    >
                      {t('profile.settings.email.enterCode')}
                    </button>
                  </>
                )}
                <button type="button" className="settings__link" onClick={openEmailChange}>
                  {t('profile.settings.email.changeAction')}
                </button>
              </>
            )}
          </div>
        )}
      </div>
      {emailError && <p className="settings__error" role="alert">{emailError}</p>}
      {emailNotice && <p className="settings__success" role="status">{emailNotice}</p>}
      {emailChangeNotice && (
        <p className="settings__success" role="status">{emailChangeNotice}</p>
      )}

      {/* Имя */}
      <div className="settings__row">
        <div className="settings__field">
          <p className="settings__label">{t('profile.settings.name')}</p>

          {editing === 'name' ? (
            <div className="settings__inputs">
              <input
                className="settings__input"
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
                placeholder={t('profile.settings.firstNamePlaceholder')}
                maxLength={100}
                autoFocus
              />
              <input
                className="settings__input"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
                placeholder={t('profile.settings.lastNamePlaceholder')}
                maxLength={100}
              />
            </div>
          ) : (
            <p className="settings__value">{fullName || t('common.notSpecified')}</p>
          )}
        </div>

        {editing === 'name' ? (
          <div className="settings__edit-actions">
            <button type="button" className="settings__save" disabled={saving} onClick={saveName}>
              {saving ? t('profile.settings.saving') : t('common.save')}
            </button>
            <button type="button" className="settings__link" onClick={cancel}>
              {t('common.cancel')}
            </button>
          </div>
        ) : (
          <button type="button" className="settings__link" onClick={() => setEditing('name')}>
            {t('common.edit')}
          </button>
        )}
      </div>

      {error && <p className="settings__error">{error}</p>}

      {/* Телефон — привязка/смена только через OTP (T8b), заменяет легаси PUT /users/me. */}
      <div className="settings__row settings__row--stack">
        <div className="settings__field">
          <p className="settings__label">{t('profile.settings.phoneLabel')}</p>

          {user?.phone ? (
            <p className="settings__value">{user.phone}</p>
          ) : (
            <p className="settings__value settings__value--warn">{t('profile.settings.phoneMissing')}</p>
          )}

          {editing === 'phone' && (
            <div className="settings__password-form">
              {phoneOtpStep === 'phone' ? (
                <label className="settings__password-field">
                  <span>{t('profile.settings.phoneOtp.label')}</span>
                  <IMaskInput
                    className="settings__input"
                    mask="+{998} 00 000-00-00"
                    placeholder={t('profile.settings.phoneOtp.placeholder')}
                    value={phone}
                    onAccept={(val) => setPhone(val.replace(/\s|-/g, ''))}
                    autoFocus
                  />
                </label>
              ) : (
                <label className="settings__password-field">
                  <span>{t('profile.settings.phoneOtp.codeLabel')}</span>
                  <input
                    className="settings__input"
                    inputMode="numeric"
                    maxLength={6}
                    value={phoneCode}
                    onChange={(e) => setPhoneCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    autoFocus
                  />
                </label>
              )}

              {phoneOtpError && (
                <small className="settings__field-error" role="alert">
                  {phoneOtpError}
                </small>
              )}

              <div className="settings__edit-actions">
                <button
                  type="button"
                  className="settings__save"
                  disabled={
                    phoneBusy
                    || (phoneOtpStep === 'phone'
                      ? !PHONE_PATTERN.test(phone.trim())
                      : phoneCode.trim().length !== 6)
                  }
                  onClick={phoneOtpStep === 'phone' ? requestPhoneLinkOtp : submitPhoneLink}
                >
                  {phoneBusy
                    ? t('profile.settings.saving')
                    : phoneOtpStep === 'phone'
                      ? t('profile.settings.phoneOtp.sendCode')
                      : t('profile.settings.phoneOtp.confirm')}
                </button>
                {phoneOtpStep === 'code' && (
                  <button
                    type="button"
                    className="settings__link"
                    disabled={phoneBusy}
                    onClick={requestPhoneLinkOtp}
                  >
                    {t('profile.settings.phoneOtp.resend')}
                  </button>
                )}
                <button type="button" className="settings__link" onClick={cancelPhoneLink}>
                  {t('common.cancel')}
                </button>
              </div>
            </div>
          )}
        </div>

        {editing !== 'phone' && (
          <button
            type="button"
            className={user?.phone ? 'settings__link' : 'settings__btn'}
            onClick={openPhoneLink}
          >
            {user?.phone ? t('common.edit') : t('common.add')}
          </button>
        )}
      </div>
      {phoneNotice && <p className="settings__success" role="status">{phoneNotice}</p>}

      {/* Пароль */}
      <div className="settings__row settings__row--stack">
        <div className="settings__field">
          <p className="settings__label">{t('profile.settings.password.title')}</p>
          {editing === 'password' ? (
            <div className="settings__password-form">
              <label className="settings__password-field">
                <span>{t('profile.settings.password.current')}</span>
                <input
                  className="settings__input"
                  type="password"
                  autoComplete="current-password"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  autoFocus
                />
                {passwordErrors.currentPassword && (
                  <small className="settings__field-error" role="alert">
                    {passwordErrors.currentPassword}
                  </small>
                )}
              </label>
              <label className="settings__password-field">
                <span>{t('profile.settings.password.new')}</span>
                <input
                  className="settings__input"
                  type="password"
                  autoComplete="new-password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                />
                {passwordErrors.newPassword && (
                  <small className="settings__field-error" role="alert">
                    {passwordErrors.newPassword}
                  </small>
                )}
              </label>
              <label className="settings__password-field">
                <span>{t('profile.settings.password.confirm')}</span>
                <input
                  className="settings__input"
                  type="password"
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                />
                {passwordErrors.confirmPassword && (
                  <small className="settings__field-error" role="alert">
                    {passwordErrors.confirmPassword}
                  </small>
                )}
              </label>
              <p className="settings__hint">{t('profile.settings.password.hint')}</p>
              {passwordErrors.general && (
                <p className="settings__field-error" role="alert">{passwordErrors.general}</p>
              )}
              <div className="settings__edit-actions">
                <button type="button" className="settings__save" disabled={saving} onClick={savePassword}>
                  {saving ? t('profile.settings.saving') : t('profile.settings.password.save')}
                </button>
                <button type="button" className="settings__link" onClick={cancel}>
                  {t('common.cancel')}
                </button>
              </div>
            </div>
          ) : (
            <p className="settings__value">••••••••</p>
          )}
        </div>
        {editing !== 'password' && (
          <button
            type="button"
            className="settings__link"
            onClick={() => {
              setPasswordNotice('');
              setEditing('password');
            }}
          >
            {t('profile.settings.password.change')}
          </button>
        )}
      </div>

      {passwordNotice && (
        <p className="settings__success" role="status" aria-live="polite">{passwordNotice}</p>
      )}

      {/* Язык */}
      <div className="settings__row">
        <div className="settings__field">
          <p className="settings__label">{t('profile.settings.language')}</p>
          <p className="settings__value">{lang === 'ru' ? 'Русский' : "O'zbekcha"}</p>
        </div>
        <button type="button" className="settings__link" onClick={switchLang}>
          {lang === 'ru' ? "O'zbekcha" : 'Русский'}
        </button>
      </div>

      {/* Уведомления */}
      <div className="settings__row">
        <div className="settings__field">
          <p className="settings__label">{t('profile.settings.notifications')}</p>
          <p className="settings__value">{t('profile.settings.notificationsDesc')}</p>
        </div>
        <button
          type="button"
          className="settings__link"
          onClick={() => setNotificationsOpen((v) => !v)}
        >
          {notificationsOpen ? t('common.hide') : t('profile.settings.view')}
        </button>
      </div>

      {notificationsOpen && (
        <div className="settings__notifications">
          <NotificationsSection />
        </div>
      )}
    </div>
  );
}
