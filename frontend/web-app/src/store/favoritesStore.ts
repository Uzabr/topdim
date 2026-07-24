import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { favoritesApi } from '../api/favorites';
import i18n from '../i18n';

interface FavoritesState {
  /** Массив ID купонов в избранном */
  favoriteIds: number[];
  /** Показана ли модалка лимита */
  showLimitModal: boolean;
  /** Текст модалки */
  limitMessage: string;
  syncGeneration: number;
  toggleFavorite: (couponOfferId: number) => void;
  isFavorite: (id: number) => boolean;
  closeLimitModal: () => void;
  /** Remove account-owned state when the authenticated session ends. */
  reset: () => void;
  /** Синхронизировать избранное с бэкендом (при логине) */
  syncWithBackend: () => Promise<void>;
}

const isAuthenticated = () => !!localStorage.getItem('accessToken');

export const useFavoritesStore = create<FavoritesState>()(
  persist(
    (set, get) => ({
      favoriteIds: [],
      showLimitModal: false,
      limitMessage: '',
      syncGeneration: 0,

      toggleFavorite: (couponOfferId) => {
        const ids = get().favoriteIds;
        const exists = ids.includes(couponOfferId);
        
        if (exists) {
          set({ favoriteIds: ids.filter((id) => id !== couponOfferId) });
          // Async sync with backend (fire-and-forget)
          if (isAuthenticated()) {
            favoritesApi.remove(couponOfferId).catch(() => {});
          }
        } else {
          const maxItems = isAuthenticated() ? 50 : 10;
          if (ids.length >= maxItems) {
            const msg = isAuthenticated()
              ? i18n.t('favorites.limitAuth')
              : i18n.t('favorites.limitGuest');
            set({ showLimitModal: true, limitMessage: msg });
            return;
          }
          set({ favoriteIds: [...ids, couponOfferId] });
          if (isAuthenticated()) {
            favoritesApi.add(couponOfferId).catch(() => {});
          }
        }
      },

      isFavorite: (id) => get().favoriteIds.includes(id),
      closeLimitModal: () => set({ showLimitModal: false, limitMessage: '' }),
      reset: () => set((state) => ({
        favoriteIds: [],
        showLimitModal: false,
        limitMessage: '',
        syncGeneration: state.syncGeneration + 1,
      })),

      syncWithBackend: async () => {
        if (!isAuthenticated()) return;
        const syncGeneration = get().syncGeneration;
        try {
          const response = await favoritesApi.getAll();
          if (get().syncGeneration !== syncGeneration) return;
          const backendIds = response.data.data.map((f) => f.couponOfferId);
          const localIds = get().favoriteIds;
          
          // Merge: local favorites that aren't on backend → push to backend
          const toAdd = localIds.filter((id) => !backendIds.includes(id));
          for (const id of toAdd) {
            if (get().syncGeneration !== syncGeneration) return;
            await favoritesApi.add(id).catch(() => {});
          }
          
          // Final state: union of both
          if (get().syncGeneration !== syncGeneration) return;
          const mergedIds = [...new Set([...backendIds, ...localIds])];
          set({ favoriteIds: mergedIds });
        } catch {
          // Fallback to localStorage
        }
      },
    }),
    { 
      name: 'favorites-storage',
      partialize: (state) => ({ favoriteIds: state.favoriteIds }),
    }
  )
);
