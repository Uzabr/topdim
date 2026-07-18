import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { SUPPORT_TELEGRAM_URL } from '../../config/features';
import './ProfileHelpSection.css';

/** Помощь — 4 FAQ-карточки + связь с поддержкой. */
export default function ProfileHelpSection() {
  const { t } = useTranslation();

  const faq = useMemo(
    () =>
      (['redeem', 'noShow', 'bonus', 'refused'] as const).map((key) => ({
        key,
        question: t(`profile.help.faq.${key}.q`),
        answer: t(`profile.help.faq.${key}.a`),
      })),
    [t],
  );

  return (
    <div className="help">
      {faq.map((item) => (
        <div key={item.key} className="help__card">
          <p className="help__q">{item.question}</p>
          <p className="help__a">{item.answer}</p>
        </div>
      ))}

      <div className="help__support">
        <a
          className="help__support-btn"
          href={SUPPORT_TELEGRAM_URL}
          target="_blank"
          rel="noopener noreferrer"
        >
          {t('profile.help.contact')}
        </a>
        <span className="help__support-hint">{t('profile.help.contactHint')}</span>
      </div>
    </div>
  );
}
