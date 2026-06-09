import { useTranslation } from 'react-i18next';

export default function PrivacyPage() {
  const { t } = useTranslation();
  return (
    <div className="container legal-page">
      <h1 className="page-title">{t('legal.privacyTitle')}</h1>
      <p className="section-copy">
        {t('legal.privacyPlaceholder')}
      </p>
    </div>
  );
}
