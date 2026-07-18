/**
 * Мобильные экраны со своей навигацией (design_handoff_sizbiz → «Мобилка - 3…9»).
 *
 * Собственная шапка «назад · заголовок · действие» есть почти у всех внутренних
 * экранов, а вот нижнюю таблетку макет убирает только там, где снизу своя кнопка
 * (купить / оформить) или её просто нет (поиск): иначе таблетка перекрывает CTA.
 */
const OWN_HEADER = [
  /^\/(ru|uz)\/coupons\/\d+/,
  /^\/(ru|uz)\/cart\/?$/,
  /^\/(ru|uz)\/checkout\/?$/,
  /^\/(ru|uz)\/payment\//,
  /^\/(ru|uz)\/profile\/?$/,
  /^\/(ru|uz)\/favorites\/?$/,
  /^\/(ru|uz)\/search\/?$/,
];

const NO_BOTTOM_NAV = [
  /^\/(ru|uz)\/coupons\/\d+/,
  /^\/(ru|uz)\/cart\/?$/,
  /^\/(ru|uz)\/checkout\/?$/,
  /^\/(ru|uz)\/payment\//,
  /^\/(ru|uz)\/search\/?$/,
];

export function hasOwnHeader(pathname: string): boolean {
  return OWN_HEADER.some((route) => route.test(pathname));
}

export function hidesBottomNav(pathname: string): boolean {
  return NO_BOTTOM_NAV.some((route) => route.test(pathname));
}
