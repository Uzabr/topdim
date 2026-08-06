import { useState, useRef, useEffect, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Card, Button, Input, Typography, message, Result, Space, Tag, Tabs, Alert } from 'antd';
import { ScanOutlined, NumberOutlined, CheckCircleFilled, CameraOutlined, StopOutlined } from '@ant-design/icons';
import { Html5Qrcode } from 'html5-qrcode';
import api from '../api';

/**
 * Normalize backend error messages into cashier-friendly text.
 * Raw backend messages may be technical — we translate them.
 */
function normalizeCashierError(backendMsg: string): string {
  const lower = backendMsg.toLowerCase();
  if (lower.includes('не найден') || lower.includes('not found')) {
    return 'Код не найден. Проверьте PIN или попросите клиента показать QR.';
  }
  if (lower.includes('другому мерчанту') || lower.includes('wrong merchant') || lower.includes('не принадлежит')) {
    return 'Этот купон относится к другому партнёру.';
  }
  if (lower.includes('used') || lower.includes('уже использован') || lower.includes('статус: used') || lower.includes('status: used')) {
    return 'Этот купон уже был использован.';
  }
  if (lower.includes('истёк') || lower.includes('истек') || lower.includes('expired') || lower.includes('срок')) {
    return 'Срок действия купона истёк.';
  }
  return backendMsg;
}

const { Title, Text } = Typography;

interface RedeemResult {
  purchasedCouponId: number;
  couponTitle: string;
  optionTitle: string;
  couponCode: string;
  merchantName: string;
  status: string;
  expiresAt?: string;
  usedAt?: string;
}

function parseTopDimQrPayload(value: string): string | null {
  const prefix = 'TOPDIM-QR:';
  if (!value.startsWith(prefix)) {
    return null;
  }
  const token = value.slice(prefix.length).trim();
  return token || null;
}

export default function RedeemPage() {
  const queryClient = useQueryClient();
  const [pinCode, setPinCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<RedeemResult | null>(null);
  const [errorText, setErrorText] = useState('');
  const [isScanning, setIsScanning] = useState(false);
  const [scannerError, setScannerError] = useState('');
  const [redeemMethod, setRedeemMethod] = useState<'PIN' | 'QR'>('PIN');

  const scannerRef = useRef<Html5Qrcode | null>(null);
  const stopScannerRef = useRef<(() => Promise<void>) | null>(null);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stopScannerRef.current?.();
    };
  }, []);

  const completeRedemption = useCallback((data: RedeemResult, method: 'PIN' | 'QR') => {
    setResult(data);
    setRedeemMethod(method);
    void queryClient.invalidateQueries({ queryKey: ['partner-redemptions'] });
    message.success(method === 'QR' ? 'Купон погашен по QR!' : 'Купон погашен по PIN!');
  }, [queryClient]);

  const redeemQrToken = useCallback(async (token: string) => {
    setLoading(true);
    setErrorText('');
    setScannerError('');
    try {
      const res = await api.post('/api/v1/partner/redemptions/qr', { qrToken: token });
      completeRedemption(res.data.data, 'QR');
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      const raw = e.response?.data?.message || 'Ошибка погашения';
      const friendly = normalizeCashierError(raw);
      setErrorText(friendly);
      message.error(friendly);
      if (raw !== friendly) console.warn('[Redeem QR] Raw backend error:', raw);
    } finally {
      setLoading(false);
    }
  }, [completeRedemption]);

  const stopScanner = useCallback(async () => {
    try {
      const scanner = scannerRef.current;
      if (scanner) {
        const state = scanner.getState();
        // Html5QrcodeScannerState: 1 = NOT_STARTED, 2 = SCANNING, 3 = PAUSED
        if (state === 2 || state === 3) {
          await scanner.stop();
        }
      }
    } catch {
      // Scanner already stopped or not initialized
    }
    setIsScanning(false);
  }, []);

  const startScanner = useCallback(async () => {
    setScannerError('');
    setErrorText('');
    setIsScanning(true);

    // Small delay to let the DOM render the container
    await new Promise(resolve => setTimeout(resolve, 100));

    const container = document.getElementById('topdim-qr-reader');
    if (!container) {
      setScannerError('Не удалось инициализировать сканер');
      setIsScanning(false);
      return;
    }

    const scanner = new Html5Qrcode('topdim-qr-reader');
    scannerRef.current = scanner;

    stopScannerRef.current = async () => {
      try {
        const state = scanner.getState();
        if (state === 2 || state === 3) {
          await scanner.stop();
        }
      } catch {
        // Ignore
      }
      setIsScanning(false);
    };

    try {
      await scanner.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 250, height: 250 } },
        async (decodedText) => {
          // Stop scanner first
          await stopScannerRef.current?.();

          const token = parseTopDimQrPayload(decodedText);
          if (!token) {
            setScannerError('Это не QR-код sizbiz. Попробуйте снова или используйте PIN-код.');
            return;
          }

          await redeemQrToken(token);
        },
        () => {
          // Ignore continuous scan errors (no QR in frame)
        }
      );
    } catch {
      setScannerError('Не удалось открыть камеру. Проверьте разрешение браузера или используйте PIN-код.');
      setIsScanning(false);
    }
  }, [redeemQrToken]);

  const handlePinRedeem = async () => {
    if (!pinCode.trim()) return message.warning('Введите код купона');
    setLoading(true);
    setErrorText('');
    try {
      const res = await api.post('/api/v1/partner/redemptions', { couponCode: pinCode.trim() });
      completeRedemption(res.data.data, 'PIN');
      setPinCode('');
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      const raw = e.response?.data?.message || 'Ошибка погашения';
      const friendly = normalizeCashierError(raw);
      setErrorText(friendly);
      message.error(friendly);
      if (raw !== friendly) console.warn('[Redeem PIN] Raw backend error:', raw);
    } finally {
      setLoading(false);
    }
  };

  const resetResult = () => {
    setResult(null);
    setErrorText('');
    setScannerError('');
  };

  if (result) {
    return (
      <div style={{ maxWidth: 480, margin: '40px auto' }}>
        <Result
          icon={<CheckCircleFilled style={{ color: '#52c41a', fontSize: 72 }} />}
          title={redeemMethod === 'QR' ? 'Купон погашен по QR!' : 'Купон погашен по PIN!'}
          subTitle={
            <Space direction="vertical" size="small">
              <Text strong style={{ fontSize: 18 }}>{result.couponTitle}</Text>
              <Text type="secondary">{result.optionTitle}</Text>
              <Tag color="blue" style={{ fontSize: 14 }}>{result.couponCode}</Tag>
              <Text type="secondary">Мерчант: {result.merchantName}</Text>
              {result.usedAt ? <Text type="secondary">Использован: {new Date(result.usedAt).toLocaleString('ru-RU')}</Text> : null}
              {result.expiresAt ? <Text type="secondary">Действовал до: {new Date(result.expiresAt).toLocaleString('ru-RU')}</Text> : null}
            </Space>
          }
          extra={
            <Button type="primary" size="large" onClick={resetResult} id="redeem-again-btn">
              Погасить ещё
            </Button>
          }
        />
      </div>
    );
  }

  const tabItems = [
    {
      key: 'pin',
      label: <span><NumberOutlined /> PIN-код</span>,
      children: (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Text type="secondary">Введите код купона, который назовёт клиент</Text>
          <Input
            placeholder="CP-XXXX1234"
            value={pinCode}
            onChange={(e) => setPinCode(e.target.value.toUpperCase())}
            size="large"
            style={{ fontSize: 20, textAlign: 'center', letterSpacing: 2 }}
            onPressEnter={handlePinRedeem}
            id="pin-input"
          />
          <Button
            type="primary" size="large" block
            loading={loading} onClick={handlePinRedeem}
            icon={<CheckCircleFilled />}
            id="pin-redeem-btn"
          >
            Погасить
          </Button>
        </Space>
      ),
    },
    {
      key: 'qr',
      label: <span><ScanOutlined /> Сканировать QR</span>,
      children: (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Text type="secondary">Наведите камеру на QR-код купона клиента</Text>

          {isScanning ? (
            <>
              <div id="topdim-qr-reader" style={{ width: '100%', minHeight: 280 }} />
              <Button
                size="large" block danger
                onClick={stopScanner}
                icon={<StopOutlined />}
                id="qr-stop-btn"
              >
                Остановить сканер
              </Button>
            </>
          ) : (
            <Button
              type="primary" size="large" block
              loading={loading}
              onClick={startScanner}
              icon={<CameraOutlined />}
              id="qr-start-btn"
            >
              Открыть камеру
            </Button>
          )}

          {scannerError ? (
            <Alert
              type="warning"
              showIcon
              message="Ошибка сканирования"
              description={scannerError}
              action={
                <Button size="small" onClick={startScanner}>
                  Повторить
                </Button>
              }
            />
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ maxWidth: 520, margin: '24px auto' }}>
      <Title level={3} style={{ textAlign: 'center' }}>🎟️ Погашение купона</Title>
      <Card style={{ borderRadius: 16, boxShadow: '0 4px 16px rgba(0,0,0,0.08)' }}>
        <Tabs items={tabItems} centered size="large" />
        {errorText ? (
          <Alert
            style={{ marginTop: 16 }}
            type="error"
            showIcon
            message="Не удалось погасить купон"
            description={errorText}
          />
        ) : null}
      </Card>
    </div>
  );
}
