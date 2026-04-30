import { useState, useRef, useEffect, useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { motion, AnimatePresence } from 'framer-motion';
import type { Variants } from 'framer-motion';
import { ReactLenis } from 'lenis/react';
import { Canvas, useFrame } from '@react-three/fiber';
import { Points, PointMaterial } from '@react-three/drei';
import * as THREE from 'three';
import {
  ArrowRight, CheckCircle2,
  MapPin, Play, Users, BarChart3, 
  ShieldCheck, Eye, Zap, Layers, Plus
} from 'lucide-react';
import { submitPartnerApplication } from '../../../api/partners';
import type { PartnerApplicationData } from '../../../api/partners';
import './PartnerLandingPage.css';

/* ── 3D PARTICLE GALAXY ── */
function ParticleGalaxy() {
  const ref = useRef<THREE.Points>(null);
  
  // Generate a spherical distribution of particles
  const [positions, colors] = useMemo(() => {
    const count = 3000;
    const pos = new Float32Array(count * 3);
    const cols = new Float32Array(count * 3);
    
    const colorRed = new THREE.Color("#ff003c");
    const colorWhite = new THREE.Color("#ffffff");
    
    for (let i = 0; i < count; i++) {
      // Spherical distribution
      const r = 10 * Math.cbrt(Math.random());
      const theta = Math.random() * 2 * Math.PI;
      const phi = Math.acos(2 * Math.random() - 1);
      
      const x = r * Math.sin(phi) * Math.cos(theta);
      const y = r * Math.sin(phi) * Math.sin(theta);
      const z = r * Math.cos(phi);
      
      pos[i * 3] = x;
      pos[i * 3 + 1] = y;
      pos[i * 3 + 2] = z;
      
      // Mix colors based on distance or random
      const mixRatio = Math.random();
      const mixedColor = colorWhite.clone().lerp(colorRed, mixRatio > 0.8 ? 1 : 0); // 20% red particles
      
      cols[i * 3] = mixedColor.r;
      cols[i * 3 + 1] = mixedColor.g;
      cols[i * 3 + 2] = mixedColor.b;
    }
    return [pos, cols];
  }, []);

  useFrame((_, delta) => {
    if (ref.current) {
      // Slow constant rotation
      ref.current.rotation.x -= delta / 10;
      ref.current.rotation.y -= delta / 15;
      
      // Scroll-based Z movement to feel like traveling through space
      const scrollY = window.scrollY;
      ref.current.position.z = (scrollY * 0.005) % 10;
    }
  });

  return (
    <group rotation={[0, 0, Math.PI / 4]}>
      <Points ref={ref} positions={positions} colors={colors} stride={3} frustumCulled={false}>
        <PointMaterial 
          transparent 
          vertexColors 
          size={0.05} 
          sizeAttenuation={true} 
          depthWrite={false} 
          blending={THREE.AdditiveBlending}
        />
      </Points>
    </group>
  );
}

/* ── NAVBAR ── */
function NavBar() {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 50);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <nav className={`nav-premium ${scrolled ? 'scrolled' : ''}`}>
      <div className="mx nav-inner">
        <a href="/" className="nav-logo">
          TopDim
        </a>
        <div className="hidden md:flex gap-4">
          <button className="btn-outline" onClick={() => document.getElementById('steps')?.scrollIntoView({ behavior: 'smooth' })}>
            Алгоритм
          </button>
          <button className="btn-primary" onClick={() => document.getElementById('lead')?.scrollIntoView({ behavior: 'smooth' })}>
            Стать партнёром
          </button>
        </div>
      </div>
    </nav>
  );
}

/* ── DATA ── */
const cities = ['Ташкент', 'Самарканд', 'Бухара', 'Наманган', 'Андижан', 'Фергана', 'Нукус', 'Хива'];
const bizCats = ['Кафе / Ресторан', 'Beauty / SPA', 'Фитнес', 'Образование', 'Развлечения', 'Магазин', 'Услуги'];

const schema = z.object({
  name: z.string().trim().min(2, 'Укажите имя'),
  phone: z.string().trim().regex(/^\+?998\s?\d{2}\s?\d{3}\s?\d{2}\s?\d{2}$/, 'Формат: +998 XX XXX XX XX'),
  city: z.string().trim().min(1, 'Выберите город'),
  businessCategory: z.string().trim().min(1, 'Выберите категорию'),
  businessName: z.string().trim().optional().or(z.literal('')),
  comment: z.string().trim().optional().or(z.literal('')),
});
type FormValues = z.infer<typeof schema>;

// Blur reveal animation variant
const blurReveal: Variants = {
  hidden: { opacity: 0, y: 30, filter: "blur(20px)" },
  visible: { opacity: 1, y: 0, filter: "blur(0px)", transition: { duration: 1.2, ease: [0.16, 1, 0.3, 1] } }
};
const staggerChildren: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { staggerChildren: 0.15 } }
};

/* ── MAIN COMPONENT ── */
export default function PartnerLandingPage() {
  return (
    <ReactLenis root options={{ lerp: 0.05, duration: 2, smoothWheel: true }}>
      <div className="canvas-container">
        <Canvas camera={{ position: [0, 0, 5], fov: 60 }} dpr={[1, 2]}>
          <ParticleGalaxy />
        </Canvas>
      </div>

      <NavBar />

      <div className="premium-lp">
        <HeroSection />
        <ImpactMarquee />
        <CategoriesSection />
        <StorySection />
        <BentoSection />
        <FormSection />
        <FaqSection />
      </div>
    </ReactLenis>
  );
}

/* ── 1. HERO ── */
function HeroSection() {
  return (
    <section className="spacer-section" style={{ minHeight: '100vh' }}>
      <div className="mx">
        <motion.div className="hero-content" variants={staggerChildren} initial="hidden" animate="visible">
          <motion.h1 className="hero-title" variants={blurReveal}>
            Будущее<br/>локального бизнеса
          </motion.h1>
          
          <motion.p className="hero-desc" variants={blurReveal}>
            TopDim объединяет лучшие предложения города в одном месте. Мы приводим клиентов, которые ищут именно вас, без сложных настроек рекламы.
          </motion.p>
          
          <motion.div className="flex gap-6 items-center justify-center" variants={blurReveal}>
            <button className="btn-primary" onClick={() => document.getElementById('lead')?.scrollIntoView({ behavior: 'smooth' })}>
              Стать партнёром <ArrowRight size={18} />
            </button>
            <button className="btn-outline" onClick={() => document.getElementById('steps')?.scrollIntoView({ behavior: 'smooth' })}>
              Как это работает <Play size={18} />
            </button>
          </motion.div>
        </motion.div>
      </div>
    </section>
  );
}

/* ── 2. MARQUEE ── */
function ImpactMarquee() {
  const items = ['COUPONS', 'LOCAL DEALS', 'NEW CLIENTS', 'PIN QR', 'TOPDIM', 'COUPONS', 'LOCAL DEALS'];
  return (
    <div className="marquee-wrapper">
      <motion.div className="marquee-track" animate={{ x: [0, -2000] }} transition={{ repeat: Infinity, duration: 30, ease: "linear" }}>
        {items.map((item, i) => <div key={i} className="m-text">{item}</div>)}
      </motion.div>
    </div>
  );
}

/* ── 3. CATEGORIES ── */
function CategoriesSection() {
  const cats = [
    { n: 'Кафе / Рестораны', i: Zap }, { n: 'Салоны красоты', i: Eye },
    { n: 'Фитнес-клубы', i: Users }, { n: 'Магазины', i: MapPin },
    { n: 'Развлечения', i: Play }, { n: 'Услуги', i: Layers }
  ];
  return (
    <section className="spacer-section">
      <div className="mx w-full">
        <motion.div initial="hidden" whileInView="visible" viewport={{ once: true, margin: "-100px" }} variants={blurReveal}>
          <h2 className="sec-title">Для кого <span>TopDim</span></h2>
          <p className="sec-desc">Наша платформа адаптирована для локального бизнеса любых форматов.</p>
        </motion.div>
        
        <motion.div className="cat-grid" variants={staggerChildren} initial="hidden" whileInView="visible" viewport={{ once: true, margin: "-100px" }}>
          {cats.map((c, i) => {
            const Icon = c.i;
            return (
              <motion.div key={i} variants={blurReveal} className="cat-card">
                <div className="cat-icon"><Icon size={36} strokeWidth={1.5} /></div>
                <h3>{c.n}</h3>
              </motion.div>
            );
          })}
        </motion.div>
      </div>
    </section>
  );
}

/* ── 4. STORY ── */
function StorySection() {
  const stepsData = [
    { t: 'Оставьте заявку', d: 'Заполнение формы занимает 1 минуту. Мы свяжемся с вами в тот же день.' },
    { t: 'Настройка акции', d: 'Вместе придумываем сочный оффер: скидка, подарок или 2 по цене 1.' },
    { t: 'Купон в эфире', d: 'Ваше предложение видят тысячи людей в вашем городе через приложение TopDim.' },
    { t: 'Поток клиентов', d: 'Гости приходят и показывают QR или говорят PIN-код кассиру для погашения.' },
  ];

  return (
    <section className="spacer-section" id="steps">
      <div className="mx w-full">
        <div className="story-wrap">
          <motion.div className="story-l" initial="hidden" whileInView="visible" viewport={{ once: true }} variants={blurReveal}>
            <h2 className="sec-title">Алгоритм<br/>Успеха</h2>
            <p className="sec-desc mt-6">Весь путь от первого контакта до потока новых клиентов занимает минимум вашего времени.</p>
          </motion.div>
          <div className="story-r">
            {stepsData.map((s, i) => (
              <motion.div 
                key={i} 
                className="step-item"
                initial={{ opacity: 0, y: 50, filter: "blur(10px)" }}
                whileInView={{ opacity: 1, y: 0, filter: "blur(0px)" }}
                viewport={{ once: true, margin: "-20%" }}
                transition={{ duration: 0.8 }}
              >
                <div className="step-num">0{i+1}</div>
                <div>
                  <h3>{s.t}</h3>
                  <p>{s.d}</p>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

/* ── 5. BENTO ── */
function BentoSection() {
  return (
    <section className="spacer-section">
      <div className="mx w-full">
        <motion.div initial="hidden" whileInView="visible" viewport={{ once: true }} variants={blurReveal}>
          <h2 className="sec-title">Аналитика и Выгода</h2>
        </motion.div>
        
        <motion.div className="bento" variants={staggerChildren} initial="hidden" whileInView="visible" viewport={{ once: true, margin: "-10%" }}>
          <motion.div className="b-card wide" variants={blurReveal}>
            <div className="b-icon"><Users size={60} strokeWidth={1} /></div>
            <h3>Новые клиенты</h3>
            <p>Привлекайте аудиторию, которая целенаправленно ищет скидки и новые места в вашем городе.</p>
          </motion.div>
          <motion.div className="b-card tall" variants={blurReveal}>
            <div className="b-icon"><BarChart3 size={60} strokeWidth={1} /></div>
            <h3>Прозрачность</h3>
            <p>Полная аналитика просмотров и использований купонов в реальном времени. Измеряйте ROI без сложных настроек рекламных кабинетов.</p>
          </motion.div>
          <motion.div className="b-card" variants={blurReveal}>
            <div className="b-icon"><MapPin size={60} strokeWidth={1} /></div>
            <h3>Локальность</h3>
            <p>Показываем ваш бизнес людям рядом с вами.</p>
          </motion.div>
          <motion.div className="b-card" variants={blurReveal}>
            <div className="b-icon"><Zap size={60} strokeWidth={1} /></div>
            <h3>Мотивация</h3>
            <p>Ограниченный срок купона стимулирует к быстрой покупке.</p>
          </motion.div>
        </motion.div>
      </div>
    </section>
  );
}

/* ── 6. FORM ── */
function FormSection() {
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm<FormValues>({ resolver: zodResolver(schema) });

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
        comment: values.comment || 'Заявка с Landing Page'
      };
      await submitPartnerApplication(data);
      setSuccess(true);
    } catch {
      alert("Ошибка при отправке. Пожалуйста, попробуйте позже.");
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <section className="spacer-section" id="lead">
      <div className="mx w-full glass-panel">
        <div className="form-hero">
          <div>
            <motion.h2 className="sec-title mb-6" initial="hidden" whileInView="visible" viewport={{ once: true }} variants={blurReveal}>
              Начать <br/>Сотрудничество
            </motion.h2>
            <motion.p className="sec-desc text-xl mb-10" initial="hidden" whileInView="visible" viewport={{ once: true }} variants={blurReveal}>
              Оставьте заявку — мы свяжемся с вами, расскажем условия и поможем подготовить первое предложение.
            </motion.p>
            <div className="flex items-center gap-4 text-dim"><ShieldCheck color="var(--accent-red)"/> Защита данных</div>
          </div>
          
          <div>
            <AnimatePresence mode="wait">
              {success ? (
                <motion.div key="success" initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="text-center py-20">
                  <CheckCircle2 size={80} color="var(--accent-red)" className="mx-auto mb-6" />
                  <h3 className="text-3xl font-display font-bold mb-4">Заявка принята</h3>
                  <p className="text-dim">Мы скоро с вами свяжемся.</p>
                </motion.div>
              ) : (
                <motion.form key="form" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onSubmit={onSubmit} noValidate>
                  <div className="flex gap-6">
                    <div className="f-group w-1/2">
                      <label>Имя</label>
                      <input {...register('name')} placeholder="Азиза" />
                      {errors.name && <span className="text-red-500 text-xs mt-2 block" role="alert">{errors.name.message}</span>}
                    </div>
                    <div className="f-group w-1/2">
                      <label>Телефон</label>
                      <input {...register('phone')} placeholder="+998 XX XXX XX XX" />
                      {errors.phone && <span className="text-red-500 text-xs mt-2 block" role="alert">{errors.phone.message}</span>}
                    </div>
                  </div>
                  
                  <div className="flex gap-6">
                    <div className="f-group w-1/2">
                      <label>Город</label>
                      <select {...register('city')}>
                        <option value="" className="text-black">Выбрать</option>
                        {cities.map(c => <option key={c} value={c} className="text-black">{c}</option>)}
                      </select>
                      {errors.city && <span className="text-red-500 text-xs mt-2 block" role="alert">{errors.city.message}</span>}
                    </div>
                    <div className="f-group w-1/2">
                      <label>Категория</label>
                      <select {...register('businessCategory')}>
                        <option value="" className="text-black">Выбрать</option>
                        {bizCats.map(c => <option key={c} value={c} className="text-black">{c}</option>)}
                      </select>
                      {errors.businessCategory && <span className="text-red-500 text-xs mt-2 block" role="alert">{errors.businessCategory.message}</span>}
                    </div>
                  </div>

                  <div className="f-group">
                    <label>Название бизнеса</label>
                    <input {...register('businessName')} placeholder="Необязательно" />
                  </div>

                  <button type="submit" disabled={submitting} className="btn-submit">
                    {submitting ? 'Отправка...' : 'Отправить'}
                  </button>
                </motion.form>
              )}
            </AnimatePresence>
          </div>
        </div>
      </div>
    </section>
  );
}

/* ── 7. FAQ & CTA ── */
function FaqSection() {
  const [open, setOpen] = useState<number | null>(0);
  const faqs = [
    { q: 'Сколько стоит подключение?', a: 'Условия прозрачны: комиссия только за реально купленные купоны.' },
    { q: 'Нужно ли самому создавать купоны?', a: 'Мы помогаем упаковать оффер и сделать крутой дизайн.' },
    { q: 'Как клиент использует купон?', a: 'Он показывает PIN или QR. Кассир вводит его в кабинете.' },
    { q: 'Подходит ли TopDim для малого бизнеса?', a: 'Да, платформа отлично генерирует локальный трафик.' },
  ];

  return (
    <section className="spacer-section" style={{ flexDirection: 'column' }}>
      <div className="mx w-full mb-40">
        <h2 className="sec-title text-center mb-16">Частые Вопросы</h2>
        <div className="faq-list">
          {faqs.map((f, i) => (
            <div key={i} className="faq-row">
              <button className="faq-head" onClick={() => setOpen(open === i ? null : i)}>
                {f.q}
                <div className={`faq-icon ${open === i ? 'open' : ''}`}><Plus size={24}/></div>
              </button>
              <AnimatePresence>
                {open === i && (
                  <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }} style={{ overflow: 'hidden' }}>
                    <div className="faq-body-inner">{f.a}</div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          ))}
        </div>
      </div>
      
      <div className="mx w-full text-center pb-40">
        <h2 className="final-title">READY TO GROW?</h2>
        <button className="btn-primary" style={{ transform: 'scale(1.2)' }} onClick={() => document.getElementById('lead')?.scrollIntoView({ behavior: 'smooth' })}>
          Стать партнёром
        </button>
      </div>
    </section>
  );
}
