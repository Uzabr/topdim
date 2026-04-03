import { create } from 'zustand';

type ViewMode = 'grid' | 'list';

interface MarketplaceState {
  search: string;
  category: string;
  priceRange: string;
  distance: string;
  viewMode: ViewMode;
  nearMeOnly: boolean;
  selectedBazaarId: number | null;
  setSearch: (value: string) => void;
  setCategory: (value: string) => void;
  setPriceRange: (value: string) => void;
  setDistance: (value: string) => void;
  setViewMode: (value: ViewMode) => void;
  toggleNearMe: () => void;
  selectBazaar: (value: number | null) => void;
}

export const useMarketplaceStore = create<MarketplaceState>((set) => ({
  search: '',
  category: 'Все',
  priceRange: 'Любая цена',
  distance: 'До 5 км',
  viewMode: 'grid',
  nearMeOnly: false,
  selectedBazaarId: 1,
  setSearch: (value) => set({ search: value }),
  setCategory: (value) => set({ category: value }),
  setPriceRange: (value) => set({ priceRange: value }),
  setDistance: (value) => set({ distance: value }),
  setViewMode: (value) => set({ viewMode: value }),
  toggleNearMe: () => set((state) => ({ nearMeOnly: !state.nearMeOnly })),
  selectBazaar: (value) => set({ selectedBazaarId: value }),
}));
