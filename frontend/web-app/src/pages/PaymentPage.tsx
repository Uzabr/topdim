import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Loader2, CheckCircle2, XCircle, ExternalLink, Clock, AlertCircle } from 'lucide-react';
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
 * 2. Когда payment появился → показываем paymentUrl для redirect
 * 3. После оплаты → показываем success/failure
 * 
 * Polling: каждые 2 сек, максимум 30 попыток (1 минута).
 */

type PaymentState = 'polling' | 'pending' | 'redirecting' | 'completed' | 'failed' | 'timeout';

const POLL_INTERVAL_MS = 2000;
const MAX_POLL_ATTEMPTS = 30;

export default function PaymentPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const lp = useLocalePath();

  const [state, setState] = useState<PaymentState>('polling');
  const [payment, setPayment] = useState<PaymentResponse | null>(null);
  const [orderTotal, setOrderTotal] = useState<number>(0);
  const [error, setError] = useState('');
  const pollCountRef = useRef(0);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const numericOrderId = Number(orderId);

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
        } else if (p.paymentUrl) {
          setState('pending');
        } else {
          setState('pending');
        }
      } catch (err: any) {
        // 404 = payment not yet created by event-driven flow
        if (err.response?.status === 404) {
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
          setError(err.response?.data?.message || 'Ошибка при проверке платежа');
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

  // Redirect to payment URL
  const handlePaymentRedirect = () => {
    if (payment?.paymentUrl) {
      setState('redirecting');
      window.location.href = payment.paymentUrl;
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

        {/* PENDING: payment created, ready for redirect */}
        {state === 'pending' && payment && (
          <div className="payment-state">
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
            <h2>Оплата прошла успешно!</h2>
            <p className="payment-subtitle">
              Заказ #{numericOrderId} оплачен
            </p>
            {payment?.transactionId && (
              <p className="payment-transaction">ID транзакции: {payment.transactionId}</p>
            )}
            <button className="primary-button" onClick={() => navigate(lp('/profile'))}>
              Перейти в профиль
            </button>
          </div>
        )}

        {/* FAILED */}
        {state === 'failed' && (
          <div className="payment-state">
            <XCircle size={48} className="payment-icon payment-icon--error" />
            <h2>Ошибка оплаты</h2>
            <p className="payment-subtitle">
              {error || 'Платёж не был завершён. Попробуйте ещё раз.'}
            </p>
            <div className="payment-actions">
              <button className="primary-button" onClick={() => window.location.reload()}>
                Попробовать снова
              </button>
              <button className="secondary-button" onClick={() => navigate(lp('/profile'))}>
                Перейти в профиль
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
              Сервис оплаты временно недоступен. Пожалуйста, попробуйте позже.
            </p>
            <div className="payment-actions">
              <button className="primary-button" onClick={() => window.location.reload()}>
                Попробовать снова
              </button>
              <button className="secondary-button" onClick={() => navigate(lp('/profile'))}>
                Перейти в профиль
              </button>
            </div>
          </div>
        )}

      </div>
    </div>
  );
}
