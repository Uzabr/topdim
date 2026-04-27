import { useState } from 'react';
import { Card, Input, Button, Typography, message, Result, Space, Tag, Tabs } from 'antd';
import { ScanOutlined, NumberOutlined, CheckCircleFilled } from '@ant-design/icons';
import api from '../api';

const { Title, Text } = Typography;

interface RedeemResult {
  purchasedCouponId: number;
  couponTitle: string;
  optionTitle: string;
  couponCode: string;
  merchantName: string;
  status: string;
}

export default function RedeemPage() {
  const [pinCode, setPinCode] = useState('');
  const [qrToken, setQrToken] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<RedeemResult | null>(null);

  const handlePinRedeem = async () => {
    if (!pinCode.trim()) return message.warning('Введите код купона');
    setLoading(true);
    try {
      const res = await api.post('/api/v1/partner/redemptions', { couponCode: pinCode.trim() });
      setResult(res.data.data);
      message.success('Купон погашен!');
      setPinCode('');
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Ошибка погашения');
    } finally {
      setLoading(false);
    }
  };

  const handleQrRedeem = async () => {
    if (!qrToken.trim()) return message.warning('Введите QR-токен');
    setLoading(true);
    try {
      const res = await api.post('/api/v1/partner/redemptions/qr', { qrToken: qrToken.trim() });
      setResult(res.data.data);
      message.success('Купон погашен по QR!');
      setQrToken('');
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Ошибка погашения');
    } finally {
      setLoading(false);
    }
  };

  const resetResult = () => setResult(null);

  if (result) {
    return (
      <div style={{ maxWidth: 480, margin: '40px auto' }}>
        <Result
          icon={<CheckCircleFilled style={{ color: '#52c41a', fontSize: 72 }} />}
          title="Купон погашен!"
          subTitle={
            <Space direction="vertical" size="small">
              <Text strong style={{ fontSize: 18 }}>{result.couponTitle}</Text>
              <Text type="secondary">{result.optionTitle}</Text>
              <Tag color="blue" style={{ fontSize: 14 }}>{result.couponCode}</Tag>
              <Text type="secondary">Мерчант: {result.merchantName}</Text>
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
      label: <span><ScanOutlined /> QR-токен</span>,
      children: (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Text type="secondary">Отсканируйте QR-код клиента или введите токен</Text>
          <Input
            placeholder="QR-токен"
            value={qrToken}
            onChange={(e) => setQrToken(e.target.value)}
            size="large"
            style={{ fontSize: 18, textAlign: 'center' }}
            onPressEnter={handleQrRedeem}
            id="qr-input"
          />
          <Button
            type="primary" size="large" block
            loading={loading} onClick={handleQrRedeem}
            icon={<ScanOutlined />}
            id="qr-redeem-btn"
          >
            Погасить по QR
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ maxWidth: 520, margin: '24px auto' }}>
      <Title level={3} style={{ textAlign: 'center' }}>🎟️ Погашение купона</Title>
      <Card style={{ borderRadius: 16, boxShadow: '0 4px 16px rgba(0,0,0,0.08)' }}>
        <Tabs items={tabItems} centered size="large" />
      </Card>
    </div>
  );
}
