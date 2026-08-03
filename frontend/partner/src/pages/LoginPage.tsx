import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Form, Input, Button, Typography, message, Space } from 'antd';
import { ShopOutlined, LockOutlined, MailOutlined } from '@ant-design/icons';
import api from '../api';

const { Title, Text } = Typography;

export default function LoginPage() {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const onFinish = async (values: { email: string; password: string }) => {
    setLoading(true);
    try {
      const res = await api.post('/api/v1/auth/login', values);
      const { accessToken, user } = res.data.data;
      localStorage.setItem('token', accessToken);
      localStorage.setItem('user', JSON.stringify(user));

      // Fetch partner access context to determine role (OWNER/CASHIER/MANAGER)
      try {
        const ctxRes = await api.get('/api/v1/partner/staff/me', {
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        if (ctxRes.data?.data) {
          localStorage.setItem('partnerContext', JSON.stringify(ctxRes.data.data));
        }
      } catch {
        // If access context fails, user is OWNER by default
        localStorage.setItem('partnerContext', JSON.stringify({ role: 'OWNER', canViewDashboard: true, canRedeem: true }));
      }

      message.success('Добро пожаловать!');
      navigate('/');
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      message.error(e.response?.data?.message || 'Ошибка входа');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      display: 'flex', justifyContent: 'center', alignItems: 'center',
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
    }}>
      <Card
        style={{
          width: 420, borderRadius: 16,
          boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
          background: 'rgba(255,255,255,0.95)',
          backdropFilter: 'blur(10px)',
        }}
      >
        <Space direction="vertical" size="large" style={{ width: '100%', textAlign: 'center' }}>
          <ShopOutlined style={{ fontSize: 48, color: '#1677ff' }} />
          <Title level={3} style={{ margin: 0 }}>Кабинет партнёра</Title>
          <Text type="secondary">sizbiz Partner Dashboard</Text>
        </Space>

        <Form layout="vertical" onFinish={onFinish} style={{ marginTop: 32 }}>
          <Form.Item name="email" rules={[{ required: true, message: 'Введите email' }]}>
            <Input prefix={<MailOutlined />} placeholder="Email" size="large" id="login-email" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: 'Введите пароль' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="Пароль" size="large" id="login-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={loading} block size="large" id="login-submit">
            Войти
          </Button>
        </Form>
      </Card>
    </div>
  );
}
