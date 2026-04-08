import { useTranslation } from 'react-i18next';

export default function PartnersPage() {
  const { t } = useTranslation();

  return (
    <div className="container page-content" style={{ padding: '80px 16px', minHeight: '60vh' }}>
      <h1 className="page-title">{t('footer.business', { defaultValue: 'Для бизнеса' })}</h1>
      <p style={{ marginTop: '20px', color: 'var(--text-secondary)' }}>
        Здесь будет информация для партнеров и бизнеса...
      </p>
    </div>
  );
}
