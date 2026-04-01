import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';

export const ForbiddenPage = () => {
  const navigate = useNavigate();

  return (
    <Result
      status="403"
      title="403"
      subTitle="У вас нет прав для доступа к этой странице."
      extra={
        <Button type="primary" onClick={() => navigate('/dashboard')}>
          На главную
        </Button>
      }
    />
  );
};
