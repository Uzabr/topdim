import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod/v3';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Card,
  Button,
  Input,
  Typography,
  Table,
  Row,
  Col,
  Alert,
  Spin,
  App,
} from 'antd';
import {
  CheckCircleOutlined,
  SendOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  partnerRedemptionsApi,
  type RedeemCouponResponse,
  type RedemptionResponse,
} from './api';
import './PartnerRedeemPage.css';

const { Title, Text } = Typography;

const redeemSchema = z.object({
  couponCode: z.string().trim().min(4, 'Введите PIN-код купона'),
  staffName: z.string().trim().max(80, 'Слишком длинное имя').optional(),
});

type RedeemFormValues = z.infer<typeof redeemSchema>;

const historyColumns: ColumnsType<RedemptionResponse> = [
  {
    title: 'Купон',
    dataIndex: 'couponTitle',
    key: 'couponTitle',
    render: (text: string, record: RedemptionResponse) =>
      `${text || '—'}${record.optionTitle ? ` / ${record.optionTitle}` : ''}`,
  },
  {
    title: 'Код',
    dataIndex: 'couponCode',
    key: 'couponCode',
    render: (text: string) => <Text code>{text}</Text>,
  },
  {
    title: 'Кассир',
    dataIndex: 'redeemedByStaff',
    key: 'redeemedByStaff',
    render: (text: string) => text || '—',
  },
  {
    title: 'Дата',
    dataIndex: 'redeemedAt',
    key: 'redeemedAt',
    render: (text: string) =>
      text ? new Date(text).toLocaleString('ru-RU') : '—',
  },
];

export const PartnerRedeemPage = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [lastResult, setLastResult] = useState<RedeemCouponResponse | null>(null);
  const [lastError, setLastError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<RedeemFormValues>({
    resolver: zodResolver(redeemSchema),
    defaultValues: { couponCode: '', staffName: '' },
  });

  // Stats query
  const statsQuery = useQuery({
    queryKey: ['partner-stats'],
    queryFn: () => partnerRedemptionsApi.getStats().then((res) => res.data.data),
  });

  // Redemption history
  const redemptionsQuery = useQuery({
    queryKey: ['partner-redemptions'],
    queryFn: () =>
      partnerRedemptionsApi.getRedemptions(0, 10).then((res) => res.data.data),
  });

  // Redeem mutation
  const redeemMutation = useMutation({
    mutationFn: partnerRedemptionsApi.redeem,
    onSuccess: (res) => {
      const data = res.data.data;
      setLastResult(data);
      setLastError(null);
      message.success('Купон использован');
      setValue('couponCode', '');
      queryClient.invalidateQueries({ queryKey: ['partner-redemptions'] });
      queryClient.invalidateQueries({ queryKey: ['partner-stats'] });
    },
    onError: (error: unknown) => {
      setLastResult(null);
      const axiosError = error as { response?: { data?: { message?: string } } };
      const msg =
        axiosError.response?.data?.message || 'Ошибка при погашении купона';
      setLastError(msg);
      message.error(msg);
    },
  });

  const onSubmit = handleSubmit((values) => {
    const couponCode = values.couponCode.trim().toUpperCase();
    redeemMutation.mutate({
      couponCode,
      staffName: values.staffName?.trim() || undefined,
    });
  });

  const stats = statsQuery.data;
  const redemptions = redemptionsQuery.data;

  return (
    <div className="partner-redeem-page">
      <Title level={3}>Погашение купонов</Title>

      {/* Stats */}
      {stats && (
        <Row gutter={16} className="stats-row">
          <Col span={6}>
            <Card size="small" className="stat-card">
              <div className="stat-value">{stats.totalCoupons}</div>
              <div className="stat-label">Купонов</div>
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small" className="stat-card">
              <div className="stat-value">{stats.totalSold}</div>
              <div className="stat-label">Продано</div>
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small" className="stat-card">
              <div className="stat-value">{stats.totalRedeemed}</div>
              <div className="stat-label">Погашено</div>
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small" className="stat-card">
              <div className="stat-value">
                {stats.totalRevenue?.toLocaleString('ru-RU')} сум
              </div>
              <div className="stat-label">Выручка</div>
            </Card>
          </Col>
        </Row>
      )}
      {statsQuery.isError && (
        <Alert
          type="warning"
          message="Не удалось загрузить статистику"
          showIcon
          closable
          style={{ marginBottom: 16 }}
        />
      )}

      {/* Redeem form */}
      <Card title="Ввод PIN-кода" className="redeem-section">
        <form onSubmit={onSubmit} className="redeem-form">
          <div className="code-input">
            <Input
              {...register('couponCode')}
              placeholder="CP-XXXXXXXX"
              size="large"
              allowClear
              status={errors.couponCode ? 'error' : undefined}
              autoFocus
            />
            {errors.couponCode && (
              <Text type="danger" style={{ fontSize: 12 }}>
                {errors.couponCode.message}
              </Text>
            )}
          </div>
          <div className="staff-input">
            <Input
              {...register('staffName')}
              placeholder="Имя кассира"
              size="large"
            />
          </div>
          <Button
            type="primary"
            htmlType="submit"
            size="large"
            icon={<SendOutlined />}
            loading={redeemMutation.isPending}
          >
            Погасить
          </Button>
        </form>

        {/* Success result */}
        {lastResult && (
          <div className="success-result">
            <div className="result-title">
              <CheckCircleOutlined /> Купон успешно погашен
            </div>
            <div className="result-detail">
              <strong>Купон:</strong> {lastResult.couponTitle}
              {lastResult.optionTitle && ` / ${lastResult.optionTitle}`}
            </div>
            <div className="result-detail">
              <strong>Код:</strong> <Text code>{lastResult.couponCode}</Text>
            </div>
            <div className="result-detail">
              <strong>Статус:</strong> {lastResult.status}
            </div>
            {lastResult.usedAt && (
              <div className="result-detail">
                <strong>Использован:</strong>{' '}
                {new Date(lastResult.usedAt).toLocaleString('ru-RU')}
              </div>
            )}
          </div>
        )}

        {/* Error result */}
        {lastError && !lastResult && (
          <div className="error-result">{lastError}</div>
        )}
      </Card>

      {/* History */}
      <Card title="История погашений" className="history-section">
        {redemptionsQuery.isLoading ? (
          <Spin />
        ) : (
          <Table
            columns={historyColumns}
            dataSource={redemptions?.content || []}
            rowKey="id"
            pagination={false}
            size="small"
            locale={{ emptyText: 'Нет погашений' }}
          />
        )}
      </Card>
    </div>
  );
};
