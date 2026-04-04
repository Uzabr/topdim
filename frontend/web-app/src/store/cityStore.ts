import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface City {
  id: string;
  name: string;
  nameUz: string;
  latitude: number;
  longitude: number;
}

export const CITIES: City[] = [
  { id: 'tashkent', name: 'Ташкент', nameUz: 'Toshkent', latitude: 41.2995, longitude: 69.2401 },
  { id: 'samarkand', name: 'Самарканд', nameUz: 'Samarqand', latitude: 39.6542, longitude: 66.9597 },
  { id: 'bukhara', name: 'Бухара', nameUz: 'Buxoro', latitude: 39.7681, longitude: 64.4556 },
  { id: 'fergana', name: 'Фергана', nameUz: 'Farg\'ona', latitude: 40.3842, longitude: 71.7869 },
  { id: 'khiva', name: 'Хива', nameUz: 'Xiva', latitude: 41.3775, longitude: 60.3619 },
];

interface CityState {
  selectedCity: City;
  isDetected: boolean;
  setCity: (city: City) => void;
  detectCity: () => Promise<void>;
}

async function detectCityByIP(): Promise<City | null> {
  try {
    const res = await fetch('https://ipapi.co/json/', { signal: AbortSignal.timeout(3000) });
    if (!res.ok) return null;
    const data = await res.json();
    const lat = data.latitude;
    const lng = data.longitude;

    // Find nearest city
    let nearest = CITIES[0];
    let minDist = Infinity;
    for (const city of CITIES) {
      const dist = Math.sqrt(
        Math.pow(lat - city.latitude, 2) + Math.pow(lng - city.longitude, 2)
      );
      if (dist < minDist) {
        minDist = dist;
        nearest = city;
      }
    }
    return nearest;
  } catch {
    return null;
  }
}

export const useCityStore = create<CityState>()(
  persist(
    (set, get) => ({
      selectedCity: CITIES[0], // Default Tashkent
      isDetected: false,
      setCity: (city) => set({ selectedCity: city }),
      detectCity: async () => {
        if (get().isDetected) return;
        const detected = await detectCityByIP();
        if (detected) {
          set({ selectedCity: detected, isDetected: true });
        }
      },
    }),
    { name: 'city-storage' }
  )
);
