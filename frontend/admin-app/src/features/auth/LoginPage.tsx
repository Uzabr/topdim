import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Form, Input, Button, Typography, App, Divider, Space } from 'antd';
import { LockOutlined, MailOutlined, CrownOutlined, SafetyCertificateOutlined, UserOutlined } from '@ant-design/icons';
import { useAuthStore } from '../../store/authStore';
import api from '../../api/client';
import type { AuthUser, UserRole } from '../../types';

const { Title, Text } = Typography;

const ALLOWED_ROLES: UserRole[] = ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'];

// ⚠️ DEMO: Удалить перед продакшеном!
const DEMO_USERS: Record<string, AuthUser> = {
  SUPER_ADMIN: {
    id: 1, email: 'superadmin@topdim.uz', phone: '+998900000001',
    firstName: 'Аброр', lastName: 'Суперадмин', role: 'SUPER_ADMIN', avatarUrl: null,
  },
  ADMIN: {
    id: 2, email: 'admin@topdim.uz', phone: '+998900000002',
    firstName: 'Админ', lastName: 'Менеджер', role: 'ADMIN', avatarUrl: null,
  },
  MODERATOR: {
    id: 3, email: 'moderator@topdim.uz', phone: '+998900000003',
    firstName: 'Модер', lastName: 'Контент', role: 'MODERATOR', avatarUrl: null,
  },
};

export const LoginPage = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);
  const { message } = App.useApp();

  const onFinish = async (values: { email: string; password: string }) => {
    setLoading(true);
    try {
      const { data } = await api.post('/api/v1/auth/login', values);
      const { accessToken, refreshToken, user } = data.data;

      if (!ALLOWED_ROLES.includes(user.role)) {
        message.error('У вас нет прав для доступа к админ-панели.');
        return;
      }

      login(accessToken, refreshToken, user);
      message.success(`Добро пожаловать, ${user.firstName}!`);
      navigate('/dashboard');
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      message.error(error.response?.data?.message || 'Ошибка входа');
    } finally {
      setLoading(false);
    }
  };

  // ⚠️ DEMO: Вход без бэкенда
  const demoLogin = (role: keyof typeof DEMO_USERS) => {
    const user = DEMO_USERS[role];
    login('demo-token-' + role, 'demo-refresh-' + role, user);
    message.success(`Демо-вход: ${user.firstName} (${role})`);
    navigate('/dashboard');
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #001529 0%, #003a70 100%)',
      }}
    >
      <Card
        style={{
          width: 420,
          borderRadius: 12,
          boxShadow: '0 8px 32px rgba(0,0,0,0.3)',
        }}
      >
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <Title level={3} style={{ marginBottom: 4 }}>
            TopDim Admin
          </Title>
          <Text type="secondary">Войдите в панель управления</Text>
        </div>

        <Form
          name="login"
          onFinish={onFinish}
          autoComplete="off"
          layout="vertical"
          size="large"
        >
          <Form.Item
            name="email"
            rules={[
              { required: true, message: 'Введите email' },
              { type: 'email', message: 'Невалидный email' },
            ]}
          >
            <Input prefix={<MailOutlined />} placeholder="Email" />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: 'Введите пароль' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="Пароль" />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              Войти
            </Button>
          </Form.Item>
        </Form>

        <Divider plain>
          <Text type="secondary" style={{ fontSize: 12 }}>⚠️ ДЕМО (без бэкенда)</Text>
        </Divider>

        <Space direction="vertical" style={{ width: '100%' }} size="small">
          <Button
            icon={<CrownOutlined />}
            block
            onClick={() => demoLogin('SUPER_ADMIN')}
            style={{ background: '#722ed1', borderColor: '#722ed1', color: '#fff' }}
          >
            👑 Войти как Super Admin
          </Button>
          <Button
            icon={<SafetyCertificateOutlined />}
            block
            onClick={() => demoLogin('ADMIN')}
            style={{ background: '#1890ff', borderColor: '#1890ff', color: '#fff' }}
          >
            💼 Войти как Admin
          </Button>
          <Button
            icon={<UserOutlined />}
            block
            onClick={() => demoLogin('MODERATOR')}
            style={{ background: '#52c41a', borderColor: '#52c41a', color: '#fff' }}
          >
            🛡️ Войти как Moderator
          </Button>
        </Space>
      </Card>
    </div>
  );
};
