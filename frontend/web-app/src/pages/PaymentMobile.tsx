import { useEffect, useRef, useState } from 'react';
import { AlertCircle, Check, ExternalLink, Loader2, ShieldCheck } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { ordersApi } from '../api/orders';
import { paymentsApi, type PaymentResponse } from '../api/payments';
import { useLocalePath } from '../hooks/useLocalePath';
import { buildQrPayload } from '../utils/coupon';
import { formatDate, formatPrice } from '../utils/format';
import './PaymentMobile.css';

type State = 'polling' | 'pending' | 'completed' | 'failed' | 'timeout';

const POLL_MS = 2000;
const MAX_POLLS = 30;

/**
 * Мобильная оплата и экран «Оплачено». Референс: «Мобилка - 6 Оплачено» — но
 * QR берём настоящий: купон приходит в профиль, никакого Telegram у нас нет.
 */
export default function PaymentMobile() {
  const { t } = useTranslation();
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const lp = useLocalePath();
  const queryClient = useQueryClient();

  const id = Number(orderId);

  const [state, setState] = useState<State>('polling');
  const [payment, setPayment] = useState<PaymentResponse | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState('');
  const [confirming, setConfirming] = useState(false);
  const pollsRef = useRef(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const isDemo = payment?.paymentMode === 'demo';

  useEffect(() => {
    if (!id) return;
    ordersApi
      .getOrder(id)
      .then((res) => setTotal(res.data.data.totalAmount))
      .catch(() => {});
  }, [id]);

  useEffect(() => {
    if (!id) return;

    const stop = () => {
      if (timerRef.current) clearInterval(timerRef.current);
      timerRef.current = null;
    };

    const poll = async () => {
      try {
        const res = await paymentsApi.getByOrderId(id);
        const p = res.data.data;
        setPayment(p);
        stop();

        const status = p.statusName?.toUpperCase();
        if (status === 'COMPLETED' || status === 'SUCCESS') setState('completed');
        else if (status === 'FAILED' || status === 'CANCELLED') setState('failed');
        else setState('pending');
      } catch (err: unknown) {
        const e = err as { response?: { status?: number; data?: { message?: string } } };
        // 404 — платёж ещё не создан событием; ждём.
        if (e.response?.status === 404) {
          pollsRef.current += 1;
          if (pollsRef.current >= MAX_POLLS) {
            stop();
            setState('timeout');
          }
          return;
        }
        setError(e.response?.data?.message || t('payment.checkError'));
        stop();
        setState('failed');
      }
    };

    poll();
    timerRef.current = setInterval(poll, POLL_MS);
    return stop;
    // t не влияет на опрос — перезапускать его при смене языка незачем.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  // QR показываем настоящий: берём свежекупленный активный купон.
  const { data: fresh } = useQuery({
    queryKey: ['my-coupons'],
    queryFn: () => ordersApi.getMyCoupons(),
    select: (res) =>
      [...res.data.data]
        .filter((c) => c.status === 'ACTIVE' && c.qrToken)
        .sort((a, b) => Date.parse(b.purchasedAt) - Date.parse(a.purchasedAt))[0] ?? null,
    enabled: state === 'completed',
    retry: false,
  });

  const confirmDemo = async () => {
    if (confirming) return;
    setConfirming(true);
    setError('');
    try {
      const res = await paymentsApi.demoComplete(id);
      setPayment(res.data.data);
      setState('completed');
      queryClient.invalidateQueries({ queryKey: ['my-coupons'] });
      queryClient.invalidateQueries({ queryKey: ['my-orders'] });
    } catch (err: unknown) {
      const e = err as { response?: { status?: number; data?: { message?: string } } };
      setError(
        e.response?.status === 403
          ? t('payment.demoUnavailable')
          : e.response?.data?.message || t('payment.confirmError'),
      );
    } finally {
      setConfirming(false);
    }
  };

  if (!id) {
    return (
      <div className="cmdone">
        <h1 className="cmdone__title">{t('payment.orderNotFound')}</h1>
        <button type="button" className="cmdone__btn" onClick={() => navigate(lp('/coupons'))}>
          {t('payment.backToCatalog')}
        </button>
      </div>
    );
  }

  const amount = formatPrice(total || payment?.amount || 0);

  if (state === 'completed') {
    return (
      <div className="cmdone">
        <span className="cmdone__check">
          <Check size={24} strokeWidth={2.4} />
        </span>

        <div className="cmdone__qr">
          {fresh?.qrToken ? (
            <QRCodeSVG value={buildQrPayload(fresh.qrToken)} size={134} level="M" />
          ) : (
            <Loader2 size={28} className="cmdone__spin" />
          )}
        </div>

        <h1 className="cmdone__title">{t('mobile.paid.title')}</h1>
        <p className="cmdone__text">
          {t('mobile.paid.text')}
          {fresh?.expiresAt && (
            <>
              <br />
              {t('mobile.paid.until', { date: formatDate(fresh.expiresAt) })}
            </>
          )}
        </p>

        <div className="cmdone__actions">
          <button
            type="button"
            className="cmdone__btn"
            onClick={() => navigate(`${lp('/profile')}?tab=coupons`)}
          >
            {t('payment.openCoupons')}
          </button>
          <button
            type="button"
            className="cmdone__btn cmdone__btn--ghost"
            onClick={() => navigate(lp('/'))}
          >
            {t('payment.toHome')}
          </button>
        </div>
      </div>
    );
  }

  if (state === 'polling') {
    return (
      <div className="cmdone">
        <Loader2 size={30} className="cmdone__spin" />
        <h1 className="cmdone__title">{t('payment.creating')}</h1>
        <p className="cmdone__text">{t('payment.creatingHint')}</p>
      </div>
    );
  }

  if (state === 'pending' && payment) {
    return (
      <div className="cmdone">
        {isDemo ? (
          <>
            <span className="cmdone__badge">
              <ShieldCheck size={13} /> {t('payment.demoBadge')}
            </span>
            <h1 className="cmdone__title">{t('payment.confirmTitle')}</h1>
            <p className="cmdone__text">{t('payment.orderLine', { orderId: id, amount })}</p>
            <p className="cmdone__note">{t('payment.demoNote')}</p>

            {error && <p className="cmdone__error">{error}</p>}

            <div className="cmdone__actions">
              <button
                type="button"
                className="cmdone__btn"
                onClick={confirmDemo}
                disabled={confirming}
              >
                {confirming ? t('payment.confirming') : t('payment.confirmPurchase')}
              </button>
            </div>
          </>
        ) : (
          <>
            <h1 className="cmdone__title">{t('payment.readyTitle')}</h1>
            <p className="cmdone__text">{t('payment.orderLine', { orderId: id, amount })}</p>

            {payment.paymentUrl ? (
              <div className="cmdone__actions">
                <a className="cmdone__btn" href={payment.paymentUrl}>
                  <ExternalLink size={15} /> {t('payment.goToPay')}
                </a>
              </div>
            ) : (
              <p className="cmdone__note">{t('payment.linkForming')}</p>
            )}
          </>
        )}
      </div>
    );
  }

  return (
    <div className="cmdone">
      <AlertCircle size={30} className="cmdone__warn" />
      <h1 className="cmdone__title">
        {state === 'timeout' ? t('payment.timeoutTitle') : t('payment.errorTitle')}
      </h1>
      <p className="cmdone__text">
        {state === 'timeout' ? t('payment.timeoutDesc') : error || t('payment.errorDefault')}
      </p>

      <div className="cmdone__actions">
        <button
          type="button"
          className="cmdone__btn"
          onClick={() => navigate(`${lp('/profile')}?tab=orders`)}
        >
          {t('payment.myOrders')}
        </button>
        <button
          type="button"
          className="cmdone__btn cmdone__btn--ghost"
          onClick={() => window.location.reload()}
        >
          {t('payment.retry')}
        </button>
      </div>
    </div>
  );
}
