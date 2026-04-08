import { X, Store, MapPin } from 'lucide-react';
import type { Bazaar, Shop } from '../../api/bazaars';
import DirectoryBazaarCard from './DirectoryBazaarCard';
import DirectoryShopCard from './DirectoryShopCard';
import './ResultsPanel.css';

interface Props {
  bazaars: Bazaar[];
  shops: Shop[];
  activeTab: 'bazaars' | 'shops';
  onTabChange: (tab: 'bazaars' | 'shops') => void;
  onClose: () => void;
}

export default function ResultsPanel({ bazaars, shops, activeTab, onTabChange, onClose }: Props) {
  return (
    <div className="results-panel glass">
      <div className="results-panel__header">
        <div className="results-panel__summary">
          <span className="results-panel__count">
            <MapPin size={16} /> {bazaars.length} базаров
          </span>
          <span className="results-panel__count">
            <Store size={16} /> {shops.length} магазинов
          </span>
        </div>
        <button className="results-panel__close" onClick={onClose} aria-label="Закрыть">
          <X size={20} />
        </button>
      </div>

      <div className="results-panel__tabs">
        <button
          className={`results-panel__tab ${activeTab === 'bazaars' ? 'results-panel__tab--active' : ''}`}
          onClick={() => onTabChange('bazaars')}
        >
          Базары ({bazaars.length})
        </button>
        <button
          className={`results-panel__tab ${activeTab === 'shops' ? 'results-panel__tab--active' : ''}`}
          onClick={() => onTabChange('shops')}
        >
          Магазины ({shops.length})
        </button>
      </div>

      <div className="results-panel__content">
        {activeTab === 'bazaars' ? (
          bazaars.length > 0 ? (
            <div className="results-panel__grid">
              {bazaars.map((b) => (
                <DirectoryBazaarCard key={b.id} bazaar={b} compact />
              ))}
            </div>
          ) : (
            <p className="results-panel__empty">В выбранной области базары не найдены</p>
          )
        ) : (
          shops.length > 0 ? (
            <div className="results-panel__grid">
              {shops.map((s) => (
                <DirectoryShopCard key={s.id} shop={s} compact />
              ))}
            </div>
          ) : (
            <p className="results-panel__empty">В выбранной области магазины не найдены</p>
          )
        )}
      </div>
    </div>
  );
}
