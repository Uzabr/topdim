import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '../../api/auth';
import { mediaApi } from '../../api/media';
import { useAuthStore } from '../../store/authStore';
import { validateAvatarFile } from '../../utils/avatar';
import { isStrongPassword } from '../../utils/password';
import UserAvatar from '../ui/UserAvatar';
import NotificationsSection from './NotificationsSection';
import './ProfileSettingsSection.css';

type EditableProfileField = 'name' | 'phone';
type EditingField = EditableProfileField | 'password' | null;

interface PasswordErrors {
  currentPassword?: string;
  newPassword?: string;
  confirmPassword?: string;
  general?: string;
}

/** Настройки — строки-карточки (design_handoff_sizbiz → «Профиль», таб «Настройки»). */
export default function ProfileSettingsSection() {
  const { t, i18n } = useTranslation();
  const { user, updateProfile, logout } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();

  const [editing, setEditing] = useState<EditingField>(null);
  const [firstName, setFirstName] = useState(user?.firstName ?? '');
  const [lastName, setLastName] = useState(user?.lastName ?? '');
  const [phone, setPhone] = useState(user?.phone ?? '');
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

    setAvatarUploading(true);
    try {
      const uploaded = await mediaApi.uploadFile(file);
      await updateProfile({ avatarUrl: uploaded.url });
      setAvatarNotice(t('profile.settings.avatar.success'));
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      setAvatarError(e.response?.data?.message || t('profile.settings.avatar.error'));
    } finally {
      setAvatarUploading(false);
    }
  };

  const requestEmailConfirmation = async () => {
    setEmailConfirming(true);
    setEmailNotice('');
    setEmailError('');
    try {
      await authApi.requestEmailConfirm();
      setEmailNotice(t('profile.settings.email.sent'));
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      setEmailError(e.response?.data?.message || t('profile.settings.email.error'));
    } finally {
      setEmailConfirming(false);
    }
  };

  const save = async (field: EditableProfileField) => {
    setError('');

    if (field === 'name' && !firstName.trim()) {
      setError(t('profile.settings.validation.firstNameRequired'));
      return;
    }
    if (field === 'phone' && !/^\+998\d{9}$/.test(phone.trim())) {
      setError(t('profile.settings.validation.phoneMin'));
      return;
    }

    setSaving(true);
    try {
      await updateProfile(
        field === 'name'
          ? { firstName: firstName.trim(), lastName: lastName.trim() }
          : { phone: phone.trim() },
      );
      setEditing(null);
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      setError(e.response?.data?.message || t('profile.settings.error'));
    } finally {
      setSaving(false);
    }
  };

  const cancel = () => {
    setFirstName(user?.firstName ?? '');
    setLastName(user?.lastName ?? '');
    setPhone(user?.phone ?? '');
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setPasswordErrors({});
    setError('');
    setEditing(null);
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

    setSaving(true);
    setPasswordErrors({});
    setPasswordNotice('');
    try {
      await authApi.changePassword(currentPassword, newPassword);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setEditing(null);
      setPasswordNotice(t('profile.settings.password.success'));
      logout();
      navigate(`/${lang}/login`, { replace: true });
    } catch (err: unknown) {
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
      setSaving(false);
    }
  };

  const switchLang = () => {
    const next = lang === 'ru' ? 'uz' : 'ru';
    i18n.changeLanguage(next);
    localStorage.setItem('language', next);
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
      <div className="settings__row">
        <div className="settings__field">
          <p className="settings__label">{t('profile.settings.email.title')}</p>
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
        </div>
        {!user?.emailVerified && (
          <div className="settings__edit-actions">
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
          </div>
        )}
      </div>
      {emailError && <p className="settings__error" role="alert">{emailError}</p>}
      {emailNotice && <p className="settings__success" role="status">{emailNotice}</p>}

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
            <button type="button" className="settings__save" disabled={saving} onClick={() => save('name')}>
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

      {/* Телефон */}
      <div className="settings__row">
        <div className="settings__field">
          <p className="settings__label">{t('profile.settings.phoneLabel')}</p>

          {editing === 'phone' ? (
            <div className="settings__inputs">
              <input
                className="settings__input"
                type="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="+998 90 123 45 67"
                maxLength={20}
                autoFocus
              />
            </div>
          ) : user?.phone ? (
            <p className="settings__value">{user.phone}</p>
          ) : (
            <p className="settings__value settings__value--warn">{t('profile.settings.phoneMissing')}</p>
          )}
        </div>

        {editing === 'phone' ? (
          <div className="settings__edit-actions">
            <button type="button" className="settings__save" disabled={saving} onClick={() => save('phone')}>
              {saving ? t('profile.settings.saving') : t('common.save')}
            </button>
            <button type="button" className="settings__link" onClick={cancel}>
              {t('common.cancel')}
            </button>
          </div>
        ) : user?.phone ? (
          <button type="button" className="settings__link" onClick={() => setEditing('phone')}>
            {t('common.edit')}
          </button>
        ) : (
          <button type="button" className="settings__btn" onClick={() => setEditing('phone')}>
            {t('common.add')}
          </button>
        )}
      </div>

      {error && <p className="settings__error">{error}</p>}

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
          {notificationsOpen ? t('common.hide') : t('profile.settings.configure')}
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
