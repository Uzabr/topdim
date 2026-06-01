import { useTranslation } from 'react-i18next';

export default function FAQPage() {
  const { t } = useTranslation();
  return (
    <div className="container legal-page">
      <h1 className="page-title">{t('footer.faq')}</h1>
      <p className="section-copy">
        {t('legal.faqPlaceholder')}
      </p>
    </div>
  );
}
