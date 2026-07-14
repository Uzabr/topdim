import { useMemo } from 'react';
import { RefreshCw, MessageSquare, Star, Bell } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import './ProfileHelpSection.css';

export default function ProfileHelpSection() {
  const { t } = useTranslation();

  const items = useMemo(() => [
    {
      icon: <RefreshCw size={24} />,
      title: t('profile.help.refundsTitle'),
      description: t('profile.help.refundsDesc'),
    },
    {
      icon: <MessageSquare size={24} />,
      title: t('profile.help.complaintsTitle'),
      description: t('profile.help.complaintsDesc'),
    },
    {
      icon: <Star size={24} />,
      title: t('profile.help.reviewsTitle'),
      description: t('profile.help.reviewsDesc'),
    },
    {
      icon: <Bell size={24} />,
      title: t('profile.help.notificationsTitle'),
      description: t('profile.help.notificationsDesc'),
    },
  ], [t]);

  return (
    <div className="profile-help">
      <h3 className="profile-help__title">{t('profile.help.title')}</h3>
      <div className="profile-help__grid">
        {items.map((item) => (
          <div key={item.title} className="profile-help__card surface-card">
            <div className="profile-help__card-icon">{item.icon}</div>
            <div>
              <h4 className="profile-help__card-title">{item.title}</h4>
              <p className="profile-help__card-desc">{item.description}</p>
            </div>
            <span className="profile-help__badge">{t('common.soon')}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
