import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  ArrowRight,
  CheckCircle2,
  ChevronDown,
  MapPin,
  Play,
  Users,
  BarChart3,
  ShieldCheck,
  Eye,
  Zap,
  Layers,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { submitPartnerApplication } from '../../../api/partners';
import type { PartnerApplicationData } from '../../../api/partners';
import './PartnerLandingPage.css';

type FormValues = {
  name: string;
  phone: string;
  city: string;
  businessCategory: string;
  businessName?: string;
  comment?: string;
};

/**
 * Лендинг для бизнеса — в том же визуальном языке, что и главная
 * (палитра «Оса»: paper/card/ink, карточки, pill-CTA).
 */
export default function PartnerLandingPage() {
  return (
    <div className="partners container">
      <HeroSection />
      <AudienceSection />
      <StepsSection />
      <BenefitsSection />
      <FormSection />
      <FaqSection />
    </div>
  );
}

function HeroSection() {
  const { t } = useTranslation();

  return (
    <section className="partners-hero">
      <span className="partners-hero__label">{t('footer.business')}</span>
      <h1 className="partners-hero__title">
        {t('partners.heroTitleLine1')}
        <br />
        {t('partners.heroTitleLine2')}
      </h1>
      <p className="partners-hero__desc">{t('partners.heroDesc')}</p>
      <div className="partners-hero__actions">
        <a href="#lead" className="partners-hero__cta">
          {t('partners.ctaJoin')}
          <ArrowRight size={18} />
        </a>
        <a href="#steps" className="partners-hero__link">
          {t('partners.howItWorks')}
        </a>
      </div>
    </section>
  );
}

function AudienceSection() {
  const { t } = useTranslation();
  const cats = [
    { n: t('partners.audience.cafe'), Icon: Zap },
    { n: t('partners.audience.beauty'), Icon: Eye },
    { n: t('partners.audience.fitness'), Icon: Users },
    { n: t('partners.audience.shops'), Icon: MapPin },
    { n: t('partners.audience.entertainment'), Icon: Play },
    { n: t('partners.audience.services'), Icon: Layers },
  ];

  return (
    <section className="partners-section">
      <h2 className="partners-section__title">
        {t('partners.audienceTitle')} sizbiz
      </h2>
      <p className="partners-section__desc">{t('partners.audienceDesc')}</p>

      <div className="partners-cats">
        {cats.map(({ n, Icon }) => (
          <div key={n} className="partners-cat">
            <span className="partners-cat__icon" aria-hidden>
              <Icon size={22} strokeWidth={1.75} />
            </span>
            <h3 className="partners-cat__name">{n}</h3>
          </div>
        ))}
      </div>
    </section>
  );
}

function StepsSection() {
  const { t } = useTranslation();
  const steps = [
    { title: t('partners.steps.apply.title'), desc: t('partners.steps.apply.desc') },
    { title: t('partners.steps.setup.title'), desc: t('partners.steps.setup.desc') },
    { title: t('partners.steps.live.title'), desc: t('partners.steps.live.desc') },
    { title: t('partners.steps.clients.title'), desc: t('partners.steps.clients.desc') },
  ];

  return (
    <section className="partners-section" id="steps">
      <h2 className="partners-section__title">
        {t('partners.algorithmTitleLine1')} {t('partners.algorithmTitleLine2')}
      </h2>

      <div className="partners-steps">
        {steps.map((step, i) => (
          <div key={step.title} className="partners-step">
            <span className="partners-step__num">{i + 1}</span>
            <h3 className="partners-step__title">{step.title}</h3>
            <p className="partners-step__desc">{step.desc}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

function BenefitsSection() {
  const { t } = useTranslation();
  const items = [
    {
      title: t('partners.benefitNewClients'),
      desc: t('partners.benefitNewClientsDesc'),
      Icon: Users,
      lead: true,
    },
    {
      title: t('partners.benefitTransparency'),
      desc: t('partners.benefitTransparencyDesc'),
      Icon: BarChart3,
    },
    {
      title: t('partners.benefitLocal'),
      desc: t('partners.benefitLocalDesc'),
      Icon: MapPin,
    },
    {
      title: t('partners.benefitMotivation'),
      desc: t('partners.benefitMotivationDesc'),
      Icon: Zap,
    },
  ];

  return (
    <section className="partners-section">
      <h2 className="partners-section__title">{t('partners.benefitsTitle')}</h2>

      <div className="partners-benefits">
        {items.map(({ title, desc, Icon, lead }) => (
          <div
            key={title}
            className={`partners-benefit${lead ? ' partners-benefit--lead' : ''}`}
          >
            <span className="partners-benefit__icon" aria-hidden>
              <Icon size={22} strokeWidth={1.75} />
            </span>
            <h3 className="partners-benefit__title">{title}</h3>
            <p className="partners-benefit__desc">{desc}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

function FormSection() {
  const { t } = useTranslation();
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);

  const schema = useMemo(
    () =>
      z.object({
        name: z.string().trim().min(2, t('partners.validation.name')),
        phone: z
          .string()
          .trim()
          .regex(/^\+?998\s?\d{2}\s?\d{3}\s?\d{2}\s?\d{2}$/, t('partners.validation.phone')),
        city: z.string().trim().min(1, t('partners.validation.city')),
        businessCategory: z.string().trim().min(1, t('partners.validation.category')),
        businessName: z.string().trim().optional().or(z.literal('')),
        comment: z.string().trim().optional().or(z.literal('')),
      }),
    [t],
  );

  const cities = useMemo(
    () => [
      t('partners.cities.tashkent'),
      t('partners.cities.samarkand'),
      t('partners.cities.bukhara'),
      t('partners.cities.namangan'),
      t('partners.cities.andijan'),
      t('partners.cities.fergana'),
      t('partners.cities.nukus'),
      t('partners.cities.khiva'),
    ],
    [t],
  );

  const bizCats = useMemo(
    () => [
      t('partners.bizCategories.cafe'),
      t('partners.bizCategories.beauty'),
      t('partners.bizCategories.fitness'),
      t('partners.bizCategories.education'),
      t('partners.bizCategories.entertainment'),
      t('partners.bizCategories.shop'),
      t('partners.bizCategories.services'),
    ],
    [t],
  );

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (values) => {
    setSubmitting(true);
    try {
      const data: PartnerApplicationData = {
        firstName: values.name,
        lastName: '-',
        phone: values.phone,
        companyName: values.businessName || `${values.businessCategory}, ${values.city}`,
        city: values.city,
        businessCategory: values.businessCategory,
        comment: values.comment || t('partners.commentDefault'),
      };
      await submitPartnerApplication(data);
      setSuccess(true);
    } catch {
      alert(t('partners.formError'));
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <section className="partners-section" id="lead">
      <div className="partners-form">
        <div className="partners-form__intro">
          <h2 className="partners-section__title">
            {t('partners.formTitleLine1')}
            <br />
            {t('partners.formTitleLine2')}
          </h2>
          <p className="partners-section__desc">{t('partners.formDesc')}</p>
          <p className="partners-form__privacy">
            <ShieldCheck size={18} aria-hidden />
            {t('partners.dataProtection')}
          </p>
        </div>

        <div className="partners-form__body">
          {success ? (
            <div className="partners-form__success">
              <CheckCircle2 size={48} aria-hidden />
              <h3>{t('partners.formSuccessTitle')}</h3>
              <p>{t('partners.formSuccessDesc')}</p>
            </div>
          ) : (
            <form onSubmit={onSubmit} noValidate>
              <div className="partners-form__row">
                <div className="partners-field">
                  <label htmlFor="partner-name">{t('partners.formName')}</label>
                  <input
                    id="partner-name"
                    className="input-field"
                    {...register('name')}
                    placeholder={t('partners.namePlaceholder')}
                  />
                  {errors.name && (
                    <span className="partners-field__error" role="alert">
                      {errors.name.message}
                    </span>
                  )}
                </div>
                <div className="partners-field">
                  <label htmlFor="partner-phone">{t('partners.formPhone')}</label>
                  <input
                    id="partner-phone"
                    className="input-field"
                    {...register('phone')}
                    placeholder="+998 XX XXX XX XX"
                  />
                  {errors.phone && (
                    <span className="partners-field__error" role="alert">
                      {errors.phone.message}
                    </span>
                  )}
                </div>
              </div>

              <div className="partners-form__row">
                <div className="partners-field">
                  <label htmlFor="partner-city">{t('partners.formCity')}</label>
                  <div className="partners-select">
                    <select
                      id="partner-city"
                      className="input-field"
                      defaultValue=""
                      {...register('city')}
                    >
                      <option value="" disabled>
                        {t('partners.formSelect')}
                      </option>
                      {cities.map((city) => (
                        <option key={city} value={city}>
                          {city}
                        </option>
                      ))}
                    </select>
                    <ChevronDown size={16} className="partners-select__chevron" aria-hidden />
                  </div>
                  {errors.city && (
                    <span className="partners-field__error" role="alert">
                      {errors.city.message}
                    </span>
                  )}
                </div>
                <div className="partners-field">
                  <label htmlFor="partner-category">{t('partners.formCategory')}</label>
                  <div className="partners-select">
                    <select
                      id="partner-category"
                      className="input-field"
                      defaultValue=""
                      {...register('businessCategory')}
                    >
                      <option value="" disabled>
                        {t('partners.formSelect')}
                      </option>
                      {bizCats.map((cat) => (
                        <option key={cat} value={cat}>
                          {cat}
                        </option>
                      ))}
                    </select>
                    <ChevronDown size={16} className="partners-select__chevron" aria-hidden />
                  </div>
                  {errors.businessCategory && (
                    <span className="partners-field__error" role="alert">
                      {errors.businessCategory.message}
                    </span>
                  )}
                </div>
              </div>

              <div className="partners-field">
                <label htmlFor="partner-business">{t('partners.formBusinessName')}</label>
                <input
                  id="partner-business"
                  className="input-field"
                  {...register('businessName')}
                  placeholder={t('partners.formOptional')}
                />
              </div>

              <button type="submit" disabled={submitting} className="partners-form__submit">
                {submitting ? t('common.submitting') : t('partners.formSubmit')}
              </button>
            </form>
          )}
        </div>
      </div>
    </section>
  );
}

function FaqSection() {
  const { t } = useTranslation();
  const [open, setOpen] = useState<number | null>(0);
  const faqs = [
    { q: t('partners.faq.q1'), a: t('partners.faq.a1') },
    { q: t('partners.faq.q2'), a: t('partners.faq.a2') },
    { q: t('partners.faq.q3'), a: t('partners.faq.a3') },
    { q: t('partners.faq.q4'), a: t('partners.faq.a4') },
  ];

  return (
    <section className="partners-section partners-section--last">
      <h2 className="partners-section__title">{t('partners.faqTitle')}</h2>

      <div className="partners-faq">
        {faqs.map((item, i) => {
          const isOpen = open === i;
          return (
            <div key={item.q} className={`partners-faq__item${isOpen ? ' is-open' : ''}`}>
              <button
                type="button"
                className="partners-faq__head"
                aria-expanded={isOpen}
                onClick={() => setOpen(isOpen ? null : i)}
              >
                {item.q}
                <span className="partners-faq__icon" aria-hidden>
                  {isOpen ? '−' : '+'}
                </span>
              </button>
              {isOpen && <p className="partners-faq__body">{item.a}</p>}
            </div>
          );
        })}
      </div>

      <div className="partners-final">
        <a href="#lead" className="partners-hero__cta">
          {t('partners.ctaJoin')}
          <ArrowRight size={18} />
        </a>
      </div>
    </section>
  );
}
