import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Loader2, CheckCircle2, XCircle, ExternalLink, Clock, AlertCircle, ShieldCheck } from 'lucide-react';
import { paymentsApi, type PaymentResponse } from '../api/payments';
import { ordersApi } from '../api/orders';
import { formatPrice } from '../utils/format';
import { useLocalePath } from '../hooks/useLocalePath';
import './PaymentPage.css';

/**
 * PaymentPage — промежуточная страница после создания order.
 * 
 * Flow:
 * 1. Показываем "Ожидание создания платежа..." (polling getByOrderId)
 * 2. Когда payment появился:
 *    - demo mode → показываем "Подтвердить покупку"
 *    - provider mode → показываем paymentUrl для redirect
 * 3. После оплаты/demo-confirm → показываем success
 * 
 * Polling: каждые 2 сек, максимум 30 попыток (1 минута).
 */

type PaymentState = 'polling' | 'pending' | 'redirecting' | 'confirming' | 'completed' | 'failed' | 'timeout';

const POLL_INTERVAL_MS = 2000;
const MAX_POLL_ATTEMPTS = 30;

export default function PaymentPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const lp = useLocalePath();
  const queryClient = useQueryClient();

  const [state, setState] = useState<PaymentState>('polling');
  const [payment, setPayment] = useState<PaymentResponse | null>(null);
  const [orderTotal, setOrderTotal] = useState<number>(0);
  const [error, setError] = useState('');
  const [demoLoading, setDemoLoading] = useState(false);
  const pollCountRef = useRef(0);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const numericOrderId = Number(orderId);

  const isDemoMode = payment?.paymentMode === 'demo';

  // Fetch order info
  useEffect(() => {
    if (!numericOrderId) return;
    ordersApi.getOrder(numericOrderId)
      .then((res) => setOrderTotal(res.data.data.totalAmount))
      .catch(() => {});
  }, [numericOrderId]);

  // Polling for payment creation
  useEffect(() => {
    if (!numericOrderId) return;

    const poll = async () => {
      try {
        const res = await paymentsApi.getByOrderId(numericOrderId);
        const p = res.data.data;
        setPayment(p);

        // Payment found — stop polling
        if (intervalRef.current) {
          clearInterval(intervalRef.current);
          intervalRef.current = null;
        }

        // Determine state based on payment status
        const status = p.statusName?.toUpperCase();
        if (status === 'COMPLETED' || status === 'SUCCESS') {
          setState('completed');
        } else if (status === 'FAILED' || status === 'CANCELLED') {
          setState('failed');
        } else {
          setState('pending');
        }
      } catch (err: unknown) {
        const error = err as { response?: { status?: number, data?: { message?: string } } };
        // 404 = payment not yet created by event-driven flow
        if (error.response?.status === 404) {
          pollCountRef.current += 1;
          if (pollCountRef.current >= MAX_POLL_ATTEMPTS) {
            if (intervalRef.current) {
              clearInterval(intervalRef.current);
              intervalRef.current = null;
            }
            setState('timeout');
          }
          // Continue polling
        } else {
          // Unexpected error
          setError(error.response?.data?.message || 'Ошибка при проверке платежа');
          if (intervalRef.current) {
            clearInterval(intervalRef.current);
            intervalRef.current = null;
          }
          setState('failed');
        }
      }
    };

    // Start polling
    poll(); // immediate first check
    intervalRef.current = setInterval(poll, POLL_INTERVAL_MS);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [numericOrderId]);

  // Redirect to real payment URL (provider mode)
  const handlePaymentRedirect = () => {
    if (payment?.paymentUrl) {
      setState('redirecting');
      window.location.href = payment.paymentUrl;
    }
  };

  // Demo completion
  const handleDemoComplete = async () => {
    if (demoLoading) return;
    setDemoLoading(true);
    setError('');

    try {
      const res = await paymentsApi.demoComplete(numericOrderId);
      const p = res.data.data;
      setPayment(p);
      setState('completed');
      // Invalidate profile queries so coupons/orders refresh
      queryClient.invalidateQueries({ queryKey: ['my-coupons'] });
      queryClient.invalidateQueries({ queryKey: ['my-orders'] });
    } catch (err: unknown) {
      const error = err as { response?: { status?: number, data?: { message?: string } } };
      const msg = error.response?.data?.message || 'Ошибка при подтверждении покупки';
      setError(msg);
      // Don't switch to 'failed' for recoverable errors — keep on pending
      if (error.response?.status === 403) {
        setError('Демо-оплата недоступна. Обратитесь к администратору.');
      }
    } finally {
      setDemoLoading(false);
    }
  };

  if (!numericOrderId) {
    return (
      <div className="payment-page container">
        <div className="payment-card glass-card">
          <AlertCircle size={48} className="payment-icon payment-icon--error" />
          <h2>Заказ не найден</h2>
          <button className="primary-button" onClick={() => navigate(lp('/coupons'))}>
            Вернуться в каталог
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="payment-page container">
      <div className="payment-card glass-card">

        {/* POLLING: waiting for payment creation */}
        {state === 'polling' && (
          <div className="payment-state">
            <Loader2 size={48} className="payment-icon spin" />
            <h2>Создаём платёж...</h2>
            <p className="payment-subtitle">
              Подготавливаем оплату для заказа #{numericOrderId}
            </p>
            <div className="payment-progress">
              <div 
                className="payment-progress__bar"
                style={{ width: `${Math.min((pollCountRef.current / MAX_POLL_ATTEMPTS) * 100, 95)}%` }}
              />
            </div>
            <p className="payment-hint">Обычно это занимает несколько секунд</p>
          </div>
        )}

        {/* PENDING: payment created */}
        {state === 'pending' && payment && (
          <div className="payment-state">
            {isDemoMode ? (
              /* ===== DEMO MODE ===== */
              <>
                <div className="payment-demo-badge">
                  <ShieldCheck size={16} />
                  Демо-режим
                </div>
                <Clock size={48} className="payment-icon payment-icon--pending" />
                <h2>Подтверждение покупки</h2>
                <p className="payment-subtitle">
                  Заказ #{numericOrderId} • {orderTotal > 0 ? formatPrice(orderTotal) : formatPrice(payment.amount)}
                </p>

                <div className="payment-demo-block">
                  <p className="payment-demo-note">
                    Это временный демонстрационный режим оплаты. После подтверждения купоны 
                    появятся в вашем профиле.
                  </p>
                  <div className="payment-details">
                    <div className="payment-detail-row">
                      <span>Сумма:</span>
                      <span>{orderTotal > 0 ? formatPrice(orderTotal) : formatPrice(payment.amount)}</span>
                    </div>
                    <div className="payment-detail-row">
                      <span>Статус:</span>
                      <span className="payment-status payment-status--pending">Ожидает подтверждения</span>
                    </div>
                  </div>
                </div>

                {error && <p className="payment-error">{error}</p>}

                <button 
                  className="primary-button payment-demo-btn"
                  onClick={handleDemoComplete}
                  disabled={demoLoading}
                >
                  {demoLoading ? (
                    <>
                      <Loader2 size={18} className="spin" />
                      Подтверждаем...
                    </>
                  ) : (
                    <>
                      <ShieldCheck size={18} />
                      Подтвердить покупку
                    </>
                  )}
                </button>

                <button 
                  className="secondary-button" 
                  onClick={() => navigate(lp('/profile'))}
                >
                  Вернуться в профиль
                </button>
              </>
            ) : (
              /* ===== PROVIDER MODE (real payment) ===== */
              <>
                <Clock size={48} className="payment-icon payment-icon--pending" />
                <h2>Платёж готов</h2>
                <p className="payment-subtitle">
                  Заказ #{numericOrderId} • {orderTotal > 0 ? formatPrice(orderTotal) : formatPrice(payment.amount)}
                </p>
                <div className="payment-details">
                  <div className="payment-detail-row">
                    <span>Провайдер:</span>
                    <span>{payment.provider}</span>
                  </div>
                  <div className="payment-detail-row">
                    <span>Статус:</span>
                    <span className="payment-status payment-status--pending">Ожидает оплаты</span>
                  </div>
                </div>
                {payment.paymentUrl ? (
                  <button className="primary-button payment-redirect-btn" onClick={handlePaymentRedirect}>
                    <ExternalLink size={18} />
                    Перейти к оплате
                  </button>
                ) : (
                  <p className="payment-hint">Ссылка на оплату формируется...</p>
                )}
              </>
            )}
          </div>
        )}

        {/* CONFIRMING: demo completion in progress */}
        {state === 'confirming' && (
          <div className="payment-state">
            <Loader2 size={48} className="payment-icon spin" />
            <h2>Подтверждаем покупку...</h2>
            <p className="payment-subtitle">Пожалуйста, подождите</p>
          </div>
        )}

        {/* REDIRECTING */}
        {state === 'redirecting' && (
          <div className="payment-state">
            <Loader2 size={48} className="payment-icon spin" />
            <h2>Переходим к оплате...</h2>
            <p className="payment-subtitle">Вы будете перенаправлены на страницу платёжной системы</p>
          </div>
        )}

        {/* COMPLETED */}
        {state === 'completed' && (
          <div className="payment-state">
            <CheckCircle2 size={48} className="payment-icon payment-icon--success" />
            <h2>Покупка подтверждена!</h2>
            <p className="payment-subtitle">
              Заказ #{numericOrderId} {isDemoMode ? 'подтверждён' : 'оплачен'}
            </p>
            {payment?.transactionId && (
              <p className="payment-transaction">ID транзакции: {payment.transactionId}</p>
            )}
            <div className="payment-success-info">
              <p>Купоны уже доступны в профиле.</p>
            </div>
            <button className="primary-button" onClick={() => navigate(lp('/profile') + '?tab=coupons')}>
              Открыть мои купоны
            </button>
            <button className="secondary-button" style={{ marginTop: 8 }} onClick={() => navigate(lp('/profile') + '?tab=orders')}>
              История заказов
            </button>
          </div>
        )}

        {/* FAILED */}
        {state === 'failed' && (
          <div className="payment-state">
            <XCircle size={48} className="payment-icon payment-icon--error" />
            <h2>Ошибка</h2>
            <p className="payment-subtitle">
              {error || 'Платёж не был завершён. Заказ сохранён, вы можете попробовать оплатить ещё раз.'}
            </p>
            <div className="payment-actions">
              <button className="primary-button" onClick={() => window.location.reload()}>
                Попробовать снова
              </button>
              <button className="secondary-button" onClick={() => navigate(lp('/profile') + '?tab=orders')}>
                Мои заказы
              </button>
            </div>
          </div>
        )}

        {/* TIMEOUT */}
        {state === 'timeout' && (
          <div className="payment-state">
            <AlertCircle size={48} className="payment-icon payment-icon--warning" />
            <h2>Платёж не создан</h2>
            <p className="payment-subtitle">
              Мы не смогли быстро получить платёж. Заказ сохранён в профиле, попробуйте продолжить оплату из раздела «Мои заказы».
            </p>
            <div className="payment-actions">
              <button className="primary-button" onClick={() => navigate(lp('/profile') + '?tab=orders')}>
                Мои заказы
              </button>
              <button className="secondary-button" onClick={() => window.location.reload()}>
                Обновить статус
              </button>
            </div>
          </div>
        )}

      </div>
    </div>
  );
}
