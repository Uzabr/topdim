import { Card, Col, Row, Statistic, Typography } from 'antd';
import {
  ShoppingCartOutlined,
  TagOutlined,
  TeamOutlined,
  AlertOutlined,
} from '@ant-design/icons';
import { useAuthStore } from '../../store/authStore';

const { Title } = Typography;

export const DashboardPage = () => {
  const user = useAuthStore((s) => s.user);

  return (
    <div>
      <Title level={4}>
        Добро пожаловать, {user?.firstName}! 👋
      </Title>

      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderLeft: '4px solid #1890ff' }}>
            <Statistic
              title="Заказы сегодня"
              value={0}
              prefix={<ShoppingCartOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderLeft: '4px solid #52c41a' }}>
            <Statistic
              title="Активных купонов"
              value={0}
              prefix={<TagOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderLeft: '4px solid #722ed1' }}>
            <Statistic
              title="Пользователей"
              value={0}
              prefix={<TeamOutlined />}
              valueStyle={{ color: '#722ed1' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderLeft: '4px solid #faad14' }}>
            <Statistic
              title="Жалоб (ожидают)"
              value={0}
              prefix={<AlertOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        <Col xs={24} lg={16}>
          <Card title="Продажи за последние 7 дней" bordered={false}>
            <div style={{ height: 300, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>
              📊 График будет здесь (Recharts)
            </div>
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="Последние действия" bordered={false}>
            <div style={{ height: 300, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>
              📋 Лента событий
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
};
