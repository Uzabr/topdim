import { useTranslation } from 'react-i18next';
import './HowItWorks.css';

const STEPS = ['buy', 'show', 'save'] as const;

/** «Как это работает» — 3 шага доверия (design_handoff_sizbiz → §10.4). */
export default function HowItWorks() {
  const { t } = useTranslation();

  return (
    <section className="how">
      <div className="how__head">
        <h2 className="how__title">{t('home.how.title')}</h2>
        <span className="how__note">{t('home.how.note')}</span>
      </div>

      <div className="how__grid">
        {STEPS.map((step, i) => (
          <div key={step} className="how__card">
            <span className="how__num">{i + 1}</span>
            <h3 className="how__card-title">{t(`home.how.${step}.title`)}</h3>
            <p className="how__card-text">{t(`home.how.${step}.text`)}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
