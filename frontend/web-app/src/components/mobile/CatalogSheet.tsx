import { useEffect } from 'react';
import {
  Dumbbell,
  Gift,
  PartyPopper,
  Sparkles,
  Tag,
  UtensilsCrossed,
  Wrench,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useQueries, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { couponsApi } from '../../api/coupons';
import type { Category } from '../../api/coupons';
import { useLocalePath } from '../../hooks/useLocalePath';
import { localizedName } from '../../utils/localizedText';
import { useScrollLock } from '../../hooks/useScrollLock';
import './CatalogSheet.css';

interface CatalogSheetProps {
  onClose: () => void;
}

/** Плитки категорий в шторке — цвет квадрата под иконкой по порядку. */
const TINTS = ['#ffe3a3', '#f7cdef', '#c9ebdb', '#cfe3f7', '#e3d7f7', '#f7e0cd'];

/** Иконка по slug категории; для неизвестных — нейтральная. */
const ICONS: Record<string, LucideIcon> = {
  food: UtensilsCrossed,
  beauty: Sparkles,
  entertainment: PartyPopper,
  'health-sport': Dumbbell,
  services: Wrench,
  gifts: Gift,
};

/**
 * Шторка каталога снизу: сетка 2 колонки, у каждой категории — счётчик купонов.
 * Референс: design_handoff_sizbiz/«Мобилка - 2 Главная» → «шторка каталога».
 */
export default function CatalogSheet({ onClose }: CatalogSheetProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const lp = useLocalePath();
  useScrollLock();

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: () => couponsApi.getCategories(),
    select: (res) => res.data.data,
  });

  // Счётчики: size=1, нужен только totalElements
  const counts = useQueries({
    queries: categories.map((c: Category) => ({
      queryKey: ['coupons', 'count', c.id],
      queryFn: () => couponsApi.getCatalog({ categoryId: c.id, size: 1 }),
      select: (res: Awaited<ReturnType<typeof couponsApi.getCatalog>>) =>
        res.data.data.totalElements,
    })),
  });

  return (
    <div className="sheet" onClick={onClose} role="dialog" aria-modal="true">
      <div className="sheet__body" onClick={(e) => e.stopPropagation()}>
        <span className="sheet__grip" />
        <h2 className="sheet__title">{t('header.catalog')}</h2>

        <div className="catalog__grid">
          {categories.map((category, i) => {
            const Icon = ICONS[category.slug] ?? Tag;
            return (
            <button
              key={category.id}
              type="button"
              className="catalog__item"
              onClick={() => {
                onClose();
                navigate(`${lp('/coupons')}?categoryId=${category.id}`);
              }}
            >
              <span className="catalog__icon" style={{ background: TINTS[i % TINTS.length] }}>
                <Icon size={20} strokeWidth={1.9} />
              </span>
              <span className="catalog__text">
                <span className="catalog__name">{localizedName(category, i18n.language)}</span>
                {counts[i]?.data !== undefined && (
                  <span className="catalog__count">
                    {t('home.tiles.count', { count: counts[i].data })}
                  </span>
                )}
              </span>
            </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
