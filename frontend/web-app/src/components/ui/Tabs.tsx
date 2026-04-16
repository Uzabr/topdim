import { useState, useRef, useEffect, useCallback } from 'react';
import './Tabs.css';

export interface Tab {
  key: string;
  label: string;
  icon?: React.ReactNode;
  badge?: number;
}

interface TabsProps {
  tabs: Tab[];
  activeKey: string;
  onChange: (key: string) => void;
  sticky?: boolean;
  children?: React.ReactNode;
}

export default function Tabs({ tabs, activeKey, onChange, sticky = false }: TabsProps) {
  const tabsRef = useRef<HTMLDivElement>(null);
  const [indicatorStyle, setIndicatorStyle] = useState<React.CSSProperties>({});

  const updateIndicator = useCallback(() => {
    if (!tabsRef.current) return;
    const activeEl = tabsRef.current.querySelector('.td-tab--active') as HTMLElement;
    if (activeEl) {
      setIndicatorStyle({
        left: activeEl.offsetLeft,
        width: activeEl.offsetWidth,
      });
    }
  }, []);

  useEffect(() => {
    updateIndicator();
  }, [activeKey, updateIndicator]);

  useEffect(() => {
    window.addEventListener('resize', updateIndicator);
    return () => window.removeEventListener('resize', updateIndicator);
  }, [updateIndicator]);

  return (
    <div
      ref={tabsRef}
      className={`td-tabs ${sticky ? 'td-tabs--sticky' : ''}`}
      role="tablist"
    >
      <div className="td-tabs__track">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            role="tab"
            aria-selected={activeKey === tab.key}
            className={`td-tab ${activeKey === tab.key ? 'td-tab--active' : ''}`}
            onClick={() => onChange(tab.key)}
          >
            {tab.icon && <span className="td-tab__icon">{tab.icon}</span>}
            <span>{tab.label}</span>
            {tab.badge !== undefined && tab.badge > 0 && (
              <span className="td-tab__badge">{tab.badge}</span>
            )}
          </button>
        ))}
        <span className="td-tabs__indicator" style={indicatorStyle} />
      </div>
    </div>
  );
}
