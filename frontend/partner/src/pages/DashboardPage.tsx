import { useQuery } from '@tanstack/react-query';
import { Card, Col, Row, Statistic, Typography, Table, Tag, Spin, Result } from 'antd';
import {
  ShoppingCartOutlined, CheckCircleOutlined, ClockCircleOutlined,
  DollarOutlined, FireOutlined, WarningOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import api from '../api';

const { Title } = Typography;

interface DashboardData {
  totalCoupons: number;
  activeCoupons: number;
  totalSold: number;
  totalRedeemed: number;
  pendingRedemption: number;
  expired: number;
  totalRevenue: number;
  recentRedemptions: Array<{
    couponTitle: string;
    couponCode: string;
    merchantLocationId: number | null;
    staffName: string | null;
    redeemMethod: string | null;
    redeemedAt: string;
  }>;
}

const fetchDashboard = async (): Promise<DashboardData> => {
  const res = await api.get('/api/v1/partner/dashboard');
  return res.data.data;
};

export default function DashboardPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['partner-dashboard'],
    queryFn: fetchDashboard,
  });

  if (isLoading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  if (error) return <Result status="error" title="Ошибка загрузки дашборда" subTitle={(error as Error).message} />;
  if (!data) return null;

  const kpis = [
    { title: 'Купонов', value: data.totalCoupons, icon: <FireOutlined />, color: '#1677ff' },
    { title: 'Продано', value: data.totalSold, icon: <ShoppingCartOutlined />, color: '#52c41a' },
    { title: 'Погашено', value: data.totalRedeemed, icon: <CheckCircleOutlined />, color: '#722ed1' },
    { title: 'Ожидает', value: data.pendingRedemption, icon: <ClockCircleOutlined />, color: '#faad14' },
    { title: 'Просрочено', value: data.expired, icon: <WarningOutlined />, color: '#ff4d4f' },
    { title: 'Выручка', value: data.totalRevenue, icon: <DollarOutlined />, color: '#13c2c2', prefix: '', suffix: ' сум' },
  ];

  const columns = [
    { title: 'Купон', dataIndex: 'couponTitle', key: 'couponTitle' },
    { title: 'Код', dataIndex: 'couponCode', key: 'couponCode', render: (v: string) => <Tag color="blue">{v}</Tag> },
    { title: 'Сотрудник', dataIndex: 'staffName', key: 'staffName', render: (v: string) => v || '—' },
    { title: 'Метод', dataIndex: 'redeemMethod', key: 'redeemMethod',
      render: (v: string) => <Tag color={v === 'QR' ? 'green' : 'orange'}>{v || 'PIN'}</Tag>
    },
    { title: 'Дата', dataIndex: 'redeemedAt', key: 'redeemedAt',
      render: (v: string) => dayjs(v).format('DD.MM.YYYY HH:mm')
    },
  ];

  return (
    <div>
      <Title level={3}>📊 Дашборд</Title>
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        {kpis.map((kpi) => (
          <Col xs={12} sm={8} md={4} key={kpi.title}>
            <Card
              hoverable
              style={{
                borderRadius: 12,
                borderLeft: `4px solid ${kpi.color}`,
                boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
              }}
            >
              <Statistic
                title={kpi.title}
                value={kpi.value}
                prefix={kpi.icon}
                suffix={kpi.suffix}
                valueStyle={{ color: kpi.color, fontSize: 22, fontWeight: 600 }}
              />
            </Card>
          </Col>
        ))}
      </Row>

      <Card title="Последние погашения" style={{ borderRadius: 12 }}>
        <Table
          dataSource={data.recentRedemptions}
          columns={columns}
          rowKey="couponCode"
          pagination={false}
          size="middle"
          locale={{ emptyText: 'Нет погашений' }}
        />
      </Card>
    </div>
  );
}
