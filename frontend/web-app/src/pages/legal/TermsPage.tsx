import { useTranslation } from 'react-i18next';

export default function TermsPage() {
  const { t } = useTranslation();
  return (
    <div className="container legal-page">
      <h1 className="page-title">{t('footer.terms')}</h1>
      <p className="section-copy">
        {t('legal.termsPlaceholder')}
      </p>
    </div>
  );
}
