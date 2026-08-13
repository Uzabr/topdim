import { Avatar, Card, Col, Descriptions, Empty, Row, Space, Tag, Typography } from 'antd';
import { EnvironmentOutlined, GlobalOutlined, MailOutlined, UserOutlined } from '@ant-design/icons';
import type { PublishedCompanyProfile } from './types';

const { Paragraph, Text, Title } = Typography;

interface CompanyProfilePreviewProps {
  profile: PublishedCompanyProfile;
}

function companyInitial(name: string): string {
  return name.trim().charAt(0).toLocaleUpperCase('ru-RU') || '?';
}

export default function CompanyProfilePreview({ profile }: CompanyProfilePreviewProps) {
  return (
    <Card>
      {profile.coverUrl ? (
        <img
          src={profile.coverUrl}
          alt={`Обложка компании ${profile.name}`}
          style={{
            width: '100%',
            maxHeight: 240,
            objectFit: 'cover',
            borderRadius: 8,
            marginBottom: 24,
          }}
        />
      ) : null}
      <Row gutter={[24, 24]}>
        <Col xs={24} md={8}>
          <Space orientation="vertical" size={12} style={{ width: '100%' }}>
            {profile.logoUrl ? (
              <Avatar
                src={profile.logoUrl}
                size={88}
                shape="square"
                aria-label={`Логотип компании ${profile.name}`}
              />
            ) : (
              <Avatar
                size={88}
                shape="square"
                aria-label={`Логотип компании ${profile.name}`}
                style={{ background: '#1677ff', fontSize: 36 }}
              >
                {companyInitial(profile.name)}
              </Avatar>
            )}
            <div>
              <Title level={3} style={{ margin: 0 }}>{profile.name}</Title>
              <Tag color={profile.active ? 'green' : 'default'}>
                {profile.active ? 'Компания активна' : 'Компания отключена'}
              </Tag>
            </div>
            <Paragraph type="secondary">
              {profile.description || 'Описание пока не заполнено'}
            </Paragraph>
          </Space>
        </Col>
        <Col xs={24} md={16}>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label={<><MailOutlined /> Email</>}>
              {profile.email || 'Не указан'}
            </Descriptions.Item>
            <Descriptions.Item label={<><GlobalOutlined /> Сайт</>}>
              {profile.website || 'Не указан'}
            </Descriptions.Item>
            <Descriptions.Item label={<><UserOutlined /> Контактное лицо</>}>
              {profile.contactPerson || 'Не указано'}
            </Descriptions.Item>
          </Descriptions>
        </Col>
      </Row>

      <Title level={4} style={{ marginTop: 24 }}>Филиалы</Title>
      {profile.locations.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Филиалы пока не добавлены" />
      ) : (
        <div role="list" aria-label="Филиалы компании">
          {profile.locations.map((location) => (
            <div
              key={location.id}
              role="listitem"
              style={{ borderTop: '1px solid #f0f0f0', padding: '16px 0' }}
            >
              <Space align="start">
                <EnvironmentOutlined style={{ marginTop: 4 }} />
                <Space orientation="vertical" size={0}>
                  <Space wrap>
                    <Text strong>{location.title || 'Филиал'}</Text>
                    {location.primary ? <Tag color="blue">Основной</Tag> : null}
                    <Tag color={location.active ? 'green' : 'default'}>
                      {location.active ? 'Активен' : 'Отключён'}
                    </Tag>
                  </Space>
                  <Text>{location.address || 'Адрес не указан'}</Text>
                  <Text type="secondary">{location.phone || 'Телефон не указан'}</Text>
                  <Text type="secondary">{location.workingHours || 'Часы работы не указаны'}</Text>
                </Space>
              </Space>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}
