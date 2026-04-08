import { useTranslation } from 'react-i18next';

export default function FAQPage() {
  const { t } = useTranslation();

  return (
    <div className="container page-content" style={{ padding: '80px 16px', minHeight: '60vh' }}>
      <h1 className="page-title">{t('footer.faq', { defaultValue: 'Частые вопросы' })}</h1>
      <p style={{ marginTop: '20px', color: 'var(--text-secondary)' }}>
        Здесь будут ответы на частые вопросы...
      </p>
    </div>
  );
}
