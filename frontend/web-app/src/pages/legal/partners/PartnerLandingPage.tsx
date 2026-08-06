import { useState, useRef, useEffect, useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { motion, AnimatePresence, useScroll, useTransform, useMotionValueEvent } from 'framer-motion';
import type { Variants } from 'framer-motion';
import { ReactLenis } from 'lenis/react';
import { Canvas, useFrame } from '@react-three/fiber';
import { Points, PointMaterial } from '@react-three/drei';
import * as THREE from 'three';
import {
  ArrowRight, CheckCircle2,
  MapPin, Play, Users, BarChart3, 
  ShieldCheck, Eye, Zap, Layers, Plus, ChevronDown
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { submitPartnerApplication } from '../../../api/partners';
import type { PartnerApplicationData } from '../../../api/partners';
import './PartnerLandingPage.css';

/* ── PARTICLE DATA (generated once at module load, not during render) ── */
function generateParticleData() {
  const count = 3000;
  const pos = new Float32Array(count * 3);
  const cols = new Float32Array(count * 3);

  // Корпоративные цвета
  const colorPrimary = new THREE.Color("#2563eb");   // синий
  const colorSecondary = new THREE.Color("#10b981"); // зелёный
  const colorAccent = new THREE.Color("#f59e0b");    // акцент

  for (let i = 0; i < count; i++) {
    const r = 10 * Math.cbrt(Math.random());
    const theta = Math.random() * 2 * Math.PI;
    const phi = Math.acos(2 * Math.random() - 1);

    pos[i * 3]     = r * Math.sin(phi) * Math.cos(theta);
    pos[i * 3 + 1] = r * Math.sin(phi) * Math.sin(theta);
    pos[i * 3 + 2] = r * Math.cos(phi);

    // Градиент между цветами с вариативностью
    const rand = Math.random();
    let mixedColor;
    if (rand < 0.6) {
      // 60% — основной синий с вариациями
      mixedColor = colorPrimary.clone().lerp(colorSecondary, Math.random() * 0.3);
    } else if (rand < 0.9) {
      // 30% — вторичный зелёный
      mixedColor = colorSecondary.clone().lerp(colorPrimary, Math.random() * 0.2);
    } else {
      // 10% — акцентный янтарный для глубины
      mixedColor = colorAccent.clone();
    }

    cols[i * 3]     = mixedColor.r;
    cols[i * 3 + 1] = mixedColor.g;
    cols[i * 3 + 2] = mixedColor.b;
  }
  return [pos, cols] as const;
}

const PARTICLE_DATA = generateParticleData();

/* ── 3D PARTICLE GALAXY ── */

function ParticleGalaxy() {
  const ref = useRef<THREE.Points>(null);
  
  const [positions, colors] = useMemo(() => PARTICLE_DATA, []);

  useFrame((_, delta) => {
    if (ref.current) {
      ref.current.rotation.x -= delta / 10;
      ref.current.rotation.y -= delta / 15;
      
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
  const { t } = useTranslation();
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
          sizbiz
        </a>
        <div className="hidden md:flex gap-8" style={{ display: 'flex', gap: '8px'}}>
          <a href="#steps" className="btn-outline" style={{ textDecoration: 'none' }}>
            {t('partners.navAlgorithm')}
          </a>
          <a href="#lead" className="btn-primary" style={{ textDecoration: 'none' }}>
            {t('partners.navJoin')}
          </a>
        </div>
      </div>
    </nav>
  );
}

type FormValues = {
  name: string;
  phone: string;
  city: string;
  businessCategory: string;
  businessName?: string;
  comment?: string;
};

/* ── CUSTOM SELECT ── */
function CustomSelect({ options, value, onChange, placeholder }: { options: string[], value: string, onChange: (val: string) => void, placeholder: string }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div className="custom-select-wrap" ref={ref}>
      <div className={`custom-select-trigger ${open ? 'open' : ''} ${value ? 'has-value' : ''}`} onClick={() => setOpen(!open)}>
        <span>{value || placeholder}</span>
        <ChevronDown size={20} style={{ transform: open ? 'rotate(180deg)' : 'rotate(0deg)', transition: 'transform 0.3s' }} />
      </div>
      <AnimatePresence>
        {open && (
          <motion.ul 
            className="custom-select-menu"
            data-lenis-prevent="true"
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            transition={{ duration: 0.2 }}
          >
            {options.map((opt) => (
              <li 
                key={opt} 
                className={value === opt ? 'selected' : ''}
                onClick={() => { onChange(opt); setOpen(false); }}
              >
                {opt}
              </li>
            ))}
          </motion.ul>
        )}
      </AnimatePresence>
    </div>
  );
}

// Blur reveal animation variant
const blurReveal: Variants = {
  hidden: { opacity: 0, y: 30, filter: "blur(20px)" },
  visible: { opacity: 1, y: 0, filter: "blur(0px)", transition: { duration: 1.2, ease: [0.16, 1, 0.3, 1] } }
};
const staggerChildren: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { staggerChildren: 0.15 } }
};

/* ── INTERACTIVE BENTO CARD ── */
function BentoCard({ children, className = "" }: { children: React.ReactNode, className?: string }) {
  const ref = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [opacity, setOpacity] = useState(0);

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!ref.current) return;
    const rect = ref.current.getBoundingClientRect();
    setPosition({ x: e.clientX - rect.left, y: e.clientY - rect.top });
  };

  return (
    <motion.div 
      ref={ref}
      className={`b-card ${className}`}
      onMouseMove={handleMouseMove}
      onMouseEnter={() => setOpacity(1)}
      onMouseLeave={() => setOpacity(0)}
      variants={blurReveal}
    >
      <div 
        className="b-card-spotlight" 
        style={{ 
          opacity,
          background: `radial-gradient(600px circle at ${position.x}px ${position.y}px, rgba(255, 0, 60, 0.12), transparent 40%)`
        }} 
      />
      <div className="b-card-content">
        {children}
      </div>
    </motion.div>
  );
}

/* ── MAIN COMPONENT ── */
export default function PartnerLandingPage() {
  useEffect(() => {
    // TopDim's global index.css sets overflow-x: hidden on body, which breaks position: sticky.
    // We temporarily remove it for this page so the Algorithm section works correctly.
    const originalOverflow = document.body.style.overflowX;
    document.body.style.overflowX = 'visible';
    return () => {
      document.body.style.overflowX = originalOverflow;
    };
  }, []);

  return (
    <ReactLenis root options={{ lerp: 0.05, duration: 2, smoothWheel: true }}>
      <div className="partner-page-root">
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
      </div>
    </ReactLenis>
  );
}

/* ── 1. HERO ── */
function HeroSection() {
  const { t } = useTranslation();
  const { scrollY } = useScroll();
  const y = useTransform(scrollY, [0, 800], [0, 200]); // текст уходит вверх на 200px при скролле 800px
  const opacity = useTransform(scrollY, [0, 600], [1, 0]); // плавное исчезновение 

  return (
    <section className="spacer-section" style={{ minHeight: '100vh' }}>
      <div className="mx">
        <motion.div 
          className="hero-content" 
          variants={staggerChildren} 
          initial="hidden" 
          animate="visible"
          style={{ y, opacity }}>
          <motion.h1 className="hero-title" variants={blurReveal}>
            {t('partners.heroTitleLine1')}<br/>{t('partners.heroTitleLine2')}
          </motion.h1>
          
          <motion.p className="hero-desc" variants={blurReveal}>
            {t('partners.heroDesc')}
          </motion.p>
          
          <motion.div className="flex flex-row gap-8 items-center justify-center" variants={blurReveal} style={{ display: 'flex', gap: '8px', justifyContent: 'center'}}>
            <a href="#lead" className="btn-primary inline-flex items-center justify-center gap-[10px]" style={{ textDecoration: 'none' }}>
              {t('partners.ctaJoin')} <ArrowRight size={18} />
            </a>
            <a href="#steps" className="btn-outline inline-flex items-center justify-center gap-[10px]" style={{ textDecoration: 'none' }}>
              <span>{t('partners.howItWorks')}</span>
            </a>
          </motion.div>
        </motion.div>
      </div>
    </section>
  );
}

/* ── 2. MARQUEE ── */
function ImpactMarquee() {
  const items = ['COUPONS', 'LOCAL DEALS', 'NEW CLIENTS', 'PIN QR', 'SIZBIZ', 'COUPONS', 'LOCAL DEALS'];
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
  const { t } = useTranslation();
  const cats = [
    { n: t('partners.audience.cafe'), i: Zap }, { n: t('partners.audience.beauty'), i: Eye },
    { n: t('partners.audience.fitness'), i: Users }, { n: t('partners.audience.shops'), i: MapPin },
    { n: t('partners.audience.entertainment'), i: Play }, { n: t('partners.audience.services'), i: Layers }
  ];
  return (
    <section className="spacer-section">
      <div className="mx w-full">
        <motion.div initial="hidden" whileInView="visible" viewport={{ once: true, margin: "-100px" }} variants={blurReveal}>
          <h2 className="sec-title">{t('partners.audienceTitle')} <span>sizbiz</span></h2>
          <p className="sec-desc">{t('partners.audienceDesc')}</p>
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

/* ── 4. STORY (PINNED SCROLL) ── */
function StorySection() {
  const { t } = useTranslation();
  const containerRef = useRef(null);
  const { scrollYProgress } = useScroll({ target: containerRef, offset: ["start start", "end end"] });

  const activeStep = useTransform(scrollYProgress, (p: number) => {
    if (p < 0.25) return 0;
    if (p < 0.5) return 1;
    if (p < 0.75) return 2;
    return 3;
  });

  const [current, setCurrent] = useState(0);

  useMotionValueEvent(activeStep, "change", (latest: number) => {
    setCurrent(latest);
  });

  const stepsData = [
    { title: t('partners.steps.apply.title'), desc: t('partners.steps.apply.desc') },
    { title: t('partners.steps.setup.title'), desc: t('partners.steps.setup.desc') },
    { title: t('partners.steps.live.title'), desc: t('partners.steps.live.desc') },
    { title: t('partners.steps.clients.title'), desc: t('partners.steps.clients.desc') },
  ];

  return (
    <section className="story-wrap-pinned" ref={containerRef} id="steps">
      <div className="story-sticky">
        <div className="story-grid">
          
          <div className="story-l-sticky">
            <h2 className="sec-title" style={{ marginBottom: 0 }}>{t('partners.algorithmTitleLine1')}<br/>{t('partners.algorithmTitleLine2')}</h2>
            <div className="story-big-num-wrap">
              <AnimatePresence>
                {stepsData.map((_, i) => (
                  current === i && (
                    <motion.div
                      key={i}
                      className="story-big-num active"
                      initial={{ opacity: 0, y: 50, rotateX: -90 }}
                      animate={{ opacity: 1, y: 0, rotateX: 0 }}
                      exit={{ opacity: 0, y: -50, rotateX: 90 }}
                      transition={{ duration: 0.5, type: 'spring' }}
                    >
                      0{i+1}
                    </motion.div>
                  )
                ))}
              </AnimatePresence>
            </div>
          </div>

          <div className="story-r-list">
            {stepsData.map((s, i) => (
              <div key={i} className={`step-item ${current === i ? 'active' : ''}`}>
                <h3>{s.title}</h3>
                <p>{s.desc}</p>
              </div>
            ))}
          </div>

        </div>
      </div>
    </section>
  );
}

/* ── 5. BENTO ── */
function BentoSection() {
  const { t } = useTranslation();
  return (
    <section className="spacer-section">
      <div className="mx w-full">
        <motion.div initial="hidden" whileInView="visible" viewport={{ once: true }} variants={blurReveal}>
          <h2 className="sec-title">{t('partners.benefitsTitle')}</h2>
        </motion.div>
        
        <motion.div className="bento" variants={staggerChildren} initial="hidden" whileInView="visible" viewport={{ once: true, margin: "-10%" }}>
          <BentoCard className="wide">
            <div className="b-icon"><Users size={60} strokeWidth={1} /></div>
            <h3>{t('partners.benefitNewClients')}</h3>
            <p>{t('partners.benefitNewClientsDesc')}</p>
          </BentoCard>
          <BentoCard className="tall">
            <div className="b-icon"><BarChart3 size={60} strokeWidth={1} /></div>
            <h3>{t('partners.benefitTransparency')}</h3>
            <p>{t('partners.benefitTransparencyDesc')}</p>
          </BentoCard>
          <BentoCard>
            <div className="b-icon"><MapPin size={60} strokeWidth={1} /></div>
            <h3>{t('partners.benefitLocal')}</h3>
            <p>{t('partners.benefitLocalDesc')}</p>
          </BentoCard>
          <BentoCard>
            <div className="b-icon"><Zap size={60} strokeWidth={1} /></div>
            <h3>{t('partners.benefitMotivation')}</h3>
            <p>{t('partners.benefitMotivationDesc')}</p>
          </BentoCard>
        </motion.div>
      </div>
    </section>
  );
}

/* ── 6. FORM ── */
function FormSection() {
  const { t } = useTranslation();
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);

  const schema = useMemo(() => z.object({
    name: z.string().trim().min(2, t('partners.validation.name')),
    phone: z.string().trim().regex(/^\+?998\s?\d{2}\s?\d{3}\s?\d{2}\s?\d{2}$/, t('partners.validation.phone')),
    city: z.string().trim().min(1, t('partners.validation.city')),
    businessCategory: z.string().trim().min(1, t('partners.validation.category')),
    businessName: z.string().trim().optional().or(z.literal('')),
    comment: z.string().trim().optional().or(z.literal('')),
  }), [t]);

  const cities = useMemo(() => [
    t('partners.cities.tashkent'), t('partners.cities.samarkand'), t('partners.cities.bukhara'),
    t('partners.cities.namangan'), t('partners.cities.andijan'), t('partners.cities.fergana'),
    t('partners.cities.nukus'), t('partners.cities.khiva'),
  ], [t]);

  const bizCats = useMemo(() => [
    t('partners.bizCategories.cafe'), t('partners.bizCategories.beauty'), t('partners.bizCategories.fitness'),
    t('partners.bizCategories.education'), t('partners.bizCategories.entertainment'),
    t('partners.bizCategories.shop'), t('partners.bizCategories.services'),
  ], [t]);

  const { register, handleSubmit, setValue, watch, formState: { errors } } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const selectedCity = watch('city');
  const selectedCategory = watch('businessCategory');

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
        comment: values.comment || t('partners.commentDefault')
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
    <section className="spacer-section" id="lead">
      <div className="mx w-full glass-panel">
        <div className="form-hero">
          <div>
            <motion.h2 className="sec-title mb-6" initial="hidden" whileInView="visible" viewport={{ once: true }} variants={blurReveal}>
              {t('partners.formTitleLine1')} <br/>{t('partners.formTitleLine2')}
            </motion.h2>
            <motion.p className="sec-desc text-xl mb-10" initial="hidden" whileInView="visible" viewport={{ once: true }} variants={blurReveal}>
              {t('partners.formDesc')}
            </motion.p>
            <div className="flex items-center gap-4 text-dim"><ShieldCheck color="var(--accent-red)"/> {t('partners.dataProtection')}</div>
          </div>
          
          <div>
            <AnimatePresence mode="wait">
              {success ? (
                <motion.div key="success" initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="text-center py-20">
                  <CheckCircle2 size={80} color="var(--accent-red)" className="mx-auto mb-6" />
                  <h3 className="text-3xl font-display font-bold mb-4">{t('partners.formSuccessTitle')}</h3>
                  <p className="text-dim">{t('partners.formSuccessDesc')}</p>
                </motion.div>
              ) : (
                <motion.form key="form" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onSubmit={onSubmit} noValidate>
                  <div className="flex gap-6 form-row">
                    <div className="f-group w-1/2">
                      <label>{t('partners.formName')}</label>
                      <input {...register('name')} placeholder={t('partners.namePlaceholder')} />
                      {errors.name && <span className="text-red-500 text-xs mt-2 block" role="alert">{errors.name.message}</span>}
                    </div>
                    <div className="f-group w-1/2">
                      <label>{t('partners.formPhone')}</label>
                      <input {...register('phone')} placeholder="+998 XX XXX XX XX" />
                      {errors.phone && <span className="text-red-500 text-xs mt-2 block" role="alert">{errors.phone.message}</span>}
                    </div>
                  </div>
                  
                  <div className="flex gap-6 form-row">
                    <div className="f-group w-1/2">
                      <label>{t('partners.formCity')}</label>
                      <CustomSelect 
                        placeholder={t('partners.formSelect')}
                        options={cities}
                        value={selectedCity || ''}
                        onChange={(val) => setValue('city', val, { shouldValidate: true })}
                      />
                      {errors.city && <span className="text-red-500 text-xs mt-2 block" role="alert">{errors.city.message}</span>}
                    </div>
                    <div className="f-group w-1/2">
                      <label>{t('partners.formCategory')}</label>
                      <CustomSelect 
                        placeholder={t('partners.formSelect')}
                        options={bizCats}
                        value={selectedCategory || ''}
                        onChange={(val) => setValue('businessCategory', val, { shouldValidate: true })}
                      />
                      {errors.businessCategory && <span className="text-red-500 text-xs mt-2 block" role="alert">{errors.businessCategory.message}</span>}
                    </div>
                  </div>

                  <div className="f-group">
                    <label>{t('partners.formBusinessName')}</label>
                    <input {...register('businessName')} placeholder={t('partners.formOptional')} />
                  </div>

                  <button type="submit" disabled={submitting} className="btn-submit">
                    {submitting ? t('common.submitting') : t('partners.formSubmit')}
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
  const { t } = useTranslation();
  const [open, setOpen] = useState<number | null>(0);
  const faqs = [
    { q: t('partners.faq.q1'), a: t('partners.faq.a1') },
    { q: t('partners.faq.q2'), a: t('partners.faq.a2') },
    { q: t('partners.faq.q3'), a: t('partners.faq.a3') },
    { q: t('partners.faq.q4'), a: t('partners.faq.a4') },
  ];

  return (
    <section className="spacer-section" style={{ flexDirection: 'column' }}>
      <div className="mx w-full mb-40">
        <h2 className="sec-title text-center mb-16">{t('partners.faqTitle')}</h2>
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
        <a href="#lead" className="btn-primary" style={{ textDecoration: 'none', transform: 'scale(1.2)', margin: '20px auto', display: 'flex', maxWidth: '240px', justifyContent: 'center' }}>
          {t('partners.ctaJoin')}
        </a>
      </div>
    </section>
  );
}
