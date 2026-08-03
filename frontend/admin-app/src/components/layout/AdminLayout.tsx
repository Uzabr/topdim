import { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Avatar, Dropdown, Typography, theme } from 'antd';
import type { MenuProps } from 'antd';
import {
  LogoutOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useAuthStore } from '../../store/authStore';
import {
  allMenuItems,
  filterMenuByRole,
  type MenuItem,
} from './adminMenu';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

function toAntMenuItems(items: MenuItem[]): MenuProps['items'] {
  return items.map(({ key, icon, label, children }) => ({
    key,
    icon,
    label,
    children: children ? toAntMenuItems(children) : undefined,
  }));
}

export const AdminLayout = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuthStore();
  const { token: themeToken } = theme.useToken();

  const userRole = user?.role || 'MODERATOR';
  const visibleMenu = filterMenuByRole(allMenuItems, userRole);
  const menuItems = toAntMenuItems(visibleMenu);
  const selectedMenuKey = location.pathname.startsWith('/coupons')
    ? '/coupons'
    : location.pathname;

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const userDropdownItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: `${user?.firstName} ${user?.lastName}`,
      disabled: true,
    },
    {
      key: 'role',
      label: `Роль: ${userRole}`,
      disabled: true,
    },
    { type: 'divider' as const },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Выйти',
      danger: true,
      onClick: handleLogout,
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        theme="dark"
        width={260}
        style={{
          overflow: 'auto',
          height: '100vh',
          position: 'fixed',
          left: 0,
          top: 0,
          bottom: 0,
        }}
      >
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderBottom: '1px solid rgba(255,255,255,0.1)',
          }}
        >
          <Text
            strong
            style={{
              color: '#fff',
              fontSize: collapsed ? 16 : 20,
              letterSpacing: 1,
            }}
          >
            {collapsed ? 'sizbiz' : 'sizbiz Admin'}
          </Text>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedMenuKey]}
          defaultOpenKeys={visibleMenu.filter((m) => m.children).map((m) => m.key)}
          items={menuItems}
          onClick={handleMenuClick}
        />
      </Sider>

      <Layout style={{ marginLeft: collapsed ? 80 : 260, transition: 'margin-left 0.2s' }}>
        <Header
          style={{
            padding: '0 24px',
            background: themeToken.colorBgContainer,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            borderBottom: `1px solid ${themeToken.colorBorderSecondary}`,
          }}
        >
          <Dropdown menu={{ items: userDropdownItems }} placement="bottomRight">
            <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Avatar icon={<UserOutlined />} style={{ backgroundColor: themeToken.colorPrimary }} />
              {!collapsed && (
                <Text>{user?.firstName} {user?.lastName}</Text>
              )}
            </div>
          </Dropdown>
        </Header>

        <Content
          style={{
            margin: 24,
            padding: 24,
            background: themeToken.colorBgContainer,
            borderRadius: themeToken.borderRadiusLG,
            minHeight: 280,
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};
