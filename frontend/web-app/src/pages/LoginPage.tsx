import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Eye, EyeOff, Mail, Lock, User, Phone } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../store/authStore';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { IMaskInput } from 'react-imask';
import { useLocalePath } from '../hooks/useLocalePath';
import './LoginPage.css';

export default function LoginPage() {
  const { t } = useTranslation();
  const [isLogin, setIsLogin] = useState(true);
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState('');

  const loginSchema = useMemo(() => z.object({
    email: z.string().min(1, t('login.validation.emailRequired')).email(t('login.validation.emailInvalid')),
    password: z.string().min(1, t('login.validation.passwordRequired')),
  }), [t]);

  const registerSchema = useMemo(() => loginSchema.extend({
    firstName: z.string().min(2, t('login.validation.firstNameMin')),
    phone: z.string()
      .optional()
      .transform(e => e === "" ? undefined : e)
      .refine((val) => !val || /^\+998\d{9}$/.test(val), {
        message: t('login.validation.phoneFormat'),
      }),
    password: z.string().min(8, t('login.validation.passwordMin')),
  }), [loginSchema, t]);

  const { login, register, isLoading } = useAuthStore();
  const navigate = useNavigate();
  const lp = useLocalePath();

  type FormData = {
    email: string;
    password: string;
    firstName?: string;
    phone?: string;
  };

  const {
    register: formRegister,
    handleSubmit,
    control,
    formState: { errors },
    reset,
  } = useForm<FormData>({
    resolver: zodResolver(isLogin ? loginSchema : registerSchema),
    mode: 'onBlur',
  });

  const onSubmit = async (data: FormData) => {
    setServerError('');
    try {
      if (isLogin) {
        await login({ email: data.email, password: data.password });
      } else {
        await register({
          email: data.email,
          password: data.password,
          firstName: data.firstName || '',
          phone: data.phone,
        });
      }
      navigate(lp('/'));
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      setServerError(error.response?.data?.message || t('login.serverError'));
    }
  };

  const toggleMode = () => {
    setIsLogin(!isLogin);
    setServerError('');
    reset();
  };

  return (
    <div className="login-page">
      <div className="login-card glass">
        <div className="login-header">
          <Link to={lp('/')} className="login-logo">💎 TopDim</Link>
          <h1>{isLogin ? t('login.titleLogin') : t('login.titleRegister')}</h1>
          <p className="login-subtitle">
            {isLogin ? t('login.subtitleLogin') : t('login.subtitleRegister')}
          </p>
        </div>

        <form className="login-form" onSubmit={handleSubmit(onSubmit)}>
          {!isLogin && (
            <div className="input-wrapper">
              <div className="input-group">
                <User size={18} className="input-icon" />
                <input
                  type="text"
                  placeholder={t('login.firstName')}
                  {...formRegister('firstName')}
                />
              </div>
              {errors.firstName && <span className="invalid-feedback">{errors.firstName.message?.toString()}</span>}
            </div>
          )}

          <div className="input-wrapper">
            <div className="input-group">
              <Mail size={18} className="input-icon" />
              <input
                type="email"
                placeholder="Email"
                {...formRegister('email')}
              />
            </div>
            {errors.email && <span className="invalid-feedback">{errors.email.message?.toString()}</span>}
          </div>

          {!isLogin && (
            <div className="input-wrapper">
              <div className="input-group">
                <Phone size={18} className="input-icon" />
                <Controller
                  name="phone"
                  control={control}
                  render={({ field: { onChange, onBlur, value, ref } }) => (
                    <IMaskInput
                      mask="+{998} 00 000-00-00"
                      placeholder={t('login.phonePlaceholder')}
                      value={value || ''}
                      onAccept={(val) => onChange(val.replace(/\s|-/g, ''))}
                      onBlur={onBlur}
                      inputRef={ref}
                    />
                  )}
                />
              </div>
              {errors.phone && <span className="invalid-feedback">{errors.phone.message?.toString()}</span>}
            </div>
          )}

          <div className="input-wrapper">
            <div className="input-group">
              <Lock size={18} className="input-icon" />
              <input
                type={showPassword ? 'text' : 'password'}
                placeholder={t('login.password')}
                {...formRegister('password')}
              />
              <button
                type="button"
                className="input-toggle"
                onClick={() => setShowPassword(!showPassword)}
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {errors.password && <span className="invalid-feedback">{errors.password.message?.toString()}</span>}
          </div>

          {serverError && <div className="login-error">{serverError}</div>}

          <button type="submit" className="login-submit" disabled={isLoading}>
            {isLoading ? t('common.loading') : isLogin ? t('login.submitLogin') : t('login.submitRegister')}
          </button>
        </form>

        <div className="login-footer">
          <span className="login-switch">
            {isLogin ? t('login.noAccount') : t('login.hasAccount')}
            <button onClick={toggleMode}>
              {isLogin ? t('login.switchRegister') : t('login.switchLogin')}
            </button>
          </span>
        </div>
      </div>
    </div>
  );
}
