/**
 * «Воронка»: клон карточки затягивает в кнопку шапки. Две фазы через clip-path
 * (~0.7s): сначала ближний к цели угол вытягивается хвостом в точку цели, потом
 * весь контур схлопывается туда же (design_handoff_sizbiz → flyFunnel()).
 *
 * Реализовано императивно (fixed-див прямо в body), а не через React-состояние:
 * анимацию запускают карточки в любом месте дерева, а цель живёт в шапке.
 */
type Point = [number, number];

let flying = false;

function polygon(points: Point[], bx: number, by: number): string {
  const p = points.map(([x, y]) => `${(x - bx).toFixed(1)}px ${(y - by).toFixed(1)}px`);
  return `polygon(${p.join(', ')})`;
}

/**
 * @param source  элемент-источник (карточка)
 * @param targetId id элемента-цели в шапке (#fav-btn / #cart-drop)
 * @param done    вызывается, когда клон долетел — здесь меняем состояние
 */
export function flyFunnel(source: Element | null, targetId: string, done: () => void): void {
  const target = document.getElementById(targetId);
  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

  if (flying || !source || !target || reduced) {
    done();
    return;
  }

  const r = source.getBoundingClientRect();
  const d = target.getBoundingClientRect();
  const tx = d.left + d.width / 2;
  const ty = d.top + d.height / 2;

  const pad = 12;
  const bx = Math.min(r.left, tx) - pad;
  const by = Math.min(r.top, ty) - pad;
  const bw = Math.max(r.right, tx) + pad - bx;
  const bh = Math.max(r.bottom, ty) + pad - by;

  const corners: Point[] = [
    [r.left, r.top],
    [r.right, r.top],
    [r.right, r.bottom],
    [r.left, r.bottom],
  ];

  // Ближний к цели угол станет «хвостом».
  let nearest = 0;
  let best = Infinity;
  corners.forEach(([x, y], i) => {
    const dist = (x - tx) ** 2 + (y - ty) ** 2;
    if (dist < best) {
      best = dist;
      nearest = i;
    }
  });

  const toward = ([x, y]: Point, f: number): Point => [x + (tx - x) * f, y + (ty - y) * f];
  const clip0 = polygon(corners, bx, by);
  const clip1 = polygon(
    corners.map((c, i) => (i === nearest ? [tx, ty] : toward(c, 0.18))),
    bx,
    by,
  );
  const clip2 = polygon([[tx, ty], [tx, ty], [tx, ty], [tx, ty]], bx, by);

  const clone = document.createElement('div');
  clone.className = 'funnel-flyer';
  Object.assign(clone.style, {
    left: `${bx}px`,
    top: `${by}px`,
    width: `${bw}px`,
    height: `${bh}px`,
    clipPath: clip0,
  });
  document.body.appendChild(clone);
  flying = true;

  const finish = () => {
    clone.remove();
    flying = false;
    done();
  };

  // Только сам старт анимации ждёт кадра. Уборку вешаем на таймеры: rAF не тикает
  // в фоновой вкладке, и клон остался бы на экране, заблокировав следующий полёт.
  requestAnimationFrame(() => {
    clone.style.clipPath = clip1;
  });

  setTimeout(() => {
    clone.style.clipPath = clip2;
    clone.style.opacity = '0.15';
  }, 330);

  setTimeout(finish, 710);
}
