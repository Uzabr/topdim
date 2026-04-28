import { RefreshCw, MessageSquare, Star, Bell } from 'lucide-react';
import './ProfileHelpSection.css';

const COMING_SOON_ITEMS = [
  {
    icon: <RefreshCw size={24} />,
    title: 'Возвраты',
    description: 'Скоро здесь можно будет запросить возврат и видеть статус.',
  },
  {
    icon: <MessageSquare size={24} />,
    title: 'Жалобы',
    description: 'Скоро здесь можно будет сообщить о проблеме с купоном или партнёром.',
  },
  {
    icon: <Star size={24} />,
    title: 'Отзывы',
    description: 'Отзывы сейчас находятся на странице купона после использования.',
  },
  {
    icon: <Bell size={24} />,
    title: 'Уведомления',
    description: 'Скоро здесь будут важные события по заказам и купонам.',
  },
];

export default function ProfileHelpSection() {
  return (
    <div className="profile-help">
      <h3 className="profile-help__title">Помощь и поддержка</h3>
      <div className="profile-help__grid">
        {COMING_SOON_ITEMS.map((item) => (
          <div key={item.title} className="profile-help__card glass-card">
            <div className="profile-help__card-icon">{item.icon}</div>
            <div>
              <h4 className="profile-help__card-title">{item.title}</h4>
              <p className="profile-help__card-desc">{item.description}</p>
            </div>
            <span className="profile-help__badge">Скоро</span>
          </div>
        ))}
      </div>
    </div>
  );
}
