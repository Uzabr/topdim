import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Table, Tag, Input, Select, Typography, Space, Button,
} from 'antd';
import {
  SearchOutlined, CheckCircleOutlined, CloseCircleOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { fetchMerchantPage } from './api';
import type { AdminMerchantSummary } from './types';
import type { ColumnsType } from 'antd/es/table';

const { Title } = Typography;

export const MerchantsPage = () => {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [activeFilter, setActiveFilter] = useState<boolean | undefined>(undefined);
  const navigate = useNavigate();

  const { data, isLoading } = useQuery({
    queryKey: ['admin-merchants', page, search, activeFilter],
    queryFn: () =>
      fetchMerchantPage({
        page,
        size: 20,
        search: search || undefined,
        active: activeFilter,
      }),
  });

  const columns: ColumnsType<AdminMerchantSummary> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    {
      title: 'Мерчант',
      dataIndex: 'name',
      render: (name: string, record) => (
        <Space>
          {record.logoUrl && (
            <img
              src={record.logoUrl}
              alt=""
              style={{ width: 28, height: 28, borderRadius: 4, objectFit: 'cover' }}
            />
          )}
          <span style={{ fontWeight: 500 }}>{name}</span>
        </Space>
      ),
    },
    {
      title: 'Статус',
      dataIndex: 'active',
      width: 100,
      render: (active: boolean) =>
        active ? (
          <Tag icon={<CheckCircleOutlined />} color="success">Активен</Tag>
        ) : (
          <Tag icon={<CloseCircleOutlined />} color="default">Не активен</Tag>
        ),
    },
    {
      title: 'Готов к публикации',
      width: 160,
      render: (_, record) =>
        record.publicationReady ? (
          <Tag color="green">Готов</Tag>
        ) : (
          <Tag color="orange">{record.publicationBlockReason || 'Не готов'}</Tag>
        ),
    },
    {
      title: 'Адрес',
      render: (_, record) => record.primaryLocation?.address || '—',
      ellipsis: true,
    },
    {
      title: 'Телефон',
      render: (_, record) => record.primaryLocation?.phone || '—',
      width: 150,
    },
    {
      title: 'Купоны',
      width: 130,
      render: (_, record) => (
        <Space size={4}>
          <Tag color="green">{record.activeCouponsCount}</Tag>
          <Tag color="orange">{record.waitingCouponsCount}</Tag>
          <Tag>{record.totalCouponsCount}</Tag>
        </Space>
      ),
    },
    {
      title: 'Действия',
      width: 100,
      render: (_, record) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={() => navigate(`/catalog/merchants/${record.id}`)}
        >
          Детали
        </Button>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>Мерчанты</Title>
      </div>

      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          placeholder="Поиск по имени, email, контакту..."
          prefix={<SearchOutlined />}
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          style={{ width: 320, maxWidth: '100%' }}
          allowClear
        />
        <Select
          placeholder="Статус"
          value={activeFilter}
          onChange={(v) => { setActiveFilter(v); setPage(0); }}
          style={{ width: 160, maxWidth: '100%' }}
          allowClear
          options={[
            { value: true, label: 'Активные' },
            { value: false, label: 'Неактивные' },
          ]}
        />
      </Space>

      <Table
        columns={columns}
        dataSource={data?.content}
        loading={isLoading}
        rowKey="id"
        pagination={{
          current: page + 1,
          pageSize: 20,
          total: data?.totalElements,
          onChange: (p) => setPage(p - 1),
          showTotal: (total) => `Всего: ${total}`,
        }}
        scroll={{ x: 900 }}
      />
    </div>
  );
};
