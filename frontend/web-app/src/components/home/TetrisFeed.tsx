import type { CouponOffer } from '../../api/coupons';
import CouponCard from '../coupon/CouponCard';
import { mapCouponOfferToCardData } from '../../utils/couponCardMapper';
import HotTile from './HotTile';
import TetrisCard from './TetrisCard';
import type { TetrisShape } from './TetrisCard';
import './TetrisCard.css';

interface TetrisFeedProps {
  coupons: CouponOffer[];
  /** Чем лид попал в ленту: сортировкой по скидке или по популярности. */
  lead: 'discount' | 'popular';
}

/**
 * Первый блок: горящий тайл занимает колонку, к нему встают 4 фигуры.
 * Дальше — блоки из 5 фигур: место тайла берёт высокая карточка.
 */
const LEAD_BLOCK: TetrisShape[] = ['l-down', 'l-left', 'l-right', 'square'];
const BLOCK: TetrisShape[] = ['tall', 'l-down', 'l-left', 'l-right', 'square'];

/**
 * Лента-мозаика: формы в пределах блока не повторяются, уголок одной карточки
 * встаёт в вырез соседней (design_handoff_sizbiz → §10.5a).
 *
 * Хвост, которого не хватает на целый блок, отдаём обычным карточкам: незакрытые
 * вырезы мозаики выглядят как дырки в сетке.
 */
export default function TetrisFeed({ coupons, lead }: TetrisFeedProps) {
  const [hot, ...rest] = coupons;

  const blocks: CouponOffer[][] = [];
  let i = 0;
  let size = LEAD_BLOCK.length;

  while (rest.length - i >= size) {
    blocks.push(rest.slice(i, i + size));
    i += size;
    size = BLOCK.length;
  }

  const tail = rest.slice(i);

  // Купонов не хватило даже на один блок (узкая категория) — обычная сетка:
  // мозаика из одного тайла оставила бы полстраницы пустой.
  if (blocks.length === 0) {
    return (
      <div className="feed__grid">
        {hot && <HotTile coupon={hot} lead={lead} />}
        {tail.map((coupon) => (
          <CouponCard key={coupon.id} coupon={mapCouponOfferToCardData(coupon)} />
        ))}
      </div>
    );
  }

  return (
    <>
      {blocks.map((block, b) => {
        const shapes = b === 0 ? LEAD_BLOCK : BLOCK;
        return (
          <div key={block[0].id} className={`tblock${b === 0 ? ' tblock--lead' : ''}`}>
            {b === 0 && hot && <HotTile coupon={hot} lead={lead} />}

            {block.map((coupon, k) => (
              <TetrisCard key={coupon.id} coupon={coupon} shape={shapes[k]} />
            ))}
          </div>
        );
      })}

      {tail.length > 0 && (
        <div className="feed__grid feed__tail">
          {tail.map((coupon) => (
            <CouponCard key={coupon.id} coupon={mapCouponOfferToCardData(coupon)} />
          ))}
        </div>
      )}
    </>
  );
}
