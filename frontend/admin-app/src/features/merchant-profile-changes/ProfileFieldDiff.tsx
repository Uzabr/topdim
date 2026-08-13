import { Card, Col, Row, Tag, Typography } from 'antd';
import type { ReactNode } from 'react';

const { Text } = Typography;

interface ProfileFieldDiffProps {
  label: string;
  before: ReactNode;
  after: ReactNode;
}

function normalize(value: ReactNode) {
  return value === null || value === undefined || value === '' ? '—' : value;
}

export function ProfileFieldDiff({ label, before, after }: ProfileFieldDiffProps) {
  const normalizedBefore = normalize(before);
  const normalizedAfter = normalize(after);
  const changed = String(normalizedBefore) !== String(normalizedAfter);

  return (
    <Card
      size="small"
      title={label}
      extra={changed ? <Tag color="gold">Изменено</Tag> : null}
      style={{ borderColor: changed ? '#faad14' : undefined }}
    >
      <Row gutter={[16, 8]}>
        <Col xs={24} md={12}>
          <Text type="secondary">Сейчас</Text>
          <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{normalizedBefore}</div>
        </Col>
        <Col xs={24} md={12}>
          <Text type="secondary">Предлагается</Text>
          <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{normalizedAfter}</div>
        </Col>
      </Row>
    </Card>
  );
}
