import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { favoritesApi } from '../api/favorites';

interface FavoritesState {
  /** Массив ID купонов в избранном */
  favoriteIds: number[];
  toggleFavorite: (couponOfferId: number) => void;
  isFavorite: (id: number) => boolean;
  /** Синхронизировать избранное с бэкендом (при логине) */
  syncWithBackend: () => Promise<void>;
}

const isAuthenticated = () => !!localStorage.getItem('accessToken');

export const useFavoritesStore = create<FavoritesState>()(
  persist(
    (set, get) => ({
      favoriteIds: [],

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
          set({ favoriteIds: [...ids, couponOfferId] });
          if (isAuthenticated()) {
            favoritesApi.add(couponOfferId).catch(() => {});
          }
        }
      },

      isFavorite: (id) => get().favoriteIds.includes(id),

      syncWithBackend: async () => {
        if (!isAuthenticated()) return;
        try {
          const response = await favoritesApi.getAll();
          const backendIds = response.data.data.map((f) => f.couponOfferId);
          const localIds = get().favoriteIds;
          
          // Merge: local favorites that aren't on backend → push to backend
          const toAdd = localIds.filter((id) => !backendIds.includes(id));
          for (const id of toAdd) {
            await favoritesApi.add(id).catch(() => {});
          }
          
          // Final state: union of both
          const mergedIds = [...new Set([...backendIds, ...localIds])];
          set({ favoriteIds: mergedIds });
        } catch {
          // Fallback to localStorage
        }
      },
    }),
    { name: 'favorites-storage' }
  )
);
