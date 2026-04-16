import { useMemo } from 'react';
import { formatPrice } from '../utils/format';

export function useFormatPrice(price: number): string {
  return useMemo(() => formatPrice(price), [price]);
}
