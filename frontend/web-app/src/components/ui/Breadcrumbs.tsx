import { ChevronRight, Home } from 'lucide-react';
import { Link } from 'react-router-dom';
import './Breadcrumbs.css';

export interface BreadcrumbItem {
  label: string;
  to?: string;
}

interface BreadcrumbsProps {
  items: BreadcrumbItem[];
}

export default function Breadcrumbs({ items }: BreadcrumbsProps) {
  return (
    <nav className="breadcrumbs" aria-label="Навигация">
      <ol className="breadcrumbs__list" itemScope itemType="https://schema.org/BreadcrumbList">
        <li
          className="breadcrumbs__item"
          itemProp="itemListElement"
          itemScope
          itemType="https://schema.org/ListItem"
        >
          <Link to="/" className="breadcrumbs__link" itemProp="item">
            <Home size={14} />
            <span itemProp="name">Главная</span>
          </Link>
          <meta itemProp="position" content="1" />
          <ChevronRight size={14} className="breadcrumbs__separator" />
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
              <Link to={item.to} className="breadcrumbs__link" itemProp="item">
                <span itemProp="name">{item.label}</span>
              </Link>
            ) : (
              <span className="breadcrumbs__current" itemProp="name">
                {item.label}
              </span>
            )}
            <meta itemProp="position" content={String(index + 2)} />
            {index < items.length - 1 && (
              <ChevronRight size={14} className="breadcrumbs__separator" />
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}
