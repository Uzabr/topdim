import { beforeEach, describe, expect, it, vi } from 'vitest';
import { favoritesApi } from '../api/favorites';
import { useFavoritesStore } from './favoritesStore';

vi.mock('../api/favorites', () => ({
  favoritesApi: {
    getAll: vi.fn(),
    add: vi.fn(),
    remove: vi.fn(),
  },
}));

describe('favoritesStore backend hydration', () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('accessToken', 'valid-token');
    useFavoritesStore.setState({ favoriteIds: [] });
  });

  it('merges server favorites with local favorites and uploads missing local ids', async () => {
    vi.mocked(favoritesApi.getAll).mockResolvedValue({
      data: {
        success: true,
        data: [
          { id: 10, couponOfferId: 2, createdAt: '2026-07-22T10:00:00' },
          { id: 11, couponOfferId: 3, createdAt: '2026-07-22T10:01:00' },
        ],
        timestamp: '2026-07-22T10:02:00',
      },
    } as Awaited<ReturnType<typeof favoritesApi.getAll>>);
    vi.mocked(favoritesApi.add).mockResolvedValue({} as Awaited<ReturnType<typeof favoritesApi.add>>);
    useFavoritesStore.setState({ favoriteIds: [1, 2] });

    await useFavoritesStore.getState().syncWithBackend();

    expect(favoritesApi.add).toHaveBeenCalledOnce();
    expect(favoritesApi.add).toHaveBeenCalledWith(1);
    expect(useFavoritesStore.getState().favoriteIds).toEqual([2, 3, 1]);
  });

  it('keeps local favorites when backend hydration fails', async () => {
    vi.mocked(favoritesApi.getAll).mockRejectedValue({ response: { status: 401 } });
    useFavoritesStore.setState({ favoriteIds: [7] });

    await expect(useFavoritesStore.getState().syncWithBackend()).resolves.toBeUndefined();

    expect(useFavoritesStore.getState().favoriteIds).toEqual([7]);
    expect(favoritesApi.add).not.toHaveBeenCalled();
  });
});
