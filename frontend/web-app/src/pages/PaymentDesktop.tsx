import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
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

export default function PaymentDesktop() {
  const { t } = useTranslation();
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
          setError(error.response?.data?.message || t('payment.checkError'));
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
      const msg = error.response?.data?.message || t('payment.confirmError');
      setError(msg);
      if (error.response?.status === 403) {
        setError(t('payment.demoUnavailable'));
      }
    } finally {
      setDemoLoading(false);
    }
  };

  if (!numericOrderId) {
    return (
      <div className="payment-page container">
        <div className="payment-card surface-card">
          <AlertCircle size={48} className="payment-icon payment-icon--error" />
          <h2>{t('payment.orderNotFound')}</h2>
          <button className="primary-button" onClick={() => navigate(lp('/coupons'))}>
            {t('payment.backToCatalog')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="payment-page container">
      <div className="payment-card surface-card">

        {/* POLLING: waiting for payment creation */}
        {state === 'polling' && (
          <div className="payment-state">
            <Loader2 size={48} className="payment-icon spin" />
            <h2>{t('payment.creating')}</h2>
            <p className="payment-subtitle">
              {t('payment.creatingDesc', { orderId: numericOrderId })}
            </p>
            <div className="payment-progress">
              <div 
                className="payment-progress__bar"
                style={{ width: `${Math.min((pollCountRef.current / MAX_POLL_ATTEMPTS) * 100, 95)}%` }}
              />
            </div>
            <p className="payment-hint">{t('payment.creatingHint')}</p>
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
                  {t('payment.demoBadge')}
                </div>
                <Clock size={48} className="payment-icon payment-icon--pending" />
                <h2>{t('payment.confirmTitle')}</h2>
                <p className="payment-subtitle">
                  {t('payment.orderLine', { orderId: numericOrderId, amount: orderTotal > 0 ? formatPrice(orderTotal) : formatPrice(payment.amount) })}
                </p>

                <div className="payment-demo-block">
                  <p className="payment-demo-note">
                    {t('payment.demoNote')}
                  </p>
                  <div className="payment-details">
                    <div className="payment-detail-row">
                      <span>{t('payment.amount')}</span>
                      <span>{orderTotal > 0 ? formatPrice(orderTotal) : formatPrice(payment.amount)}</span>
                    </div>
                    <div className="payment-detail-row">
                      <span>{t('payment.status')}</span>
                      <span className="payment-status payment-status--pending">{t('payment.statusPendingConfirm')}</span>
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
                      {t('payment.confirming')}
                    </>
                  ) : (
                    <>
                      <ShieldCheck size={18} />
                      {t('payment.confirmPurchase')}
                    </>
                  )}
                </button>

                <button 
                  className="secondary-button" 
                  onClick={() => navigate(lp('/profile'))}
                >
                  {t('payment.backToProfile')}
                </button>
              </>
            ) : (
              /* ===== PROVIDER MODE (real payment) ===== */
              <>
                <Clock size={48} className="payment-icon payment-icon--pending" />
                <h2>{t('payment.readyTitle')}</h2>
                <p className="payment-subtitle">
                  {t('payment.orderLine', { orderId: numericOrderId, amount: orderTotal > 0 ? formatPrice(orderTotal) : formatPrice(payment.amount) })}
                </p>
                <div className="payment-details">
                  <div className="payment-detail-row">
                    <span>{t('payment.provider')}</span>
                    <span>{payment.provider}</span>
                  </div>
                  <div className="payment-detail-row">
                    <span>{t('payment.status')}</span>
                    <span className="payment-status payment-status--pending">{t('payment.statusPendingPay')}</span>
                  </div>
                </div>
                {payment.paymentUrl ? (
                  <button className="primary-button payment-redirect-btn" onClick={handlePaymentRedirect}>
                    <ExternalLink size={18} />
                    {t('payment.goToPay')}
                  </button>
                ) : (
                  <p className="payment-hint">{t('payment.linkForming')}</p>
                )}
              </>
            )}
          </div>
        )}

        {/* CONFIRMING: demo completion in progress */}
        {state === 'confirming' && (
          <div className="payment-state">
            <Loader2 size={48} className="payment-icon spin" />
            <h2>{t('payment.confirmingPurchase')}</h2>
            <p className="payment-subtitle">{t('payment.pleaseWait')}</p>
          </div>
        )}

        {/* REDIRECTING */}
        {state === 'redirecting' && (
          <div className="payment-state">
            <Loader2 size={48} className="payment-icon spin" />
            <h2>{t('payment.redirecting')}</h2>
            <p className="payment-subtitle">{t('payment.redirectingDesc')}</p>
          </div>
        )}

        {/* COMPLETED */}
        {state === 'completed' && (
          <div className="payment-state">
            <CheckCircle2 size={48} className="payment-icon payment-icon--success" />
            <h2>{t('payment.successTitle')}</h2>
            <p className="payment-subtitle">
              {isDemoMode
                ? t('payment.successDescConfirmed', { orderId: numericOrderId })
                : t('payment.successDescPaid', { orderId: numericOrderId })}
            </p>
            {payment?.transactionId && (
              <p className="payment-transaction">{t('payment.transactionId', { id: payment.transactionId })}</p>
            )}
            <div className="payment-next-steps">
              <h3>{t('payment.nextStepsTitle')}</h3>
              <ol>
                <li>{t('payment.step1')}</li>
                <li>{t('payment.step2')}</li>
                <li>{t('payment.step3')}</li>
              </ol>
            </div>
            <button className="primary-button" onClick={() => navigate(lp('/profile') + '?tab=coupons')}>
              {t('payment.openCoupons')}
            </button>
            <button className="secondary-button" style={{ marginTop: 8 }} onClick={() => navigate(lp('/profile') + '?tab=orders')}>
              {t('payment.orderHistory')}
            </button>
          </div>
        )}

        {/* FAILED */}
        {state === 'failed' && (
          <div className="payment-state">
            <XCircle size={48} className="payment-icon payment-icon--error" />
            <h2>{t('payment.errorTitle')}</h2>
            <p className="payment-subtitle">
              {error || t('payment.errorDefault')}
            </p>
            <div className="payment-actions">
              <button className="primary-button" onClick={() => window.location.reload()}>
                {t('payment.retry')}
              </button>
              <button className="secondary-button" onClick={() => navigate(lp('/profile') + '?tab=orders')}>
                {t('payment.myOrders')}
              </button>
            </div>
          </div>
        )}

        {/* TIMEOUT */}
        {state === 'timeout' && (
          <div className="payment-state">
            <AlertCircle size={48} className="payment-icon payment-icon--warning" />
            <h2>{t('payment.timeoutTitle')}</h2>
            <p className="payment-subtitle">
              {t('payment.timeoutDesc')}
            </p>
            <div className="payment-actions">
              <button className="primary-button" onClick={() => navigate(lp('/profile') + '?tab=orders')}>
                {t('payment.myOrders')}
              </button>
              <button className="secondary-button" onClick={() => window.location.reload()}>
                {t('payment.refreshStatus')}
              </button>
            </div>
          </div>
        )}

      </div>
    </div>
  );
}
