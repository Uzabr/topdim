import { create } from 'zustand';
import { ordersApi } from '../api/orders';
import type { CartItem, AddToCartRequest } from '../api/orders';

interface CartState {
  items: CartItem[];
  isOpen: boolean;
  isLoading: boolean;
  totalItems: number;
  totalPrice: number;
  openCart: () => void;
  closeCart: () => void;
  toggleCart: () => void;
  fetchCart: () => Promise<void>;
  addToCart: (request: AddToCartRequest) => Promise<void>;
  removeFromCart: (itemId: number) => void;
}

const calcTotals = (items: CartItem[]) => ({
  totalItems: items.reduce((sum, item) => sum + item.quantity, 0),
  totalPrice: items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0),
});

export const useCartStore = create<CartState>((set, get) => ({
  items: [],
  isOpen: false,
  isLoading: false,
  totalItems: 0,
  totalPrice: 0,

  openCart: () => set({ isOpen: true }),
  closeCart: () => set({ isOpen: false }),
  toggleCart: () => set((s) => ({ isOpen: !s.isOpen })),

  fetchCart: async () => {
    set({ isLoading: true });
    try {
      const response = await ordersApi.getCart();
      const items = response.data.data.items;
      set({ items, isLoading: false, ...calcTotals(items) });
    } catch {
      set({ isLoading: false });
    }
  },

  addToCart: async (request) => {
    try {
      const response = await ordersApi.addToCart(request);
      const items = response.data.data.items;
      set({ items, isOpen: true, ...calcTotals(items) });
    } catch (error) {
      throw error;
    }
  },

  removeFromCart: async (itemId) => {
    const prevItems = get().items;
    const newItems = prevItems.filter((item) => item.id !== itemId);
    set({ items: newItems, ...calcTotals(newItems) });
    try {
      await ordersApi.removeFromCart(itemId);
    } catch {
      set({ items: prevItems, ...calcTotals(prevItems) });
    }
  },
}));
