import { useEffect, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Result,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { useSearchParams } from 'react-router-dom';
import api from '../api';
import {
  historyApiParams,
  parseHistorySearchParams,
  serializeHistoryFilters,
} from '../features/redemptions/historyFilters';

const { RangePicker } = DatePicker;
const { Title, Text } = Typography;

interface RedemptionItem {
  id: number;
  couponTitle?: string;
  optionTitle?: string;
  couponCode?: string;
  redeemedByStaff?: string;
  redeemMethod?: string;
  redeemedAt: string;
}

interface RedemptionPage {
  content: RedemptionItem[];
  number: number;
  size: number;
  totalElements: number;
}

interface HistoryFormValues {
  code?: string;
  period?: [Dayjs | null, Dayjs | null];
}

const columns: ColumnsType<RedemptionItem> = [
  {
    title: 'Купон',
    key: 'coupon',
    render: (_, record) => (
      <Space direction="vertical" size={0}>
        <Text strong>{record.couponTitle || '—'}</Text>
        {record.optionTitle ? <Text type="secondary">{record.optionTitle}</Text> : null}
      </Space>
    ),
  },
  {
    title: 'Код',
    dataIndex: 'couponCode',
    key: 'couponCode',
    render: (code?: string) => code ? <Tag>{code}</Tag> : '—',
  },
  {
    title: 'Сотрудник',
    dataIndex: 'redeemedByStaff',
    key: 'redeemedByStaff',
    render: (staff?: string) => staff || '—',
  },
  {
    title: 'Способ',
    dataIndex: 'redeemMethod',
    key: 'redeemMethod',
    render: (method?: string) => method
      ? <Tag color={method === 'QR' ? 'blue' : 'green'}>{method}</Tag>
      : '—',
  },
  {
    title: 'Дата погашения',
    dataIndex: 'redeemedAt',
    key: 'redeemedAt',
    render: (value: string) => dayjs(value).format('DD.MM.YYYY HH:mm'),
  },
];

export default function RedemptionHistoryPage() {
  const [form] = Form.useForm<HistoryFormValues>();
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = useMemo(
    () => parseHistorySearchParams(searchParams),
    [searchParams],
  );

  useEffect(() => {
    const normalized = serializeHistoryFilters(filters);
    if (normalized.toString() !== searchParams.toString()) {
      setSearchParams(normalized, { replace: true });
    }
  }, [filters, searchParams, setSearchParams]);

  useEffect(() => {
    form.setFieldsValue({
      code: filters.code,
      period: filters.from || filters.to
        ? [filters.from ? dayjs(filters.from) : null, filters.to ? dayjs(filters.to) : null]
        : undefined,
    });
  }, [filters.code, filters.from, filters.to, form]);

  const query = useQuery({
    queryKey: ['partner-redemptions', filters],
    queryFn: async (): Promise<RedemptionPage> => {
      const response = await api.get('/api/v1/partner/redemptions', {
        params: historyApiParams(filters),
      });
      return response.data.data;
    },
  });

  const applyFilters = (values: HistoryFormValues) => {
    setSearchParams(serializeHistoryFilters({
      code: (values.code || '').trim().slice(0, 50),
      ...(values.period?.[0] ? { from: values.period[0].format('YYYY-MM-DD') } : {}),
      ...(values.period?.[1] ? { to: values.period[1].format('YYYY-MM-DD') } : {}),
      page: 1,
    }));
  };

  const resetFilters = () => {
    form.resetFields();
    setSearchParams(new URLSearchParams());
  };

  const changePage = (page: number) => {
    setSearchParams(serializeHistoryFilters({ ...filters, page }));
  };

  const hasFilters = Boolean(filters.code || filters.from || filters.to);

  return (
    <div>
      <Title level={3}>История погашений</Title>

      <Card style={{ marginBottom: 16, borderRadius: 12 }}>
        <Form<HistoryFormValues>
          form={form}
          layout="inline"
          onFinish={applyFilters}
        >
          <Form.Item name="code" label="Код купона">
            <Input
              allowClear
              maxLength={50}
              placeholder="CP-XXXX1234"
              style={{ width: 220 }}
            />
          </Form.Item>
          <Form.Item name="period" label="Период">
            <RangePicker
              allowClear
              allowEmpty={[true, true]}
              format="DD.MM.YYYY"
              placeholder={['Дата от', 'Дата до']}
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">Найти</Button>
              <Button onClick={resetFilters}>Сбросить</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      {query.isLoading ? (
        <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />
      ) : null}

      {query.isError ? (
        <Result
          status="error"
          title="Не удалось загрузить историю погашений"
          subTitle="Проверьте соединение и повторите попытку"
          extra={<Button type="primary" onClick={() => void query.refetch()}>Повторить</Button>}
        />
      ) : null}

      {!query.isLoading && !query.isError && query.data?.content.length === 0 ? (
        <Result
          status="info"
          title={hasFilters
            ? 'По заданным фильтрам ничего не найдено'
            : 'Погашений пока нет'}
        />
      ) : null}

      {!query.isLoading && !query.isError && query.data && query.data.content.length > 0 ? (
        <Card style={{ borderRadius: 12 }}>
          <Table<RedemptionItem>
            columns={columns}
            dataSource={query.data.content}
            rowKey="id"
            pagination={{
              current: filters.page,
              pageSize: 20,
              total: query.data.totalElements,
              showSizeChanger: false,
              onChange: changePage,
            }}
            scroll={{ x: 760 }}
          />
        </Card>
      ) : null}
    </div>
  );
}
