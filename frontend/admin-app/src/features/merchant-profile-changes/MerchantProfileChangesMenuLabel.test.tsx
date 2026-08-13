import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchPendingMerchantProfileChangeCount } from './api';
import { MerchantProfileChangesMenuLabel } from './MerchantProfileChangesMenuLabel';

vi.mock('./api', () => ({
  MERCHANT_PROFILE_CHANGES_QUERY_KEY: ['merchant-profile-changes'],
  fetchPendingMerchantProfileChangeCount: vi.fn(),
}));

describe('MerchantProfileChangesMenuLabel', () => {
  beforeEach(() => {
    vi.mocked(fetchPendingMerchantProfileChangeCount).mockResolvedValue(37);
  });

  it('shows the complete pending-review server count next to the menu label', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MerchantProfileChangesMenuLabel />
      </QueryClientProvider>,
    );

    expect(screen.getByText('Изменения компаний')).toBeTruthy();
    await waitFor(() => {
      expect(document.querySelector('[title="37"]')).toBeTruthy();
    });
  });
});
