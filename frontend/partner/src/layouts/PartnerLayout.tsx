import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button, Tag } from 'antd';
import {
  DashboardOutlined, ScanOutlined, TeamOutlined,
  LogoutOutlined, ShopOutlined, GiftOutlined
} from '@ant-design/icons';
import { useMemo } from 'react';

const { Header, Sider, Content } = Layout;

interface PartnerContext {
  role?: string;
  canViewDashboard?: boolean;
  canRedeem?: boolean;
  staffName?: string;
  merchantId?: number;
  merchantLocationId?: number;
}

function getPartnerContext(): PartnerContext {
  try {
    const raw = localStorage.getItem('partnerContext');
    return raw ? JSON.parse(raw) : { role: 'OWNER', canViewDashboard: true, canRedeem: true };
  } catch {
    return { role: 'OWNER', canViewDashboard: true, canRedeem: true };
  }
}

function getUserName(): string {
  try {
    const raw = localStorage.getItem('user');
    if (raw) {
      const u = JSON.parse(raw);
      return u.firstName || u.email || 'Партнёр';
    }
  } catch { /* ignore */ }
  return 'Партнёр';
}

export default function PartnerLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const ctx = useMemo(() => getPartnerContext(), []);
  const userName = useMemo(() => getUserName(), []);

  const isCashier = ctx.role === 'CASHIER';
  const isOwner = ctx.role === 'OWNER' || (!ctx.role);

  const menuItems = useMemo(() => {
    const items = [];

    // Dashboard — only for Owner and Manager
    if (ctx.canViewDashboard || isOwner) {
      items.push({ key: '/', icon: <DashboardOutlined />, label: 'Дашборд' });
    }

    // My Coupons — only for Owner and Manager
    if (isOwner || ctx.canViewDashboard) {
      items.push({ key: '/coupons', icon: <GiftOutlined />, label: 'Мои купоны' });
    }

    // Redeem — for all (cashier, manager, owner)
    if (ctx.canRedeem !== false) {
      items.push({ key: '/redeem', icon: <ScanOutlined />, label: 'Погашение' });
    }

    // Staff management — only for Owner
    if (isOwner) {
      items.push({ key: '/staff', icon: <TeamOutlined />, label: 'Сотрудники' });
    }

    return items;
  }, [ctx, isOwner]);

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    localStorage.removeItem('partnerContext');
    navigate('/login');
  };

  // Determine the role label
  const roleLabel = isCashier ? 'Кассир' : isOwner ? 'Владелец' : ctx.role || 'Партнёр';
  const roleColor = isCashier ? 'blue' : isOwner ? 'green' : 'purple';

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
          <div style={{ color: '#fff', fontSize: 14, marginTop: 8, fontWeight: 600 }}>sizbiz Partner</div>
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
          gap: 12,
          boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
        }}>
          <span style={{ fontSize: 14, color: '#666' }}>
            {ctx.staffName || userName}
          </span>
          <Tag color={roleColor} style={{ margin: 0 }}>{roleLabel}</Tag>
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
