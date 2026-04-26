import { useState } from 'react';
import { Table, Tag, Button, Space, Typography, App, Modal, Form, Input, Descriptions } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, EyeOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../../api/client';
import type { PartnerApplication, ApiResponse, PageResponse } from '../../types';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';

const { Title } = Typography;

const statusColors: Record<string, string> = {
  PENDING: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
};

interface ApprovePayload {
  loginEmail: string;
  temporaryPassword: string;
  merchantName: string;
  contactPerson: string;
  address: string;
  phone: string;
  city: string;
  workingHours: string;
  website: string;
}

interface RejectPayload {
  reason: string;
}

export const PartnerApplicationsPage = () => {
  const [page, setPage] = useState(0);
  const [approveTarget, setApproveTarget] = useState<PartnerApplication | null>(null);
  const [rejectTarget, setRejectTarget] = useState<PartnerApplication | null>(null);
  const [detailTarget, setDetailTarget] = useState<PartnerApplication | null>(null);
  const [approveForm] = Form.useForm<ApprovePayload>();
  const [rejectForm] = Form.useForm<RejectPayload>();
  const queryClient = useQueryClient();
  const { message } = App.useApp();

  const { data, isLoading } = useQuery({
    queryKey: ['partner-applications', page],
    queryFn: async () => {
      const res = await api.get<ApiResponse<PageResponse<PartnerApplication>>>(
        '/api/v1/admin/partner-applications',
        { params: { page, size: 20 } },
      );
      return res.data.data;
    },
  });

  const approveMutation = useMutation({
    mutationFn: (args: { id: number; payload: ApprovePayload }) =>
      api.patch(`/api/v1/admin/partner-applications/${args.id}/approve`, args.payload),
    onSuccess: () => {
      message.success('Заявка одобрена — партнёр и мерчант созданы');
      queryClient.invalidateQueries({ queryKey: ['partner-applications'] });
      setApproveTarget(null);
      approveForm.resetFields();
    },
    onError: () => message.error('Ошибка при одобрении заявки'),
  });

  const rejectMutation = useMutation({
    mutationFn: (args: { id: number; payload: RejectPayload }) =>
      api.patch(`/api/v1/admin/partner-applications/${args.id}/reject`, args.payload),
    onSuccess: () => {
      message.info('Заявка отклонена');
      queryClient.invalidateQueries({ queryKey: ['partner-applications'] });
      setRejectTarget(null);
      rejectForm.resetFields();
    },
    onError: () => message.error('Ошибка при отклонении заявки'),
  });

  const openApproveModal = (record: PartnerApplication) => {
    setApproveTarget(record);
    approveForm.setFieldsValue({
      loginEmail: record.email || '',
      temporaryPassword: '',
      merchantName: record.companyName,
      contactPerson: `${record.firstName} ${record.lastName}`,
      address: record.address || '',
      phone: record.phone,
      city: record.city || '',
      workingHours: record.workingHours || '',
      website: record.website || '',
    });
  };

  const columns: ColumnsType<PartnerApplication> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    {
      title: 'Имя',
      render: (_, record) => `${record.firstName} ${record.lastName}`,
    },
    { title: 'Телефон', dataIndex: 'phone' },
    { title: 'Компания', dataIndex: 'companyName' },
    { title: 'Город', dataIndex: 'city' },
    {
      title: 'Статус',
      dataIndex: 'status',
      render: (status: string) => <Tag color={statusColors[status]}>{status}</Tag>,
    },
    {
      title: 'Дата',
      dataIndex: 'createdAt',
      render: (date: string) => dayjs(date).format('DD.MM.YYYY HH:mm'),
    },
    {
      title: 'Действия',
      width: 280,
      render: (_, record) => (
        <Space>
          <Button size="small" icon={<EyeOutlined />} onClick={() => setDetailTarget(record)}>
            Детали
          </Button>
          {record.status === 'PENDING' && (
            <>
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                size="small"
                onClick={() => openApproveModal(record)}
              >
                Одобрить
              </Button>
              <Button
                danger
                icon={<CloseCircleOutlined />}
                size="small"
                onClick={() => setRejectTarget(record)}
              >
                Отклонить
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Title level={4}>Заявки на партнёрство</Title>
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
      />

      {/* Detail Modal */}
      <Modal
        open={!!detailTarget}
        title={`Заявка #${detailTarget?.id}`}
        onCancel={() => setDetailTarget(null)}
        footer={null}
        width={600}
      >
        {detailTarget && (
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="Имя">{detailTarget.firstName}</Descriptions.Item>
            <Descriptions.Item label="Фамилия">{detailTarget.lastName}</Descriptions.Item>
            <Descriptions.Item label="Телефон">{detailTarget.phone}</Descriptions.Item>
            <Descriptions.Item label="Email">{detailTarget.email || '—'}</Descriptions.Item>
            <Descriptions.Item label="Компания">{detailTarget.companyName}</Descriptions.Item>
            <Descriptions.Item label="Город">{detailTarget.city || '—'}</Descriptions.Item>
            <Descriptions.Item label="Адрес" span={2}>{detailTarget.address || '—'}</Descriptions.Item>
            <Descriptions.Item label="Часы работы">{detailTarget.workingHours || '—'}</Descriptions.Item>
            <Descriptions.Item label="Категория">{detailTarget.businessCategory || '—'}</Descriptions.Item>
            <Descriptions.Item label="Сайт">{detailTarget.website || '—'}</Descriptions.Item>
            <Descriptions.Item label="Telegram">{detailTarget.telegramUsername || '—'}</Descriptions.Item>
            <Descriptions.Item label="Комментарий" span={2}>{detailTarget.comment || '—'}</Descriptions.Item>
            <Descriptions.Item label="Источник">{detailTarget.source}</Descriptions.Item>
            <Descriptions.Item label="Статус">
              <Tag color={statusColors[detailTarget.status]}>{detailTarget.status}</Tag>
            </Descriptions.Item>
            {detailTarget.rejectionReason && (
              <Descriptions.Item label="Причина отказа" span={2}>
                {detailTarget.rejectionReason}
              </Descriptions.Item>
            )}
            {detailTarget.linkedUserId && (
              <Descriptions.Item label="User ID">{detailTarget.linkedUserId}</Descriptions.Item>
            )}
            {detailTarget.linkedMerchantId && (
              <Descriptions.Item label="Merchant ID">{detailTarget.linkedMerchantId}</Descriptions.Item>
            )}
          </Descriptions>
        )}
      </Modal>

      {/* Approve Modal */}
      <Modal
        open={!!approveTarget}
        title={`Одобрить заявку #${approveTarget?.id}`}
        onCancel={() => { setApproveTarget(null); approveForm.resetFields(); }}
        onOk={() => approveForm.submit()}
        confirmLoading={approveMutation.isPending}
        okText="Одобрить и создать партнёра"
        cancelText="Отмена"
        width={560}
      >
        <Form
          form={approveForm}
          layout="vertical"
          onFinish={(values) => approveTarget && approveMutation.mutate({ id: approveTarget.id, payload: values })}
        >
          <Form.Item name="loginEmail" label="Email для входа" rules={[{ required: true, type: 'email' }]}>
            <Input placeholder="partner@example.uz" />
          </Form.Item>
          <Form.Item name="temporaryPassword" label="Временный пароль" rules={[{ required: true, min: 8 }]}>
            <Input.Password placeholder="Минимум 8 символов" />
          </Form.Item>
          <Form.Item name="merchantName" label="Название мерчанта" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="contactPerson" label="Контактное лицо">
            <Input />
          </Form.Item>
          <Form.Item name="address" label="Адрес" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="Телефон" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="city" label="Город">
            <Input />
          </Form.Item>
          <Form.Item name="workingHours" label="Время работы">
            <Input />
          </Form.Item>
          <Form.Item name="website" label="Сайт">
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      {/* Reject Modal */}
      <Modal
        open={!!rejectTarget}
        title={`Отклонить заявку #${rejectTarget?.id}`}
        onCancel={() => { setRejectTarget(null); rejectForm.resetFields(); }}
        onOk={() => rejectForm.submit()}
        confirmLoading={rejectMutation.isPending}
        okText="Отклонить"
        okButtonProps={{ danger: true }}
        cancelText="Отмена"
      >
        <Form
          form={rejectForm}
          layout="vertical"
          onFinish={(values) => rejectTarget && rejectMutation.mutate({ id: rejectTarget.id, payload: values })}
        >
          <Form.Item name="reason" label="Причина отказа" rules={[{ required: true, max: 500 }]}>
            <Input.TextArea rows={3} placeholder="Укажите причину..." />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
