import { useQuery } from '@tanstack/react-query';
import { Card, Table, Tag, Typography, Spin, Result, Space } from 'antd';
import {
  CheckCircleOutlined, ClockCircleOutlined, EditOutlined,
  StopOutlined, FireOutlined, EyeOutlined
} from '@ant-design/icons';
import api from '../api';

const { Title, Text } = Typography;

interface CouponItem {
  id: number;
  title: string;
  offerDescription?: string;
  oldPrice?: number;
  fromPrice: number;
  discountPercent?: number;
  coverImageUrl?: string;
  status: string;
  totalSold: number;
}

const fetchMyCoupons = async (): Promise<{ content: CouponItem[]; totalElements: number }> => {
  const res = await api.get('/api/v1/partner/coupons', { params: { page: 0, size: 50 } });
  return res.data.data;
};

const STATUS_CONFIG: Record<string, { color: string; label: string; icon: React.ReactNode }> = {
  ACTIVE:             { color: 'green',   label: 'Активен',     icon: <CheckCircleOutlined /> },
  LEAD:               { color: 'default', label: 'Заявка',      icon: <EditOutlined /> },
  DRAFT:              { color: 'blue',    label: 'Черновик',     icon: <EditOutlined /> },
  MODERATION:         { color: 'orange',  label: 'Модерация',   icon: <ClockCircleOutlined /> },
  APPROVED:           { color: 'cyan',    label: 'Одобрен',     icon: <CheckCircleOutlined /> },
  REVISION_REQUESTED: { color: 'warning', label: 'Доработка',   icon: <EditOutlined /> },
  REJECTED:           { color: 'red',     label: 'Отклонён',    icon: <StopOutlined /> },
  SOLD_OUT:           { color: 'volcano', label: 'Распродан',   icon: <FireOutlined /> },
  PAUSED:             { color: 'gold',    label: 'Приостановлен',icon: <ClockCircleOutlined /> },
  EXPIRED:            { color: 'default', label: 'Истёк',       icon: <StopOutlined /> },
  ARCHIVED:           { color: 'default', label: 'Архив',       icon: <StopOutlined /> },
};

function formatPrice(value?: number) {
  if (!value) return '—';
  return value.toLocaleString('ru-RU') + ' сум';
}

export default function CouponsPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['partner-coupons'],
    queryFn: fetchMyCoupons,
  });

  if (isLoading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  if (error) return <Result status="error" title="Ошибка загрузки купонов" subTitle={(error as Error).message} />;

  const coupons = data?.content || [];

  const columns = [
    {
      title: '',
      dataIndex: 'coverImageUrl',
      key: 'cover',
      width: 60,
      render: (url: string) => url
        ? <img src={url} alt="" style={{ width: 48, height: 48, objectFit: 'cover', borderRadius: 8 }} />
        : <div style={{ width: 48, height: 48, background: '#f0f0f0', borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <EyeOutlined style={{ color: '#bbb' }} />
          </div>,
    },
    {
      title: 'Название',
      dataIndex: 'title',
      key: 'title',
      render: (title: string, record: CouponItem) => (
        <Space direction="vertical" size={0}>
          <Text strong>{title}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>#{record.id}</Text>
        </Space>
      ),
    },
    {
      title: 'Цена',
      key: 'price',
      render: (_: unknown, record: CouponItem) => (
        <Space direction="vertical" size={0}>
          <Text strong style={{ color: '#1677ff' }}>{formatPrice(record.fromPrice)}</Text>
          {record.oldPrice ? (
            <Text delete type="secondary" style={{ fontSize: 12 }}>{formatPrice(record.oldPrice)}</Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: 'Скидка',
      dataIndex: 'discountPercent',
      key: 'discount',
      width: 80,
      render: (v: number) => v ? <Tag color="red">-{v}%</Tag> : '—',
    },
    {
      title: 'Продано',
      dataIndex: 'totalSold',
      key: 'totalSold',
      width: 90,
      render: (v: number) => <Text strong>{v}</Text>,
    },
    {
      title: 'Статус',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (status: string) => {
        const cfg = STATUS_CONFIG[status] || { color: 'default', label: status, icon: null };
        return <Tag icon={cfg.icon} color={cfg.color}>{cfg.label}</Tag>;
      },
    },
  ];

  return (
    <div>
      <Title level={3}>🎟️ Мои купоны</Title>
      <Card style={{ borderRadius: 12 }}>
        <Table
          dataSource={coupons}
          columns={columns}
          rowKey="id"
          pagination={coupons.length > 10 ? { pageSize: 10 } : false}
          size="middle"
          locale={{ emptyText: 'У вас пока нет купонов' }}
        />
      </Card>
    </div>
  );
}
