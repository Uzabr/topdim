import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Card, Table, Tag, Typography, Spin, Result, Space, Button, Tooltip } from 'antd';
import {
  CheckCircleOutlined, ClockCircleOutlined, EditOutlined,
  StopOutlined, FireOutlined, EyeOutlined, PlusOutlined,
  ExclamationCircleOutlined, SendOutlined
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
  revisionComment?: string;
  createdAt?: string;
}

const fetchMyCoupons = async (): Promise<{ content: CouponItem[]; totalElements: number }> => {
  const res = await api.get('/api/v1/partner/coupons', { params: { page: 0, size: 50 } });
  return res.data.data;
};

const STATUS_CONFIG: Record<string, { color: string; label: string; icon: React.ReactNode }> = {
  ACTIVE:                { color: 'green',   label: 'Опубликована',        icon: <CheckCircleOutlined /> },
  LEAD:                  { color: 'purple',  label: 'Новая заявка',        icon: <SendOutlined /> },
  DRAFT:                 { color: 'blue',    label: 'В работе у sizbiz',   icon: <EditOutlined /> },
  WAITING_FOR_MERCHANT:  { color: 'orange',  label: 'На согласовании',     icon: <ClockCircleOutlined /> },
  REVISION_REQUESTED:    { color: 'gold',    label: 'Нужны уточнения',     icon: <ExclamationCircleOutlined /> },
  SOLD_OUT:              { color: 'volcano', label: 'Распродан',           icon: <FireOutlined /> },
  PAUSED:                { color: 'gold',    label: 'Приостановлен',       icon: <ClockCircleOutlined /> },
  ARCHIVED:              { color: 'default', label: 'Отклонена/Архив',     icon: <StopOutlined /> },
};

const EDITABLE_STATUSES = new Set(['LEAD', 'DRAFT', 'REVISION_REQUESTED']);

function formatPrice(value?: number) {
  if (!value) return '—';
  return value.toLocaleString('ru-RU') + ' сум';
}

export default function CouponsPage() {
  const navigate = useNavigate();
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
      key: 'status',
      width: 200,
      render: (_: unknown, record: CouponItem) => {
        const cfg = STATUS_CONFIG[record.status] || { color: 'default', label: record.status, icon: null };
        return (
          <Space direction="vertical" size={0}>
            <Tag icon={cfg.icon} color={cfg.color}>{cfg.label}</Tag>
            {record.status === 'REVISION_REQUESTED' && record.revisionComment && (
              <Tooltip title={record.revisionComment}>
                <Text type="warning" style={{ fontSize: 11, cursor: 'help' }}>
                  💬 {record.revisionComment.substring(0, 40)}...
                </Text>
              </Tooltip>
            )}
            {record.status === 'ARCHIVED' && record.revisionComment && (
              <Text type="secondary" style={{ fontSize: 11 }}>
                Причина: {record.revisionComment.substring(0, 40)}
              </Text>
            )}
            {record.status === 'WAITING_FOR_MERCHANT' && (
              <Text type="secondary" style={{ fontSize: 11 }}>
                Откройте preview и подтвердите запуск
              </Text>
            )}
          </Space>
        );
      },
    },
    {
      title: '',
      key: 'actions',
      width: 120,
      render: (_: unknown, record: CouponItem) => {
        if (record.status === 'WAITING_FOR_MERCHANT') {
          return (
            <Button
              type="link"
              icon={<EyeOutlined />}
              size="small"
              onClick={() => navigate(`/coupons/${record.id}/review`)}
            >
              Согласовать
            </Button>
          );
        }

        if (EDITABLE_STATUSES.has(record.status)) {
          return (
            <Button type="link" icon={<EditOutlined />} size="small">
              Изменить
            </Button>
          );
        }

        return null;
      },
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>🎟️ Мои предложения</Title>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          size="large"
          onClick={() => navigate('/coupons/new')}
        >
          Создать заявку
        </Button>
      </div>
      <Card style={{ borderRadius: 12 }}>
        <Table
          dataSource={coupons}
          columns={columns}
          rowKey="id"
          pagination={coupons.length > 10 ? { pageSize: 10 } : false}
          size="middle"
          locale={{ emptyText: 'У вас пока нет предложений. Подайте первое предложение!' }}
        />
      </Card>
    </div>
  );
}
