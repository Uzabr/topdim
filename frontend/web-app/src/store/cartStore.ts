import { create } from 'zustand';
import type { AddToCartRequest } from '../api/orders';

// ═══ Local Cart Item (localStorage-first, no backend required) ═══
export interface LocalCartItem {
  /** Unique key for deduplication: `${couponOfferId}-${couponOptionId}` */
  key: string;
  couponOfferId: number;
  couponOptionId: number;
  couponTitle: string;
  optionTitle: string;
  unitPrice: number;
  quantity: number;
  coverImageUrl?: string;
  isGift?: boolean;
  giftRecipientName?: string;
  giftRecipientPhone?: string;
  addedAt: number; // timestamp
}

interface CartState {
  items: LocalCartItem[];
  isOpen: boolean;
  totalItems: number;
  totalPrice: number;

  // Actions
  openCart: () => void;
  closeCart: () => void;
  toggleCart: () => void;
  addToCart: (request: AddToCartRequest & { coverImageUrl?: string }) => void;
  updateQuantity: (key: string, quantity: number) => void;
  removeFromCart: (key: string) => void;
  clearCart: () => void;
}

// ═══ LocalStorage Persistence ═══
const CART_STORAGE_KEY = 'topdim_cart';

function loadFromStorage(): LocalCartItem[] {
  try {
    const raw = localStorage.getItem(CART_STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function saveToStorage(items: LocalCartItem[]) {
  try {
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items));
  } catch {
    // quota exceeded — ignore
  }
}

// ═══ Helpers ═══
const calcTotals = (items: LocalCartItem[]) => ({
  totalItems: items.reduce((sum, item) => sum + item.quantity, 0),
  totalPrice: items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0),
});

const makeKey = (couponOfferId: number, couponOptionId: number) =>
  `${couponOfferId}-${couponOptionId}`;

// ═══ Store ═══
const initialItems = loadFromStorage();

export const useCartStore = create<CartState>((set, get) => ({
  items: initialItems,
  isOpen: false,
  ...calcTotals(initialItems),

  openCart: () => set({ isOpen: true }),
  closeCart: () => set({ isOpen: false }),
  toggleCart: () => set((s) => ({ isOpen: !s.isOpen })),

  addToCart: (request) => {
    const key = makeKey(request.couponOfferId, request.couponOptionId);
    const existing = get().items.find((item) => item.key === key);

    // Check total item count limit (30)
    const currentTotal = get().items.reduce((sum, item) => sum + item.quantity, 0);
    const addQty = request.quantity || 1;
    if (currentTotal + addQty > 30 && !existing) {
      alert('Максимум 30 позиций в корзине. Удалите лишние, чтобы добавить новые.');
      return;
    }

    let newItems: LocalCartItem[];

    if (existing) {
      if (existing.quantity + addQty > 30) {
        alert('Максимум 30 позиций в корзине.');
        return;
      }
      // Increment quantity
      newItems = get().items.map((item) =>
        item.key === key
          ? { ...item, quantity: item.quantity + addQty }
          : item
      );
    } else {
      // Add new item
      const newItem: LocalCartItem = {
        key,
        couponOfferId: request.couponOfferId,
        couponOptionId: request.couponOptionId,
        couponTitle: request.couponTitle,
        optionTitle: request.optionTitle,
        unitPrice: request.unitPrice,
        quantity: addQty,
        coverImageUrl: request.coverImageUrl,
        isGift: request.isGift,
        giftRecipientName: request.giftRecipientName,
        giftRecipientPhone: request.giftRecipientPhone,
        addedAt: Date.now(),
      };
      newItems = [...get().items, newItem];
    }

    saveToStorage(newItems);
    set({ items: newItems, isOpen: true, ...calcTotals(newItems) });
  },

  updateQuantity: (key, quantity) => {
    if (quantity <= 0) {
      get().removeFromCart(key);
      return;
    }
    const newItems = get().items.map((item) =>
      item.key === key ? { ...item, quantity } : item
    );
    saveToStorage(newItems);
    set({ items: newItems, ...calcTotals(newItems) });
  },

  removeFromCart: (key) => {
    const newItems = get().items.filter((item) => item.key !== key);
    saveToStorage(newItems);
    set({ items: newItems, ...calcTotals(newItems) });
  },

  clearCart: () => {
    saveToStorage([]);
    set({ items: [], totalItems: 0, totalPrice: 0 });
  },
}));
