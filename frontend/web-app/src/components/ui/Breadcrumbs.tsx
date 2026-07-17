import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useLocalePath } from '../../hooks/useLocalePath';
import './Breadcrumbs.css';

export interface BreadcrumbItem {
  label: string;
  to?: string;
}

interface BreadcrumbsProps {
  items: BreadcrumbItem[];
}

export default function Breadcrumbs({ items }: BreadcrumbsProps) {
  const { t } = useTranslation();
  const lp = useLocalePath();
  return (
    <nav className="breadcrumbs" aria-label={t('common.navigation')}>
      <ol className="breadcrumbs__list" itemScope itemType="https://schema.org/BreadcrumbList">
        <li
          className="breadcrumbs__item"
          itemProp="itemListElement"
          itemScope
          itemType="https://schema.org/ListItem"
        >
          <Link to={lp('/')} className="breadcrumbs__link" itemProp="item">
            <span itemProp="name">{t('common.home')}</span>
          </Link>
          <meta itemProp="position" content="1" />
          <span className="breadcrumbs__separator" aria-hidden="true">·</span>
        </li>
        {items.map((item, index) => (
          <li
            key={index}
            className="breadcrumbs__item"
            itemProp="itemListElement"
            itemScope
            itemType="https://schema.org/ListItem"
          >
            {item.to ? (
              <Link to={lp(item.to)} className="breadcrumbs__link" itemProp="item">
                <span itemProp="name">{item.label}</span>
              </Link>
            ) : (
              <span className="breadcrumbs__current" itemProp="name">
                {item.label}
              </span>
            )}
            <meta itemProp="position" content={String(index + 2)} />
            {index < items.length - 1 && (
              <span className="breadcrumbs__separator" aria-hidden="true">·</span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}
