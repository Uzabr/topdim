import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button } from 'antd';
import {
  DashboardOutlined, ScanOutlined, TeamOutlined,
  LogoutOutlined, ShopOutlined, GiftOutlined
} from '@ant-design/icons';

const { Header, Sider, Content } = Layout;

export default function PartnerLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems = [
    { key: '/', icon: <DashboardOutlined />, label: 'Дашборд' },
    { key: '/coupons', icon: <GiftOutlined />, label: 'Мои купоны' },
    { key: '/redeem', icon: <ScanOutlined />, label: 'Погашение' },
    { key: '/staff', icon: <TeamOutlined />, label: 'Сотрудники' },
  ];

  const handleLogout = () => {
    localStorage.removeItem('token');
    navigate('/login');
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        breakpoint="lg"
        collapsedWidth={64}
        style={{
          background: 'linear-gradient(180deg, #1a1a2e 0%, #16213e 100%)',
          boxShadow: '2px 0 8px rgba(0,0,0,0.15)',
        }}
      >
        <div style={{ padding: '20px 16px', textAlign: 'center' }}>
          <ShopOutlined style={{ fontSize: 28, color: '#1677ff' }} />
          <div style={{ color: '#fff', fontSize: 14, marginTop: 8, fontWeight: 600 }}>TopDim Partner</div>
        </div>

        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          onClick={({ key }) => navigate(key)}
          items={menuItems}
          style={{ background: 'transparent', borderRight: 'none' }}
        />
      </Sider>

      <Layout>
        <Header style={{
          background: '#fff', padding: '0 24px',
          display: 'flex', justifyContent: 'flex-end', alignItems: 'center',
          boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
        }}>
          <Button type="text" icon={<LogoutOutlined />} onClick={handleLogout} id="logout-btn">
            Выйти
          </Button>
        </Header>
        <Content style={{ margin: 24, padding: 24, background: '#f5f5f5', minHeight: 280, borderRadius: 8 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
