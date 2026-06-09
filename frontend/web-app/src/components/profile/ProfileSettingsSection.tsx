import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../../store/authStore';
import { Save, CheckCircle, AlertCircle } from 'lucide-react';
import './ProfileSettingsSection.css';

export default function ProfileSettingsSection() {
  const { t } = useTranslation();
  const { user, updateProfile } = useAuthStore();

  const [firstName, setFirstName] = useState(user?.firstName || '');
  const [lastName, setLastName] = useState(user?.lastName || '');
  const [phone, setPhone] = useState(user?.phone || '');
  const [isLoading, setIsLoading] = useState(false);
  const [success, setSuccess] = useState('');
  const [error, setError] = useState('');

  const validate = (): string | null => {
    if (!firstName.trim()) return t('profile.settings.validation.firstNameRequired');
    if (firstName.trim().length > 100) return t('profile.settings.validation.firstNameMax');
    if (lastName.trim().length > 100) return t('profile.settings.validation.lastNameMax');
    if (!phone.trim()) return t('profile.settings.validation.phoneRequired');
    if (phone.trim().length < 9) return t('profile.settings.validation.phoneMin');
    if (phone.trim().length > 20) return t('profile.settings.validation.phoneMax');
    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setIsLoading(true);
    try {
      await updateProfile({
        firstName: firstName.trim(),
        lastName: lastName.trim() || undefined,
        phone: phone.trim(),
      });
      setSuccess(t('profile.settings.success'));
      setTimeout(() => setSuccess(''), 3000);
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      const msg = e.response?.data?.message || t('profile.settings.error');
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="profile-settings">
      <h3 className="profile-settings__title">{t('profile.settings.title')}</h3>

      {!user?.phone && (
        <div className="profile-settings__phone-warning">
          <AlertCircle size={16} />
          {t('profile.settings.phoneHint')}
        </div>
      )}

      <form className="profile-settings__form" onSubmit={handleSubmit}>
        <div className="profile-settings__field">
          <label className="profile-settings__label">Email</label>
          <input
            className="profile-settings__input profile-settings__input--readonly"
            type="email"
            value={user?.email || ''}
            disabled
            id="profile-email"
          />
        </div>

        <div className="profile-settings__field">
          <label className="profile-settings__label" htmlFor="profile-first-name">{t('profile.settings.firstName')}</label>
          <input
            className="profile-settings__input"
            type="text"
            id="profile-first-name"
            value={firstName}
            onChange={(e) => { setFirstName(e.target.value); setError(''); }}
            maxLength={100}
            placeholder={t('profile.settings.firstNamePlaceholder')}
          />
        </div>

        <div className="profile-settings__field">
          <label className="profile-settings__label" htmlFor="profile-last-name">{t('profile.settings.lastName')}</label>
          <input
            className="profile-settings__input"
            type="text"
            id="profile-last-name"
            value={lastName}
            onChange={(e) => { setLastName(e.target.value); setError(''); }}
            maxLength={100}
            placeholder={t('profile.settings.lastNamePlaceholder')}
          />
        </div>

        <div className="profile-settings__field">
          <label className="profile-settings__label" htmlFor="profile-phone">{t('profile.settings.phone')}</label>
          <input
            className="profile-settings__input"
            type="tel"
            id="profile-phone"
            value={phone}
            onChange={(e) => { setPhone(e.target.value); setError(''); }}
            placeholder="+998 90 123 45 67"
            maxLength={20}
          />
        </div>

        {error && (
          <div className="profile-settings__error">
            <AlertCircle size={14} />
            {error}
          </div>
        )}

        {success && (
          <div className="profile-settings__success">
            <CheckCircle size={14} />
            {success}
          </div>
        )}

        <button
          type="submit"
          className="primary-button profile-settings__submit"
          disabled={isLoading}
          id="profile-save-btn"
        >
          {isLoading ? t('profile.settings.saving') : (
            <>
              <Save size={16} />
              {t('profile.settings.save')}
            </>
          )}
        </button>
      </form>
    </div>
  );
}
