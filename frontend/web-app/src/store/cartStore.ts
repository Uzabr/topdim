import { create } from 'zustand';
import { ordersApi } from '../api/orders';
import type { AddToCartRequest, CartItem } from '../api/orders';
import i18n from '../i18n';
import {
  captureSessionGeneration,
  isSessionGenerationCurrent,
} from '../sessionCleanup';

// ═══ Constants ═══
/** Максимальное количество позиций в guest-корзине (localStorage). */
export const GUEST_CART_LIMIT = 5;

// ═══ Local Cart Item (localStorage-first, для guest пользователей) ═══
export interface LocalCartItem {
  /** Unique key for deduplication: `${couponOfferId}-${couponOptionId}` */
  key: string;
  couponOfferId: number;
  couponOptionId: number;
  couponTitle: string;
  optionTitle: string;
  unitPrice: number;
  /** Обычная цена опции — для показа скидки в корзине. Может отсутствовать
      у старых записей и у backend-корзины (там её не отдают). */
  oldPrice?: number;
  quantity: number;
  coverImageUrl?: string;
  isGift?: boolean;
  giftRecipientName?: string;
  giftRecipientPhone?: string;
  addedAt: number; // timestamp
}

type CartMode = 'guest' | 'auth';

interface CartState {
  mode: CartMode;
  // Guest cart (localStorage)
  localItems: LocalCartItem[];
  // Auth cart (backend)
  backendItems: CartItem[];
  backendCartId: number | null;
  /** Backward-compatible: returns localItems (guest) or backendItems mapped to LocalCartItem shape (auth). */
  items: LocalCartItem[];

  isOpen: boolean;
  isLoading: boolean;
  error: string | null;
  totalItems: number;
  totalPrice: number;

  // Actions
  openCart: () => void;
  closeCart: () => void;
  toggleCart: () => void;
  setMode: (mode: CartMode) => void;
  addToCart: (request: AddToCartRequest & { coverImageUrl?: string; oldPrice?: number }) => void;
  updateQuantity: (key: string, quantity: number) => void;
  removeFromCart: (key: string) => void;
  clearCart: () => void;
  clearError: () => void;

  // Auth-mode actions
  fetchBackendCart: () => Promise<void>;
  addToBackendCart: (request: AddToCartRequest) => Promise<void>;
  removeFromBackendCart: (itemId: number) => Promise<void>;
  syncLocalCartToBackend: () => Promise<void>;
}

// ═══ LocalStorage Persistence (guest only) ═══
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

function clearStorage() {
  localStorage.removeItem(CART_STORAGE_KEY);
}

// ═══ Helpers ═══
const calcLocalTotals = (items: LocalCartItem[]) => ({
  totalItems: items.reduce((sum, item) => sum + item.quantity, 0),
  totalPrice: items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0),
});

const calcBackendTotals = (items: CartItem[]) => ({
  totalItems: items.reduce((sum, item) => sum + item.quantity, 0),
  totalPrice: items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0),
});

/** Convert backend CartItem[] to LocalCartItem[] for backward-compatible `items` property. */
const backendToLocal = (items: CartItem[]): LocalCartItem[] =>
  items.map((item) => ({
    key: `${item.couponOfferId}-${item.couponOptionId}`,
    couponOfferId: item.couponOfferId,
    couponOptionId: item.couponOptionId,
    couponTitle: item.couponTitle,
    optionTitle: item.optionTitle,
    unitPrice: item.unitPrice,
    quantity: item.quantity,
    isGift: item.gift,
    giftRecipientName: item.giftRecipientName,
    giftRecipientPhone: item.giftRecipientPhone,
    addedAt: 0,
  }));

const makeKey = (couponOfferId: number, couponOptionId: number) =>
  `${couponOfferId}-${couponOptionId}`;

// ═══ Store ═══
const initialItems = loadFromStorage();

export const useCartStore = create<CartState>((set, get) => ({
  mode: 'guest' as CartMode,
  localItems: initialItems,
  backendItems: [],
  backendCartId: null,
  items: initialItems,
  isOpen: false,
  isLoading: false,
  error: null,
  ...calcLocalTotals(initialItems),

  openCart: () => set({ isOpen: true }),
  closeCart: () => set({ isOpen: false }),
  toggleCart: () => set((s) => ({ isOpen: !s.isOpen })),
  clearError: () => set({ error: null }),

  setMode: (mode: CartMode) => {
    if (mode === 'auth') {
      const state = get();
      const mapped = backendToLocal(state.backendItems);
      set({
        mode: 'auth',
        items: mapped,
        ...calcBackendTotals(state.backendItems),
      });
    } else {
      const localItems = loadFromStorage();
      set({
        mode: 'guest',
        backendItems: [],
        backendCartId: null,
        localItems,
        items: localItems,
        isLoading: false,
        error: null,
        ...calcLocalTotals(localItems),
      });
    }
  },

  // ═══ Guest-mode actions (localStorage) ═══

  addToCart: (request) => {
    const { mode } = get();

    // Auth mode → delegate to backend
    if (mode === 'auth') {
      get().addToBackendCart(request);
      return;
    }

    // Guest mode → localStorage with GUEST_CART_LIMIT
    const key = makeKey(request.couponOfferId, request.couponOptionId);
    const existing = get().localItems.find((item) => item.key === key);

    const currentTotal = get().localItems.reduce((sum, item) => sum + item.quantity, 0);
    const addQty = request.quantity || 1;

    if (currentTotal + addQty > GUEST_CART_LIMIT && !existing) {
      alert(i18n.t('cart.guestLimit', { limit: GUEST_CART_LIMIT }));
      return;
    }

    let newItems: LocalCartItem[];

    if (existing) {
      if (existing.quantity + addQty > GUEST_CART_LIMIT) {
        alert(i18n.t('cart.guestLimitShort', { limit: GUEST_CART_LIMIT }));
        return;
      }
      newItems = get().localItems.map((item) =>
        item.key === key
          ? { ...item, quantity: item.quantity + addQty }
          : item
      );
    } else {
      const newItem: LocalCartItem = {
        key,
        couponOfferId: request.couponOfferId,
        couponOptionId: request.couponOptionId,
        couponTitle: request.couponTitle,
        optionTitle: request.optionTitle,
        unitPrice: request.unitPrice,
        oldPrice: request.oldPrice,
        quantity: addQty,
        coverImageUrl: request.coverImageUrl,
        isGift: request.isGift,
        giftRecipientName: request.giftRecipientName,
        giftRecipientPhone: request.giftRecipientPhone,
        addedAt: Date.now(),
      };
      newItems = [...get().localItems, newItem];
    }

    saveToStorage(newItems);
    set({ localItems: newItems, items: newItems, isOpen: true, ...calcLocalTotals(newItems) });
  },

  updateQuantity: (key, quantity) => {
    if (quantity <= 0) {
      get().removeFromCart(key);
      return;
    }

    if (get().mode === 'auth') {
      // Auth mode: call backend PATCH and refresh cart
      const item = get().backendItems.find(
        (i) => makeKey(i.couponOfferId, i.couponOptionId) === key
      );
      if (item) {
        const sessionGeneration = captureSessionGeneration();
        set({ isLoading: true, error: null });
        ordersApi.updateCartItemQuantity(item.id, quantity)
          .then(async () => {
            if (!isSessionGenerationCurrent(sessionGeneration)) return;
            await get().fetchBackendCart();
            if (!isSessionGenerationCurrent(sessionGeneration)) return;
            set({ isLoading: false });
          })
          .catch((err: unknown) => {
            if (!isSessionGenerationCurrent(sessionGeneration)) return;
            const error = err as { response?: { data?: { message?: string } } };
            const message = error.response?.data?.message || i18n.t('cart.updateQtyError');
            set({ isLoading: false, error: message });
          });
      }
      return;
    }

    // Guest mode
    const newItems = get().localItems.map((item) =>
      item.key === key ? { ...item, quantity } : item
    );
    saveToStorage(newItems);
    set({ localItems: newItems, items: newItems, ...calcLocalTotals(newItems) });
  },

  removeFromCart: (key) => {
    if (get().mode === 'auth') {
      // Auth mode: find backendItem and remove via API
      const item = get().backendItems.find(
        (i) => makeKey(i.couponOfferId, i.couponOptionId) === key
      );
      if (item) {
        get().removeFromBackendCart(item.id);
      }
      return;
    }

    // Guest mode
    const newItems = get().localItems.filter((item) => item.key !== key);
    saveToStorage(newItems);
    set({ localItems: newItems, items: newItems, ...calcLocalTotals(newItems) });
  },

  clearCart: () => {
    if (get().mode === 'auth') {
      set({ backendItems: [], items: [], totalItems: 0, totalPrice: 0 });
    } else {
      clearStorage();
      set({ localItems: [], items: [], totalItems: 0, totalPrice: 0 });
    }
  },

  // ═══ Auth-mode actions (backend API) ═══

  fetchBackendCart: async () => {
    const sessionGeneration = captureSessionGeneration();
    set({ isLoading: true });
    try {
      const response = await ordersApi.getCart();
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const cart = response.data.data;
      set({
        backendItems: cart.items,
        backendCartId: cart.id,
        items: backendToLocal(cart.items),
        isLoading: false,
        ...calcBackendTotals(cart.items),
      });
    } catch (error) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      console.error('Failed to fetch backend cart:', error);
      set({ isLoading: false });
    }
  },

  addToBackendCart: async (request: AddToCartRequest) => {
    const sessionGeneration = captureSessionGeneration();
    set({ isLoading: true, error: null });
    try {
      const response = await ordersApi.addToCart(request);
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const cart = response.data.data;
      const backendItems = cart.items || [];
      set({
        backendItems,
        backendCartId: cart.id,
        items: backendToLocal(backendItems),
        isOpen: true,
        isLoading: false,
        error: null,
        ...calcBackendTotals(backendItems),
      });
    } catch (err: unknown) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const error = err as { response?: { data?: { message?: string } } };
      const message = error.response?.data?.message || i18n.t('cart.addError');
      set({ isLoading: false, error: message });
      throw err;
    }
  },

  removeFromBackendCart: async (itemId: number) => {
    const sessionGeneration = captureSessionGeneration();
    set({ isLoading: true });
    try {
      await ordersApi.removeFromCart(itemId);
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      // Re-fetch cart to get updated state
      await get().fetchBackendCart();
    } catch (error) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      console.error('Failed to remove from backend cart:', error);
      set({ isLoading: false });
    }
  },

  /**
   * Синхронизация localStorage корзины → backend при авторизации.
   * Переносит все localItems в backend cart, затем очищает localStorage.
   * Это гарантирует, что после логина backend — единственный source of truth.
   */
  syncLocalCartToBackend: async () => {
    const sessionGeneration = captureSessionGeneration();
    const { localItems } = get();
    if (localItems.length === 0) {
      // Нечего синхронизировать, просто загружаем backend cart
      await get().fetchBackendCart();
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      set({ mode: 'auth' });
      return;
    }

    set({ isLoading: true });
    try {
      // Переносим каждый local item в backend
      for (const item of localItems) {
        await ordersApi.addToCart({
          couponOfferId: item.couponOfferId,
          couponOptionId: item.couponOptionId,
          couponTitle: item.couponTitle,
          optionTitle: item.optionTitle,
          unitPrice: item.unitPrice,
          quantity: item.quantity,
          isGift: item.isGift,
          giftRecipientName: item.giftRecipientName,
          giftRecipientPhone: item.giftRecipientPhone,
        });
        if (!isSessionGenerationCurrent(sessionGeneration)) return;
      }

      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      // Очищаем localStorage
      clearStorage();
      set({ localItems: [] });

      // Загружаем актуальную backend cart
      await get().fetchBackendCart();
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      set({ mode: 'auth', isLoading: false });
    } catch (error) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      console.error('Failed to sync local cart to backend:', error);
      set({ isLoading: false });
    }
  },
}));
