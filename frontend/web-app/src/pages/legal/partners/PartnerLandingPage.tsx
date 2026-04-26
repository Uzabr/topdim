import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  ArrowRight,
  BadgePercent,
  BarChart3,
  Building2,
  CheckCircle2,
  ChevronDown,
  CircleDollarSign,
  ClipboardCheck,
  Coffee,
  Compass,
  Dumbbell,
  GraduationCap,
  HeartHandshake,
  HelpCircle,
  MapPin,
  Menu,
  MessageCircle,
  MoonStar,
  Orbit,
  PackageCheck,
  QrCode,
  Rocket,
  Scissors,
  Send,
  ShieldCheck,
  ShoppingBag,
  Sparkles,
  Store,
  Target,
  TicketCheck,
  TimerReset,
  Users,
  X,
  Zap,
} from 'lucide-react';
import { submitPartnerApplication } from '../../../api/partners';
import type { PartnerApplicationData } from '../../../api/partners';
import './PartnerLandingPage.css';

const cities = ['Ташкент', 'Самарканд', 'Бухара', 'Наманган', 'Андижан', 'Фергана', 'Нукус', 'Хива'];

const businessCategories = [
  'Ресторан',
  'Кафе или кофейня',
  'Beauty',
  'SPA',
  'Фитнес',
  'Развлечения',
  'Учебный центр',
  'Магазин',
  'Сервисная компания',
  'Локальные услуги',
];

const leadSchema = z.object({
  name: z.string().trim().min(2, 'Укажите имя'),
  phone: z.string().trim().min(9, 'Укажите телефон'),
  city: z.string().trim().min(1, 'Выберите город'),
  businessCategory: z.string().trim().min(1, 'Выберите категорию'),
  businessName: z.string().trim().optional().or(z.literal('')),
});

type LeadFormValues = z.infer<typeof leadSchema>;

type LeadFormVariant = 'hero' | 'compact';

const navItems = [
  { href: '#who', label: 'Кто мы' },
  { href: '#problems', label: 'Проблемы' },
  { href: '#solution', label: 'Решение' },
  { href: '#how', label: 'Как работает' },
  { href: '#benefits', label: 'Преимущества' },
  { href: '#fit', label: 'Для кого' },
  { href: '#faq', label: 'FAQ' },
  { href: '#lead', label: 'Заявка' },
];

const trustBadges = [
  { icon: Rocket, label: 'Быстрый запуск' },
  { icon: HeartHandshake, label: 'Менеджер поможет' },
  { icon: QrCode, label: 'PIN/QR погашение' },
  { icon: Store, label: 'Для локального бизнеса' },
];

const problemCards = [
  {
    icon: TimerReset,
    title: 'Мало клиентов в спокойные часы?',
    text: 'В будние дни, утром или после обеда бизнес может терять выручку из-за низкого потока.',
  },
  {
    icon: Users,
    title: 'Клиенты приходят один раз и не возвращаются?',
    text: 'Без понятного предложения людям сложнее вернуться снова и закрепить привычку к вашему месту.',
  },
  {
    icon: CircleDollarSign,
    title: 'Реклама съедает бюджет, а результат неясен?',
    text: 'Не всегда понятно, сколько реальных клиентов приносит продвижение и что именно сработало.',
  },
  {
    icon: ClipboardCheck,
    title: 'Сложно быстро и правильно запустить акцию?',
    text: 'Нужно продумать механику, оформить предложение и показать его нужной аудитории.',
  },
];

const solutionFlow = ['Бизнес', 'Акция', 'Пользователь', 'Купон', 'Визит', 'Повторная покупка'];

const howSteps = [
  {
    title: 'Вы оставляете заявку',
    text: 'Мы связываемся с вами, узнаем формат бизнеса, город и цель акции.',
  },
  {
    title: 'Мы помогаем запустить предложение',
    text: 'Подготавливаем акцию, условия, срок действия, лимиты и механику использования.',
  },
  {
    title: 'Клиенты приходят к вам',
    text: 'Пользователь показывает купон, а вы подтверждаете его по PIN/QR.',
  },
];

const aboutCards = [
  { icon: TicketCheck, title: 'Купоны и акции', text: 'Понятные предложения для клиентов и партнёров.' },
  { icon: MapPin, title: 'Места рядом', text: 'Фокус на локальном спросе и удобстве выбора.' },
  { icon: ShoppingBag, title: 'Базары и магазины', text: 'Постепенно собираем локальную торговлю в единую карту.' },
  { icon: QrCode, title: 'PIN/QR погашение', text: 'Простая проверка купона на месте.' },
  { icon: MessageCircle, title: 'Поддержка менеджера', text: 'Помогаем подготовить первую механику акции.' },
  { icon: Compass, title: 'Рынок Узбекистана', text: 'Строим продукт под привычки локального бизнеса.' },
];

const audienceCards = [
  {
    title: 'Молодые люди',
    text: 'Ищут новые места, любят быстрый выбор и хорошо реагируют на понятную выгоду.',
  },
  {
    title: 'Семьи',
    text: 'Выбирают предложения, где заранее понятно, что они получат и сколько сэкономят.',
  },
  {
    title: 'Пользователи рядом',
    text: 'Могут открыть ваш бизнес через карту, категорию или подборку локальных предложений.',
  },
  {
    title: 'Любители акций и купонов',
    text: 'Следят за выгодными предложениями и готовы пробовать новые места чаще.',
  },
];

const benefitCards = [
  {
    icon: Target,
    title: 'Понятный результат',
    text: 'Купон помогает связать интерес клиента с реальным визитом и погашением.',
  },
  {
    icon: Rocket,
    title: 'Быстрый запуск',
    text: 'Вы оставляете заявку, а команда помогает подготовить первую акцию без сложной настройки.',
  },
  {
    icon: MapPin,
    title: 'Клиенты рядом',
    text: 'TopDim делает упор на локальные предложения, город и удобный выбор рядом.',
  },
  {
    icon: QrCode,
    title: 'Удобное PIN/QR-погашение',
    text: 'Кассиру достаточно проверить код купона, а история использования остаётся в системе.',
  },
  {
    icon: HeartHandshake,
    title: 'Поддержка менеджера',
    text: 'Мы помогаем сформулировать условия, лимиты и сценарий запуска.',
  },
  {
    icon: Zap,
    title: 'Без лишней сложности',
    text: 'На старте не нужно строить отдельный кабинет или команду маркетинга.',
  },
];

const expectationItems = [
  'помощь с первой акцией',
  'оформление предложения',
  'публикация на платформе',
  'понятные условия использования',
  'PIN/QR-погашение',
  'поддержка менеджера',
  'тестирование разных механик',
  'постепенное привлечение новых клиентов',
];

const categoryCards = [
  { icon: Building2, label: 'Рестораны' },
  { icon: Coffee, label: 'Кафе и кофейни' },
  { icon: Scissors, label: 'Beauty' },
  { icon: Sparkles, label: 'SPA' },
  { icon: Dumbbell, label: 'Фитнес' },
  { icon: MoonStar, label: 'Развлечения' },
  { icon: GraduationCap, label: 'Учебные центры' },
  { icon: ShoppingBag, label: 'Магазины' },
  { icon: PackageCheck, label: 'Сервисы' },
  { icon: Store, label: 'Локальные услуги' },
];

const miniScenarios = [
  'Ресторан запускает сет для буднего вечера.',
  'Салон красоты продвигает новую услугу.',
  'Учебный центр предлагает пробное занятие.',
];

const faqs = [
  {
    question: 'Нужно ли создавать аккаунт?',
    answer: 'Нет. Сначала оставьте заявку, мы свяжемся с вами и объясним, какой формат запуска подойдёт вашему бизнесу.',
  },
  {
    question: 'Какие акции можно запускать?',
    answer: 'Скидка на услугу, набор, специальное меню, пробный визит, предложение на спокойные часы или ограниченный купон.',
  },
  {
    question: 'Как клиент использует купон?',
    answer: 'Клиент покупает или сохраняет купон, приходит к вам и показывает PIN/QR. Партнёр подтверждает использование.',
  },
  {
    question: 'Можно ли ограничить количество купонов?',
    answer: 'Да. При подготовке акции можно задать лимит, срок действия и понятные условия использования.',
  },
  {
    question: 'Подходит ли TopDim для небольшого бизнеса?',
    answer: 'Да. Мы как раз делаем формат простым для локальных кафе, салонов, сервисов, магазинов и учебных центров.',
  },
];

function buildPayload(values: LeadFormValues, source: string): PartnerApplicationData {
  const parts = values.name.trim().split(/\s+/);
  const firstName = parts[0] || values.name.trim();
  const lastName = parts.slice(1).join(' ') || '-';
  const companyName = values.businessName?.trim() || `${values.businessCategory}, ${values.city}`;

  return {
    firstName,
    lastName,
    phone: values.phone.trim(),
    companyName,
    city: values.city,
    businessCategory: values.businessCategory,
    comment: `Заявка с партнёрского лендинга TopDim: ${source}`,
  };
}

export default function PartnerLandingPage() {
  return (
    <div className="td-partner">
      <BackgroundScene />
      <Header />
      <main>
        <HeroSection />
        <ProblemsSection />
        <SolutionSection />
        <HowItWorksSection />
        <LeadCTASection />
        <AboutSection />
        <AudienceSection />
        <BenefitsSection />
        <ExpectationsSection />
        <ExamplePromoSection />
        <BusinessCategoriesSection />
        <FAQSection />
        <FinalCTASection />
      </main>
      <Footer />
      <a className="td-partner__mobile-cta" href="#lead">
        Оставить заявку
      </a>
    </div>
  );
}

function BackgroundScene() {
  return (
    <div className="td-partner-bg" aria-hidden="true">
      <span className="td-orb td-orb--one" />
      <span className="td-orb td-orb--two" />
      <span className="td-orb td-orb--three" />
      <span className="td-premium-layer td-premium-layer--map" />
      <span className="td-premium-layer td-premium-layer--card" />
      <span className="td-premium-layer td-premium-layer--phone" />
      <span className="td-premium-layer td-premium-layer--ring" />
      <span className="td-ring td-ring--one" />
      <span className="td-ring td-ring--two" />
      <span className="td-ribbon td-ribbon--one" />
      <span className="td-ribbon td-ribbon--two" />
      <span className="td-particle td-particle--one" />
      <span className="td-particle td-particle--two" />
      <span className="td-particle td-particle--three" />
    </div>
  );
}

function Header() {
  const [isOpen, setIsOpen] = useState(false);

  const closeMenu = () => setIsOpen(false);

  return (
    <header className="td-header">
      <a className="td-logo" href="#top" onClick={closeMenu}>
        <span>TopDim</span>
        <small>partners</small>
      </a>
      <nav className={`td-nav ${isOpen ? 'td-nav--open' : ''}`} aria-label="Партнёрская навигация">
        {navItems.map((item) => (
          <a key={item.href} href={item.href} onClick={closeMenu}>
            {item.label}
          </a>
        ))}
      </nav>
      <a className="td-header__cta" href="#lead" onClick={closeMenu}>
        Стать партнёром
      </a>
      <button
        aria-expanded={isOpen}
        aria-label={isOpen ? 'Закрыть меню' : 'Открыть меню'}
        className="td-menu-button"
        type="button"
        onClick={() => setIsOpen((value) => !value)}
      >
        {isOpen ? <X size={22} /> : <Menu size={22} />}
      </button>
    </header>
  );
}

function HeroSection() {
  return (
    <section className="td-hero" id="top">
      <div className="td-hero__copy">
        <p className="td-eyebrow">
          <Sparkles size={16} />
          Партнёрская программа TopDim
        </p>
        <h1>
          Приводите новых клиентов через <span>купоны TopDim</span>
        </h1>
        <p className="td-hero__lead">
          Запускайте акции, показывайте ваш бизнес людям рядом и принимайте купоны по PIN/QR — без сложной настройки и лишних рисков.
        </p>
        <p className="td-hero__emotion">
          Откройте новую вселенную возможностей для вашего бизнеса вместе с TopDim.
        </p>
        <div className="td-hero__actions">
          <a className="td-button td-button--primary" href="#lead">
            Оставить заявку
            <ArrowRight size={18} />
          </a>
          <a className="td-button td-button--secondary" href="#how">
            Как это работает
          </a>
        </div>
        <div className="td-trust-badges" aria-label="Почему стоит попробовать TopDim">
          {trustBadges.map(({ icon: Icon, label }) => (
            <span key={label}>
              <Icon size={16} />
              {label}
            </span>
          ))}
        </div>
      </div>

      <div className="td-hero__visual">
        <div className="td-portal-card" aria-hidden="true">
          <div className="td-portal-card__orbit">
            <Orbit size={120} />
          </div>
          <div className="td-floating-card td-floating-card--coupon">
            <span>Купон дня</span>
            <strong>-25%</strong>
            <p>Кофе + десерт</p>
          </div>
          <div className="td-floating-card td-floating-card--pin">
            <QrCode size={30} />
            <div>
              <span>PIN</span>
              <strong>4829</strong>
            </div>
          </div>
          <div className="td-floating-card td-floating-card--growth">
            <BarChart3 size={22} />
            <p>Новый канал роста</p>
          </div>
        </div>
        <LeadForm id="hero-lead-form" source="hero" title="Получите консультацию" variant="hero" />
      </div>
    </section>
  );
}

function LeadForm({ id, source, title, variant }: { id: string; source: string; title: string; variant: LeadFormVariant }) {
  const [submitted, setSubmitted] = useState(false);
  const [serverError, setServerError] = useState('');
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    reset,
  } = useForm<LeadFormValues>({
    resolver: zodResolver(leadSchema),
    mode: 'onBlur',
    defaultValues: {
      name: '',
      phone: '',
      city: '',
      businessCategory: '',
      businessName: '',
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setServerError('');
    try {
      await submitPartnerApplication(buildPayload(values, source));
      setSubmitted(true);
      reset();
    } catch (error: unknown) {
      const axiosError = error as { response?: { data?: { message?: string } } };
      setServerError(axiosError.response?.data?.message || 'Не получилось отправить заявку. Попробуйте ещё раз.');
    }
  });

  if (submitted) {
    return (
      <div className={`td-lead-form td-lead-form--success td-lead-form--${variant}`} id={id}>
        <CheckCircle2 size={42} />
        <h3>Заявка принята!</h3>
        <p>Спасибо, мы свяжемся с вами в ближайшее время и подскажем лучший формат акции.</p>
        <button className="td-button td-button--secondary" type="button" onClick={() => setSubmitted(false)}>
          Оставить ещё заявку
        </button>
      </div>
    );
  }

  return (
    <form className={`td-lead-form td-lead-form--${variant}`} id={id} onSubmit={onSubmit} noValidate>
      <div className="td-lead-form__header">
        <span>
          <Send size={18} />
        </span>
        <div>
          <h3>{title}</h3>
          <p>Мы подскажем, какой формат акции лучше подойдёт вашему бизнесу.</p>
        </div>
      </div>

      <div className="td-field-grid">
        <label className="td-field">
          <span>Ваше имя</span>
          <input placeholder="Например, Азиза" autoComplete="name" {...register('name')} />
          {errors.name && <small>{errors.name.message}</small>}
        </label>

        <label className="td-field">
          <span>Номер телефона</span>
          <input placeholder="+998 90 123 45 67" autoComplete="tel" inputMode="tel" {...register('phone')} />
          {errors.phone && <small>{errors.phone.message}</small>}
        </label>

        <label className="td-field">
          <span>Город</span>
          <select {...register('city')}>
            <option value="">Выберите город</option>
            {cities.map((city) => (
              <option key={city} value={city}>
                {city}
              </option>
            ))}
          </select>
          {errors.city && <small>{errors.city.message}</small>}
        </label>

        <label className="td-field">
          <span>Категория бизнеса</span>
          <select {...register('businessCategory')}>
            <option value="">Выберите категорию</option>
            {businessCategories.map((category) => (
              <option key={category} value={category}>
                {category}
              </option>
            ))}
          </select>
          {errors.businessCategory && <small>{errors.businessCategory.message}</small>}
        </label>
      </div>

      <label className="td-field">
        <span>
          Название бизнеса <em>необязательно</em>
        </span>
        <input placeholder="Например, Ali Cafe" autoComplete="organization" {...register('businessName')} />
      </label>

      {serverError && <p className="td-form-error">{serverError}</p>}

      <button className="td-button td-button--primary td-lead-form__button" type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Отправляем...' : variant === 'compact' ? 'Отправить заявку' : 'Получить консультацию'}
      </button>

      <p className="td-lead-form__note">
        Мы свяжемся с вами и подскажем, какой формат акции лучше подойдёт вашему бизнесу.
      </p>
    </form>
  );
}

function ProblemsSection() {
  return (
    <section className="td-section" id="problems">
      <SectionHeading eyebrow="Проблемы бизнеса" title="Есть проблемы, которые мешают бизнесу расти?" icon={HelpCircle} />
      <div className="td-card-grid td-card-grid--four">
        {problemCards.map(({ icon: Icon, title, text }) => (
          <article className="td-info-card td-info-card--problem" key={title}>
            <span className="td-card-icon">
              <Icon size={24} />
            </span>
            <h3>{title}</h3>
            <p>{text}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

function SolutionSection() {
  return (
    <section className="td-section td-solution" id="solution">
      <div className="td-solution__copy">
        <p className="td-eyebrow">
          <Orbit size={16} />
          Решение от TopDim
        </p>
        <h2>TopDim помогает запускать акции и приводить клиентов без лишних рисков</h2>
        <p>
          Вы размещаете выгодное предложение, пользователи находят его в TopDim, покупают или сохраняют купон, приходят к вам,
          а вы подтверждаете использование через PIN/QR.
        </p>
        <strong>Вы получаете не просто просмотры, а понятный канал привлечения клиентов.</strong>
      </div>
      <div className="td-solution-flow" aria-label="Путь купона от бизнеса к клиенту">
        {solutionFlow.map((item, index) => (
          <div className="td-flow-step" key={item}>
            <span>{index + 1}</span>
            <p>{item}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

function HowItWorksSection() {
  return (
    <section className="td-section" id="how">
      <SectionHeading
        eyebrow="Как это работает"
        title="Три понятных шага до первой акции"
        text="Мы убираем лишнюю сложность: сначала короткая заявка, потом помощь с механикой, затем запуск и приём клиентов."
        icon={Rocket}
      />
      <div className="td-steps">
        {howSteps.map((step, index) => (
          <article className="td-step-card" key={step.title}>
            <span>{index + 1}</span>
            <h3>{step.title}</h3>
            <p>{step.text}</p>
          </article>
        ))}
      </div>
      <div className="td-centered-action">
        <a className="td-button td-button--primary" href="#lead">
          Запустить акцию
          <ArrowRight size={18} />
        </a>
      </div>
    </section>
  );
}

function LeadCTASection() {
  return (
    <section className="td-section td-lead-cta" id="lead">
      <div className="td-lead-cta__copy">
        <p className="td-eyebrow">
          <Send size={16} />
          Оставить заявку
        </p>
        <h2>Начните принимать новых клиентов через TopDim</h2>
        <p>Оставьте заявку — мы расскажем, как запустить акцию именно для вашего бизнеса.</p>
      </div>
      <LeadForm id="lead-form" source="middle-cta" title="Заявка на партнёрство" variant="compact" />
    </section>
  );
}

function AboutSection() {
  return (
    <section className="td-section" id="who">
      <SectionHeading
        eyebrow="Кто мы"
        title="TopDim — платформа локальных купонов и выгодных предложений"
        text="Мы создаём удобную платформу для жителей Узбекистана, где можно находить скидки, купоны, места рядом, магазины, базары и услуги. Для бизнеса TopDim становится понятным каналом привлечения клиентов и запуска специальных предложений."
        icon={Compass}
      />
      <div className="td-card-grid td-card-grid--three">
        {aboutCards.map(({ icon: Icon, title, text }) => (
          <article className="td-info-card" key={title}>
            <span className="td-card-icon">
              <Icon size={23} />
            </span>
            <h3>{title}</h3>
            <p>{text}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

function AudienceSection() {
  return (
    <section className="td-section td-audience">
      <SectionHeading
        eyebrow="Наша аудитория"
        title="Кого вы сможете привлечь через TopDim?"
        text="TopDim ориентирован на людей, которые ищут выгодные предложения, новые места и удобные сервисы рядом с собой."
        icon={Users}
      />
      <div className="td-card-grid td-card-grid--four">
        {audienceCards.map((card) => (
          <article className="td-info-card td-info-card--audience" key={card.title}>
            <h3>{card.title}</h3>
            <p>{card.text}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

function BenefitsSection() {
  return (
    <section className="td-section" id="benefits">
      <SectionHeading eyebrow="Почему партнёру выгодно" title="TopDim делает запуск акции понятным и управляемым" icon={HeartHandshake} />
      <div className="td-card-grid td-card-grid--three">
        {benefitCards.map(({ icon: Icon, title, text }) => (
          <article className="td-info-card" key={title}>
            <span className="td-card-icon">
              <Icon size={23} />
            </span>
            <h3>{title}</h3>
            <p>{text}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

function ExpectationsSection() {
  return (
    <section className="td-section td-expectations">
      <div>
        <p className="td-eyebrow">
          <ShieldCheck size={16} />
          Что от нас ожидать
        </p>
        <h2>Что вы получите после подключения?</h2>
        <p>
          Мы не оставляем бизнес один на один с настройками. На MVP TopDim помогает подготовить первую акцию и сделать запуск спокойным.
        </p>
      </div>
      <div className="td-expectations__list">
        {expectationItems.map((item) => (
          <span key={item}>
            <CheckCircle2 size={18} />
            {item}
          </span>
        ))}
      </div>
    </section>
  );
}

function ExamplePromoSection() {
  return (
    <section className="td-section td-example">
      <div className="td-example__copy">
        <p className="td-eyebrow">
          <BadgePercent size={16} />
          Пример сценария
        </p>
        <h2>Как может выглядеть акция в TopDim</h2>
        <p>
          Кофейня хочет загрузить спокойные часы с 14:00 до 17:00 и запускает купон: кофе + десерт со скидкой 25%.
        </p>
        <div className="td-example__points">
          <span><strong>Цель</strong> загрузить тихие часы</span>
          <span><strong>Механика</strong> ограниченный купон на сет</span>
          <span><strong>Погашение</strong> PIN/QR на месте</span>
          <span><strong>Эффект</strong> больше поводов прийти</span>
        </div>
      </div>
      <div className="td-example-card" aria-hidden="true">
        <span>TopDim coupon</span>
        <strong>-25%</strong>
        <p>Кофе + десерт</p>
        <small>14:00-17:00 · будние дни</small>
      </div>
      <div className="td-mini-scenarios">
        {miniScenarios.map((scenario) => (
          <span key={scenario}>{scenario}</span>
        ))}
      </div>
    </section>
  );
}

function BusinessCategoriesSection() {
  return (
    <section className="td-section" id="fit">
      <SectionHeading eyebrow="Для кого подходит" title="Для локального бизнеса, которому важны новые визиты" icon={Store} />
      <div className="td-categories">
        {categoryCards.map(({ icon: Icon, label }) => (
          <span key={label}>
            <Icon size={19} />
            {label}
          </span>
        ))}
      </div>
    </section>
  );
}

function FAQSection() {
  return (
    <section className="td-section" id="faq">
      <SectionHeading eyebrow="FAQ" title="Частые вопросы партнёров" icon={MessageCircle} />
      <div className="td-faq-list">
        {faqs.map((item) => (
          <details key={item.question}>
            <summary>
              <span>{item.question}</span>
              <ChevronDown size={18} />
            </summary>
            <p>{item.answer}</p>
          </details>
        ))}
      </div>
    </section>
  );
}

function FinalCTASection() {
  return (
    <section className="td-section td-final-cta">
      <div className="td-final-cta__copy">
        <p className="td-eyebrow">
          <Sparkles size={16} />
          Новая орбита роста
        </p>
        <h2>Готовы открыть новые возможности для вашего бизнеса?</h2>
        <p>Оставьте заявку — и TopDim поможет вам сделать первый шаг к новой аудитории и новым продажам.</p>
      </div>
      <LeadForm id="final-lead-form" source="final-cta" title="Сделать первый шаг" variant="compact" />
    </section>
  );
}

function Footer() {
  return (
    <footer className="td-footer">
      <div>
        <a className="td-logo" href="#top">
          <span>TopDim</span>
          <small>partners</small>
        </a>
        <p>Платформа локальных купонов, скидок и выгодных предложений для бизнеса в Узбекистане.</p>
      </div>
      <nav aria-label="Футер партнёрской страницы">
        {navItems.slice(0, 6).map((item) => (
          <a key={item.href} href={item.href}>
            {item.label}
          </a>
        ))}
      </nav>
      <div className="td-footer__contacts">
        <span>Telegram: @topdim_partner</span>
        <span>Телефон: +998 XX XXX XX XX</span>
        <span>Email: partners@topdim.uz</span>
        <span>Узбекистан</span>
      </div>
    </footer>
  );
}

function SectionHeading({ eyebrow, title, text, icon: Icon }: { eyebrow: string; title: string; text?: string; icon?: React.ElementType }) {
  return (
    <div className="td-section-heading">
      <p className="td-eyebrow">
        {Icon && <Icon size={16} />}
        {eyebrow}
      </p>
      <h2>{title}</h2>
      {text && <p>{text}</p>}
    </div>
  );
}
