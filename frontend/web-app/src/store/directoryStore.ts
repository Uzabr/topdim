import { create } from 'zustand';
import type { Bazaar, Shop } from '../api/bazaars';

interface DirectoryState {
  // Search & filters
  search: string;
  activeTab: 'bazaars' | 'shops';
  bazaarTypeFilter: string;       // 'Все' | 'BAZAAR' | 'SHOPPING_CENTER' | ...
  shopCategoryFilter: string;

  // Map area selection
  isAreaSelecting: boolean;
  selectedAreaBounds: { minLat: number; maxLat: number; minLon: number; maxLon: number } | null;
  areaBazaars: Bazaar[];
  areaShops: Shop[];
  showResultsPanel: boolean;

  // Selected bazaar (from sidebar click or map marker)
  selectedBazaarId: number | null;

  // Actions
  setSearch: (value: string) => void;
  setActiveTab: (tab: 'bazaars' | 'shops') => void;
  setBazaarTypeFilter: (type: string) => void;
  setShopCategoryFilter: (cat: string) => void;
  toggleAreaSelecting: () => void;
  setAreaResults: (bazaars: Bazaar[], shops: Shop[], bounds: { minLat: number; maxLat: number; minLon: number; maxLon: number }) => void;
  clearAreaResults: () => void;
  selectBazaar: (id: number | null) => void;
}

export const useDirectoryStore = create<DirectoryState>((set) => ({
  search: '',
  activeTab: 'bazaars',
  bazaarTypeFilter: 'Все',
  shopCategoryFilter: 'Все',
  isAreaSelecting: false,
  selectedAreaBounds: null,
  areaBazaars: [],
  areaShops: [],
  showResultsPanel: false,
  selectedBazaarId: null,

  setSearch: (value) => set({ search: value }),
  setActiveTab: (tab) => set({ activeTab: tab }),
  setBazaarTypeFilter: (type) => set({ bazaarTypeFilter: type }),
  setShopCategoryFilter: (cat) => set({ shopCategoryFilter: cat }),

  toggleAreaSelecting: () =>
    set((s) => ({
      isAreaSelecting: !s.isAreaSelecting,
      // Clear results when toggling off
      ...(!s.isAreaSelecting ? {} : { showResultsPanel: false, areaBazaars: [], areaShops: [], selectedAreaBounds: null }),
    })),

  setAreaResults: (bazaars, shops, bounds) =>
    set({
      areaBazaars: bazaars,
      areaShops: shops,
      selectedAreaBounds: bounds,
      showResultsPanel: true,
      isAreaSelecting: false,
    }),

  clearAreaResults: () =>
    set({
      areaBazaars: [],
      areaShops: [],
      selectedAreaBounds: null,
      showResultsPanel: false,
      isAreaSelecting: false,
    }),

  selectBazaar: (id) => set({ selectedBazaarId: id }),
}));
