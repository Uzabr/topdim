import { useState } from 'react';
import { useAuthStore } from '../../store/authStore';
import { Save, CheckCircle, AlertCircle } from 'lucide-react';
import './ProfileSettingsSection.css';

export default function ProfileSettingsSection() {
  const { user, updateProfile } = useAuthStore();

  const [firstName, setFirstName] = useState(user?.firstName || '');
  const [lastName, setLastName] = useState(user?.lastName || '');
  const [phone, setPhone] = useState(user?.phone || '');
  const [isLoading, setIsLoading] = useState(false);
  const [success, setSuccess] = useState('');
  const [error, setError] = useState('');

  const validate = (): string | null => {
    if (!firstName.trim()) return 'Имя обязательно';
    if (firstName.trim().length > 100) return 'Имя не должно превышать 100 символов';
    if (lastName.trim().length > 100) return 'Фамилия не должна превышать 100 символов';
    if (!phone.trim()) return 'Укажите телефон';
    if (phone.trim().length < 9) return 'Телефон слишком короткий (минимум 9 символов)';
    if (phone.trim().length > 20) return 'Телефон не должен превышать 20 символов';
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
      setSuccess('Профиль обновлён');
      setTimeout(() => setSuccess(''), 3000);
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      const msg = e.response?.data?.message || 'Ошибка обновления профиля';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="profile-settings">
      <h3 className="profile-settings__title">Настройки профиля</h3>

      {!user?.phone && (
        <div className="profile-settings__phone-warning">
          <AlertCircle size={16} />
          Телефон нужен для оформления заказа и связи по купону.
        </div>
      )}

      <form className="profile-settings__form" onSubmit={handleSubmit}>
        {/* Email — read-only */}
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

        {/* First Name */}
        <div className="profile-settings__field">
          <label className="profile-settings__label" htmlFor="profile-first-name">Имя *</label>
          <input
            className="profile-settings__input"
            type="text"
            id="profile-first-name"
            value={firstName}
            onChange={(e) => { setFirstName(e.target.value); setError(''); }}
            maxLength={100}
            placeholder="Имя"
          />
        </div>

        {/* Last Name */}
        <div className="profile-settings__field">
          <label className="profile-settings__label" htmlFor="profile-last-name">Фамилия</label>
          <input
            className="profile-settings__input"
            type="text"
            id="profile-last-name"
            value={lastName}
            onChange={(e) => { setLastName(e.target.value); setError(''); }}
            maxLength={100}
            placeholder="Фамилия (необязательно)"
          />
        </div>

        {/* Phone */}
        <div className="profile-settings__field">
          <label className="profile-settings__label" htmlFor="profile-phone">Телефон *</label>
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
          {isLoading ? 'Сохранение...' : (
            <>
              <Save size={16} />
              Сохранить изменения
            </>
          )}
        </button>
      </form>
    </div>
  );
}
