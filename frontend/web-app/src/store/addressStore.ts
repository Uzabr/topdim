import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/** Адрес пользователя из шапки («нетерпеливый пин»). Нужен для расстояния до заведения. */
interface AddressState {
  address: string;
  setAddress: (address: string) => void;
  clearAddress: () => void;
}

export const useAddressStore = create<AddressState>()(
  persist(
    (set) => ({
      address: '',
      setAddress: (address) => set({ address: address.trim() }),
      clearAddress: () => set({ address: '' }),
    }),
    { name: 'address-storage' },
  ),
);
