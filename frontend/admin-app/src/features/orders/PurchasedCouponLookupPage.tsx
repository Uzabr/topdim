import { useState } from 'react';
import { Card, Input, Button, Typography, Descriptions, Tag, Alert, Space, Spin, Empty } from 'antd';
import { SearchOutlined, TagOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { lookupPurchasedCoupon, type AdminPurchasedCouponLookup } from './api';

const { Title, Text } = Typography;

const statusColors: Record<string, string> = {
  ACTIVE: 'green',
  USED: 'blue',
  EXPIRED: 'orange',
  REFUNDED: 'purple',
};

const statusLabels: Record<string, string> = {
  ACTIVE: 'Активен',
  USED: 'Использован',
  EXPIRED: 'Истёк',
  REFUNDED: 'Возвращён',
};

export function PurchasedCouponLookupPage() {
  const [searchCode, setSearchCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<AdminPurchasedCouponLookup | null>(null);
  const [errorMsg, setErrorMsg] = useState('');

  const handleSearch = async () => {
    if (!searchCode.trim()) return;
    setLoading(true);
    setResult(null);
    setErrorMsg('');
    try {
      const data = await lookupPurchasedCoupon(searchCode);
      setResult(data);
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Купон не найден');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <Title level={3} style={{ marginBottom: 16 }}><TagOutlined /> Поиск купленного купона</Title>

      <Card style={{ borderRadius: 12, marginBottom: 24 }}>
        <Space.Compact style={{ width: '100%', maxWidth: 480 }}>
          <Input
            placeholder="Введите код купона (CP-XXXX1234)"
            value={searchCode}
            onChange={(e) => setSearchCode(e.target.value.toUpperCase())}
            onPressEnter={handleSearch}
            size="large"
            style={{ letterSpacing: 1.5, fontFamily: 'monospace' }}
            id="coupon-lookup-input"
          />
          <Button
            type="primary" size="large"
            icon={<SearchOutlined />}
            loading={loading}
            onClick={handleSearch}
            id="coupon-lookup-btn"
          >
            Найти
          </Button>
        </Space.Compact>
      </Card>

      {loading && <Spin size="large" style={{ display: 'block', margin: '40px auto' }} />}

      {errorMsg && (
        <Alert
          type="warning" showIcon
          message="Купон не найден"
          description={errorMsg}
          style={{ maxWidth: 600 }}
        />
      )}

      {result && (
        <Card title="Результат поиска" style={{ borderRadius: 12, maxWidth: 700 }}>
          <Descriptions bordered column={1} size="middle">
            <Descriptions.Item label="ID купона">{result.purchasedCouponId}</Descriptions.Item>
            <Descriptions.Item label="Код">
              <Text copyable strong style={{ fontFamily: 'monospace', fontSize: 16 }}>{result.couponCode}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Статус">
              <Tag color={statusColors[result.status] || 'default'} style={{ fontSize: 14 }}>
                {statusLabels[result.status] || result.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Купон">{result.couponTitle}</Descriptions.Item>
            <Descriptions.Item label="Опция">{result.optionTitle || '—'}</Descriptions.Item>
            <Descriptions.Item label="ID заказа">
              {result.orderId || '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Покупатель (userId)">{result.userId}</Descriptions.Item>
            <Descriptions.Item label="Мерчант">
              {result.merchantName || '—'} (ID: {result.merchantId || '—'})
            </Descriptions.Item>
            <Descriptions.Item label="Адрес мерчанта">{result.merchantAddress || '—'}</Descriptions.Item>
            <Descriptions.Item label="Куплен">
              {result.purchasedAt ? dayjs(result.purchasedAt).format('DD.MM.YYYY HH:mm') : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Действует до">
              {result.expiresAt ? dayjs(result.expiresAt).format('DD.MM.YYYY HH:mm') : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Использован">
              {result.usedAt ? dayjs(result.usedAt).format('DD.MM.YYYY HH:mm') : '—'}
            </Descriptions.Item>
          </Descriptions>

          <Alert
            type="info" showIcon
            message="Только для чтения"
            description="Данная страница предназначена для поддержки. Погашение купонов выполняется партнёром через Partner App."
            style={{ marginTop: 16 }}
          />
        </Card>
      )}

      {!loading && !result && !errorMsg && (
        <Empty description="Введите код купона для поиска" style={{ marginTop: 40 }} />
      )}
    </div>
  );
}
