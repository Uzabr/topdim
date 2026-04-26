import { useState } from 'react';
import type { FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { submitPartnerApplication } from '../../api/partners';
import type { PartnerApplicationData } from '../../api/partners';
import './PartnersPage.css';

export default function PartnersPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language || 'ru';

  const [form, setForm] = useState<PartnerApplicationData>({
    firstName: '',
    lastName: '',
    phone: '',
    companyName: '',
    email: '',
    city: '',
    address: '',
    workingHours: '',
    businessCategory: '',
    website: '',
    telegramUsername: '',
    comment: '',
  });

  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>,
  ) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);

    try {
      await submitPartnerApplication(form);
      setSubmitted(true);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      setError(
        axiosErr?.response?.data?.message ||
          t('partners.error', { defaultValue: 'Ошибка при отправке заявки. Попробуйте позже.' }),
      );
    } finally {
      setSubmitting(false);
    }
  };

  if (submitted) {
    return (
      <div className="partner-page">
        <div className="partner-success">
          <div className="partner-success-icon">🎉</div>
          <h2>{t('partners.successTitle', { defaultValue: 'Заявка отправлена!' })}</h2>
          <p>
            {t('partners.successText', {
              defaultValue: 'Наша команда рассмотрит вашу заявку в ближайшее время. Мы свяжемся с вами по указанным контактам.',
            })}
          </p>
          <Link to={`/${lang}`} className="partner-back-btn">
            {t('partners.backHome', { defaultValue: 'На главную' })}
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="partner-page">
      <div className="partner-hero">
        <h1>{t('partners.title', { defaultValue: 'Станьте партнёром TopDim' })}</h1>
        <p>
          {t('partners.subtitle', {
            defaultValue:
              'Размещайте свои купоны на нашей платформе и привлекайте тысячи новых клиентов. Заполните форму — мы всё настроим за вас.',
          })}
        </p>
      </div>

      <div className="partner-form-card">
        <h2>{t('partners.formTitle', { defaultValue: 'Заявка на партнёрство' })}</h2>

        <form className="partner-form" onSubmit={handleSubmit}>
          <div className="partner-form-row">
            <div className="partner-field">
              <label>
                {t('partners.firstName', { defaultValue: 'Имя' })}
                <span className="required">*</span>
              </label>
              <input
                id="partner-firstName"
                name="firstName"
                required
                maxLength={50}
                value={form.firstName}
                onChange={handleChange}
                placeholder="Алишер"
              />
            </div>
            <div className="partner-field">
              <label>
                {t('partners.lastName', { defaultValue: 'Фамилия' })}
                <span className="required">*</span>
              </label>
              <input
                id="partner-lastName"
                name="lastName"
                required
                maxLength={50}
                value={form.lastName}
                onChange={handleChange}
                placeholder="Каримов"
              />
            </div>
          </div>

          <div className="partner-form-row">
            <div className="partner-field">
              <label>
                {t('partners.phone', { defaultValue: 'Телефон' })}
                <span className="required">*</span>
              </label>
              <input
                id="partner-phone"
                name="phone"
                type="tel"
                required
                maxLength={20}
                value={form.phone}
                onChange={handleChange}
                placeholder="+998 90 123 45 67"
              />
            </div>
            <div className="partner-field">
              <label>{t('partners.email', { defaultValue: 'Email' })}</label>
              <input
                id="partner-email"
                name="email"
                type="email"
                maxLength={255}
                value={form.email}
                onChange={handleChange}
                placeholder="partner@example.uz"
              />
            </div>
          </div>

          <div className="partner-field">
            <label>
              {t('partners.companyName', { defaultValue: 'Название бизнеса' })}
              <span className="required">*</span>
            </label>
            <input
              id="partner-companyName"
              name="companyName"
              required
              maxLength={100}
              value={form.companyName}
              onChange={handleChange}
              placeholder="Ali Cafe"
            />
          </div>

          <div className="partner-form-row">
            <div className="partner-field">
              <label>{t('partners.city', { defaultValue: 'Город' })}</label>
              <input
                id="partner-city"
                name="city"
                maxLength={100}
                value={form.city}
                onChange={handleChange}
                placeholder="Ташкент"
              />
            </div>
            <div className="partner-field">
              <label>{t('partners.businessCategory', { defaultValue: 'Категория бизнеса' })}</label>
              <select
                id="partner-businessCategory"
                name="businessCategory"
                value={form.businessCategory}
                onChange={handleChange}
              >
                <option value="">{t('partners.selectCategory', { defaultValue: 'Выберите...' })}</option>
                <option value="beauty">{t('partners.cat.beauty', { defaultValue: 'Красота и здоровье' })}</option>
                <option value="food">{t('partners.cat.food', { defaultValue: 'Еда и напитки' })}</option>
                <option value="entertainment">{t('partners.cat.entertainment', { defaultValue: 'Развлечения' })}</option>
                <option value="education">{t('partners.cat.education', { defaultValue: 'Образование' })}</option>
                <option value="fitness">{t('partners.cat.fitness', { defaultValue: 'Фитнес и спорт' })}</option>
                <option value="services">{t('partners.cat.services', { defaultValue: 'Услуги' })}</option>
                <option value="other">{t('partners.cat.other', { defaultValue: 'Другое' })}</option>
              </select>
            </div>
          </div>

          <div className="partner-field">
            <label>{t('partners.address', { defaultValue: 'Адрес' })}</label>
            <input
              id="partner-address"
              name="address"
              maxLength={500}
              value={form.address}
              onChange={handleChange}
              placeholder="ул. Амира Темура, 10"
            />
          </div>

          <div className="partner-form-row">
            <div className="partner-field">
              <label>{t('partners.workingHours', { defaultValue: 'Время работы' })}</label>
              <input
                id="partner-workingHours"
                name="workingHours"
                maxLength={255}
                value={form.workingHours}
                onChange={handleChange}
                placeholder="10:00 – 22:00"
              />
            </div>
            <div className="partner-field">
              <label>Telegram</label>
              <input
                id="partner-telegram"
                name="telegramUsername"
                maxLength={100}
                value={form.telegramUsername}
                onChange={handleChange}
                placeholder="@alicafe"
              />
            </div>
          </div>

          <div className="partner-field">
            <label>{t('partners.website', { defaultValue: 'Сайт' })}</label>
            <input
              id="partner-website"
              name="website"
              type="url"
              maxLength={255}
              value={form.website}
              onChange={handleChange}
              placeholder="https://alicafe.uz"
            />
          </div>

          <div className="partner-field">
            <label>{t('partners.comment', { defaultValue: 'Комментарий' })}</label>
            <textarea
              id="partner-comment"
              name="comment"
              maxLength={1000}
              value={form.comment}
              onChange={handleChange}
              placeholder={t('partners.commentPlaceholder', {
                defaultValue: 'Расскажите о своём бизнесе...',
              })}
            />
          </div>

          {error && <div className="partner-field"><span className="field-error">{error}</span></div>}

          <button
            type="submit"
            className="partner-submit-btn"
            disabled={submitting}
            id="partner-submit"
          >
            {submitting
              ? t('partners.submitting', { defaultValue: 'Отправка...' })
              : t('partners.submit', { defaultValue: 'Отправить заявку' })}
          </button>
        </form>
      </div>
    </div>
  );
}
