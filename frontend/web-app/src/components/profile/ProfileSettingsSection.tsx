import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import NotificationsSection from './NotificationsSection';
import './ProfileSettingsSection.css';

type EditingField = 'name' | 'phone' | null;

/** Настройки — строки-карточки (design_handoff_sizbiz → «Профиль», таб «Настройки»). */
export default function ProfileSettingsSection() {
  const { t, i18n } = useTranslation();
  const { user, updateProfile } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();

  const [editing, setEditing] = useState<EditingField>(null);
  const [firstName, setFirstName] = useState(user?.firstName ?? '');
  const [lastName, setLastName] = useState(user?.lastName ?? '');
  const [phone, setPhone] = useState(user?.phone ?? '');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  const lang = i18n.language?.substring(0, 2) === 'uz' ? 'uz' : 'ru';
  const fullName = [user?.firstName, user?.lastName].filter(Boolean).join(' ');

  const save = async (field: Exclude<EditingField, null>) => {
    setError('');

    if (field === 'name' && !firstName.trim()) {
      setError(t('profile.settings.validation.firstNameRequired'));
      return;
    }
    if (field === 'phone' && phone.trim().length < 9) {
      setError(t('profile.settings.validation.phoneMin'));
      return;
    }

    setSaving(true);
    try {
      await updateProfile(
        field === 'name'
          ? { firstName: firstName.trim(), lastName: lastName.trim() || undefined }
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
    setError('');
    setEditing(null);
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
