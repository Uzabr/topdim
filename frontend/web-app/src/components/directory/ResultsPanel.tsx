import { MapPin, Store, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { Bazaar, Shop } from '../../api/bazaars';
import DirectoryBazaarCard from './DirectoryBazaarCard';
import DirectoryShopCard from './DirectoryShopCard';
import './ResultsPanel.css';

interface ResultsPanelProps {
  bazaars: Bazaar[];
  shops: Shop[];
  activeTab: 'bazaars' | 'shops';
  onTabChange: (tab: 'bazaars' | 'shops') => void;
  onClose: () => void;
}

export default function ResultsPanel({ bazaars, shops, activeTab, onTabChange, onClose }: ResultsPanelProps) {
  const { t } = useTranslation();

  return (
    <div className="results-panel surface-card">
      <div className="results-panel__header">
        <div className="results-panel__summary">
          <span className="results-panel__count">
            <MapPin size={16} /> {bazaars.length}
          </span>
          <span className="results-panel__count">
            <Store size={16} /> {shops.length}
          </span>
        </div>
        <button className="results-panel__close" onClick={onClose} aria-label={t('common.close')}>
          <X size={20} />
        </button>
      </div>

      <div className="results-panel__tabs">
        <button
          className={`results-panel__tab ${activeTab === 'bazaars' ? 'results-panel__tab--active' : ''}`}
          onClick={() => onTabChange('bazaars')}
        >
          {t('directory.bazaarsTitle', { count: bazaars.length })}
        </button>
        <button
          className={`results-panel__tab ${activeTab === 'shops' ? 'results-panel__tab--active' : ''}`}
          onClick={() => onTabChange('shops')}
        >
          {t('directory.shopsSidebarTitle', { count: shops.length })}
        </button>
      </div>

      <div className="results-panel__content">
        {activeTab === 'bazaars' && (
          bazaars.length > 0 ? (
            <div className="results-panel__grid">
              {bazaars.map((b) => (
                <DirectoryBazaarCard key={b.id} bazaar={b} />
              ))}
            </div>
          ) : (
            <p className="results-panel__empty">{t('directory.results.bazaarsEmpty')}</p>
          )
        )}
        {activeTab === 'shops' && (
          shops.length > 0 ? (
            <div className="results-panel__grid">
              {shops.map((s) => (
                <DirectoryShopCard key={s.id} shop={s} />
              ))}
            </div>
          ) : (
            <p className="results-panel__empty">{t('directory.results.shopsEmpty')}</p>
          )
        )}
      </div>
    </div>
  );
}
