import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { TopdimDeal } from '../data/topdim';

interface FavoritesState {
  favorites: TopdimDeal[];
  toggleFavorite: (deal: TopdimDeal) => void;
  isFavorite: (id: number) => boolean;
}

export const useFavoritesStore = create<FavoritesState>()(
  persist(
    (set, get) => ({
      favorites: [],
      toggleFavorite: (deal) => {
        const favs = get().favorites;
        if (favs.some((d) => d.id === deal.id)) {
          set({ favorites: favs.filter((d) => d.id !== deal.id) });
        } else {
          set({ favorites: [...favs, deal] });
        }
      },
      isFavorite: (id) => get().favorites.some((d) => d.id === id),
    }),
    { name: 'favorites-storage' }
  )
);
