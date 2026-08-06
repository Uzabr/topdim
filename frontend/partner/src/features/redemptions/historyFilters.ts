import dayjs from 'dayjs';

export interface RedemptionHistoryFilters {
  code: string;
  from?: string;
  to?: string;
  page: number;
}

function validDate(value: string | null): value is string {
  return Boolean(
    value
    && /^\d{4}-\d{2}-\d{2}$/.test(value)
    && dayjs(value).isValid()
    && dayjs(value).format('YYYY-MM-DD') === value,
  );
}

export function parseHistorySearchParams(
  params: URLSearchParams,
): RedemptionHistoryFilters {
  const code = (params.get('code') || '').trim().slice(0, 50);
  let from = validDate(params.get('from')) ? params.get('from')! : undefined;
  let to = validDate(params.get('to')) ? params.get('to')! : undefined;

  if (from && to && from > to) {
    from = undefined;
    to = undefined;
  }

  const rawPage = params.get('page');
  const parsedPage = rawPage && /^\d+$/.test(rawPage) ? Number(rawPage) : 1;
  const page = Number.isSafeInteger(parsedPage) && parsedPage > 0 ? parsedPage : 1;

  return {
    code,
    ...(from ? { from } : {}),
    ...(to ? { to } : {}),
    page,
  };
}

export function serializeHistoryFilters(
  filters: RedemptionHistoryFilters,
): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.code) params.set('code', filters.code);
  if (filters.from) params.set('from', filters.from);
  if (filters.to) params.set('to', filters.to);
  if (filters.page > 1) params.set('page', String(filters.page));
  return params;
}

export function historyApiParams(
  filters: RedemptionHistoryFilters,
): Record<string, string | number> {
  return {
    page: filters.page - 1,
    size: 20,
    ...(filters.code ? { couponCode: filters.code } : {}),
    ...(filters.from ? { dateFrom: filters.from } : {}),
    ...(filters.to ? { dateTo: filters.to } : {}),
  };
}
