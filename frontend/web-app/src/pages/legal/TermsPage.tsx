import { useTranslation } from 'react-i18next';

export default function TermsPage() {
  const { t } = useTranslation();

  return (
    <div className="container page-content" style={{ padding: '80px 16px', minHeight: '60vh' }}>
      <h1 className="page-title">{t('footer.terms', { defaultValue: 'Пользовательское соглашение' })}</h1>
      <p style={{ marginTop: '20px', color: 'var(--text-secondary)' }}>
        Здесь будет текст пользовательского соглашения...
      </p>
    </div>
  );
}
