import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App as AntApp, Button, Result, Space, Spin, Tabs, Tag, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { companyApi } from './api';
import CompanyProfilePreview from './CompanyProfilePreview';
import CompanyRequestsTable from './CompanyRequestsTable';
import type { CompanyProfileChangeStatus } from './types';

const { Text, Title } = Typography;

const ACTIVE_STATUSES: CompanyProfileChangeStatus[] = [
  'DRAFT',
  'PENDING_REVIEW',
  'IN_REVIEW',
  'REVISION_REQUESTED',
];
const MAX_ACTIVE_REQUESTS = 10;

export default function CompanyProfilePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message } = AntApp.useApp();
  const profileQuery = useQuery({
    queryKey: ['company-profile'],
    queryFn: companyApi.getPublishedProfile,
  });
  const activeCountQueries = useQueries({
    queries: ACTIVE_STATUSES.map((status) => ({
      queryKey: ['company-change-requests', { status, page: 0, size: 1 }],
      queryFn: () => companyApi.listRequests({ status, page: 0, size: 1 }),
    })),
  });

  const createMutation = useMutation({
    mutationFn: companyApi.createDraft,
    onSuccess: async (draft) => {
      await queryClient.invalidateQueries({ queryKey: ['company-change-requests'] });
      await queryClient.invalidateQueries({ queryKey: ['company-profile'] });
      navigate(`/company/requests/${draft.id}`);
    },
    onError: () => message.error('Не удалось создать заявку'),
  });

  if (profileQuery.isLoading) {
    return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  }
  if (profileQuery.error || !profileQuery.data) {
    return <Result status="error" title="Не удалось загрузить профиль компании" />;
  }

  const activeCountLoading = activeCountQueries.some((query) => query.isLoading);
  const activeCountError = activeCountQueries.some((query) => query.isError);
  const activeCount = activeCountQueries.reduce(
    (total, query) => total + (query.data?.totalElements ?? 0),
    0,
  );
  const activeLimitReached = !activeCountLoading && activeCount >= MAX_ACTIVE_REQUESTS;
  const profile = profileQuery.data;

  return (
    <section aria-label="Раздел компании">
      <Space
        align="start"
        style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}
        wrap
      >
        <div>
          <Title level={2} style={{ margin: 0 }}>Моя компания</Title>
          <Space wrap>
            <Tag>Версия {profile.profileVersion}</Tag>
            <Tag color={profile.publicationReady ? 'green' : 'orange'}>
              {profile.publicationReady ? 'Готов к публикации' : 'Нужны данные'}
            </Tag>
          </Space>
        </div>
        <Space orientation="vertical" align="end" size={4}>
          <Text>{activeCountLoading ? 'Считаем активные заявки…' : `${activeCount} из 10 активных`}</Text>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={activeCountLoading || activeCountError || activeLimitReached}
            loading={createMutation.isPending}
            onClick={() => createMutation.mutate()}
          >
            Создать заявку на изменение
          </Button>
          {activeLimitReached ? (
            <Text type="danger">Достигнут лимит активных заявок</Text>
          ) : null}
          {activeCountError ? (
            <Text type="danger">Не удалось проверить лимит активных заявок</Text>
          ) : null}
        </Space>
      </Space>

      <Tabs
        defaultActiveKey="profile"
        items={[
          {
            key: 'profile',
            label: 'Опубликованный профиль',
            children: (
              <>
                {!profile.publicationReady && profile.publicationBlockReason ? (
                  <Alert
                    type="warning"
                    showIcon
                    title="Что нужно заполнить"
                    description={(
                      <ul style={{ margin: 0, paddingLeft: 20 }}>
                        <li>{profile.publicationBlockReason}</li>
                      </ul>
                    )}
                    style={{ marginBottom: 16 }}
                  />
                ) : null}
                <CompanyProfilePreview profile={profile} />
              </>
            ),
          },
          {
            key: 'requests',
            label: 'Мои заявки',
            children: <CompanyRequestsTable />,
          },
        ]}
      />
    </section>
  );
}
