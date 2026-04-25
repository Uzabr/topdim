import { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Avatar, Dropdown, Typography, theme } from 'antd';
import {
  DashboardOutlined,
  SafetyCertificateOutlined,
  CommentOutlined,
  StarOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  TeamOutlined,
  SettingOutlined,
  AuditOutlined,
  LogoutOutlined,
  UserOutlined,
  AppstoreOutlined,
  TagOutlined,
  FileTextOutlined,
  ProjectOutlined,
} from '@ant-design/icons';
import { useAuthStore } from '../../store/authStore';
import type { UserRole } from '../../types';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

interface MenuItem {
  key: string;
  icon: React.ReactNode;
  label: string;
  roles: UserRole[];
  children?: MenuItem[];
}

const allMenuItems: MenuItem[] = [
  {
    key: '/dashboard',
    icon: <DashboardOutlined />,
    label: 'Дашборд',
    roles: ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'],
  },
  {
    key: '/moderation',
    icon: <AppstoreOutlined />,
    label: 'Контент (Купоны)',
    roles: ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'],
    children: [
      { key: '/moderation/coupons', icon: <TagOutlined />, label: 'Все купоны', roles: ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'] },
      { key: '/moderation/coupons/kanban', icon: <ProjectOutlined />, label: 'Канбан-доска', roles: ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'] },
      { key: '/moderation/coupons/review', icon: <SafetyCertificateOutlined />, label: 'Решения мерчанта', roles: ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'] },
      { key: '/moderation/coupons/create', icon: <FileTextOutlined />, label: 'Создать купон', roles: ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    key: '/support',
    icon: <SafetyCertificateOutlined />,
    label: 'Поддержка',
    roles: ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'],
    children: [
      { key: '/support/complaints', icon: <CommentOutlined />, label: 'Жалобы', roles: ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'] },
      { key: '/support/reviews', icon: <StarOutlined />, label: 'Отзывы', roles: ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    key: '/catalog',
    icon: <ShopOutlined />,
    label: 'Справочники',
    roles: ['ADMIN', 'SUPER_ADMIN'],
    children: [
      { key: '/catalog/categories', icon: <AppstoreOutlined />, label: 'Категории', roles: ['ADMIN', 'SUPER_ADMIN'] },
      { key: '/catalog/bazaars', icon: <ShopOutlined />, label: 'Базары', roles: ['ADMIN', 'SUPER_ADMIN'] },
      { key: '/catalog/shops', icon: <ShopOutlined />, label: 'Магазины', roles: ['ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    key: '/orders',
    icon: <ShoppingCartOutlined />,
    label: 'Заказы',
    roles: ['ADMIN', 'SUPER_ADMIN'],
    children: [
      { key: '/orders/list', icon: <FileTextOutlined />, label: 'Все заказы', roles: ['ADMIN', 'SUPER_ADMIN'] },
      { key: '/orders/promocodes', icon: <TagOutlined />, label: 'Промокоды', roles: ['ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    key: '/users',
    icon: <TeamOutlined />,
    label: 'Пользователи',
    roles: ['ADMIN', 'SUPER_ADMIN'],
    children: [
      { key: '/users/list', icon: <UserOutlined />, label: 'Все пользователи', roles: ['ADMIN', 'SUPER_ADMIN'] },
      { key: '/users/partner-applications', icon: <FileTextOutlined />, label: 'Заявки партнёров', roles: ['ADMIN', 'SUPER_ADMIN'] },
    ],
  },
  {
    key: '/system',
    icon: <SettingOutlined />,
    label: 'Система',
    roles: ['SUPER_ADMIN'],
    children: [
      { key: '/system/staff', icon: <TeamOutlined />, label: 'Сотрудники', roles: ['SUPER_ADMIN'] },
      { key: '/system/audit', icon: <AuditOutlined />, label: 'Журнал действий', roles: ['SUPER_ADMIN'] },
    ],
  },
  {
    key: '/partner',
    icon: <SafetyCertificateOutlined />,
    label: 'Партнёр',
    roles: ['PARTNER', 'ADMIN', 'SUPER_ADMIN'],
    children: [
      { key: '/partner/redeem', icon: <TagOutlined />, label: 'Погашение купонов', roles: ['PARTNER', 'ADMIN', 'SUPER_ADMIN'] },
    ],
  },
];

function filterMenuByRole(items: MenuItem[], role: UserRole): MenuItem[] {
  return items
    .filter((item) => item.roles.includes(role))
    .map((item) => ({
      ...item,
      children: item.children ? filterMenuByRole(item.children, role) : undefined,
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
            {collapsed ? 'TD' : 'TopDim Admin'}
          </Text>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          defaultOpenKeys={visibleMenu.filter((m) => m.children).map((m) => m.key)}
          items={visibleMenu as any}
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
