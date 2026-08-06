import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../api';
import RedeemPage from './RedeemPage';

const scannerStart = vi.hoisted(() => vi.fn());
const scannerStop = vi.hoisted(() => vi.fn().mockResolvedValue(undefined));

vi.mock('html5-qrcode', () => ({
  Html5Qrcode: vi.fn(function Html5QrcodeMock() {
    return {
      start: scannerStart,
      stop: scannerStop,
      getState: vi.fn().mockReturnValue(2),
    };
  }),
}));

vi.mock('../api', () => ({
  default: { post: vi.fn() },
}));

const mockedApi = vi.mocked(api);

function createQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

function renderRedeem(queryClient: QueryClient) {
  return render(
    <QueryClientProvider client={queryClient}>
      <RedeemPage />
    </QueryClientProvider>,
  );
}

describe('RedeemPage history refresh', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    scannerStart.mockReset();
    scannerStart.mockResolvedValue(undefined);
    mockedApi.post.mockReset();
  });

  it('invalidates redemption history after successful PIN redemption', async () => {
    const queryClient = createQueryClient();
    const historyKey = ['partner-redemptions', { page: 1 }];
    queryClient.setQueryData(historyKey, { content: [] });
    mockedApi.post.mockResolvedValue({
      data: {
        data: {
          purchasedCouponId: 1,
          couponTitle: 'SPA',
          optionTitle: 'VIP',
          couponCode: 'CP-1234',
          merchantName: 'Oasis',
          status: 'USED',
        },
      },
    });

    renderRedeem(queryClient);
    fireEvent.change(screen.getByPlaceholderText('CP-XXXX1234'), {
      target: { value: 'CP-1234' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Погасить$/ }));

    expect((await screen.findAllByText('Купон погашен по PIN!')).length).toBeGreaterThan(0);
    expect(queryClient.getQueryState(historyKey)?.isInvalidated).toBe(true);
  });

  it('invalidates redemption history after successful QR redemption', async () => {
    const queryClient = createQueryClient();
    const historyKey = ['partner-redemptions', { page: 2 }];
    queryClient.setQueryData(historyKey, { content: [] });
    mockedApi.post.mockResolvedValue({
      data: {
        data: {
          purchasedCouponId: 2,
          couponTitle: 'Dinner',
          optionTitle: 'Two guests',
          couponCode: 'CP-QR-1',
          merchantName: 'Oasis',
          status: 'USED',
        },
      },
    });
    scannerStart.mockImplementation(async (
      _camera: unknown,
      _config: unknown,
      onSuccess: (decodedText: string) => Promise<void>,
    ) => {
      await onSuccess('TOPDIM-QR:qr-token-1');
    });

    renderRedeem(queryClient);
    fireEvent.click(screen.getByRole('tab', { name: /Сканировать QR/i }));
    fireEvent.click(screen.getByRole('button', { name: /Открыть камеру$/ }));

    await waitFor(() => expect(mockedApi.post).toHaveBeenCalledWith(
      '/api/v1/partner/redemptions/qr',
      { qrToken: 'qr-token-1' },
    ));
    expect((await screen.findAllByText('Купон погашен по QR!')).length).toBeGreaterThan(0);
    expect(queryClient.getQueryState(historyKey)?.isInvalidated).toBe(true);
  });

  it('keeps cached history valid when redemption fails', async () => {
    const queryClient = createQueryClient();
    const historyKey = ['partner-redemptions', { page: 1 }];
    queryClient.setQueryData(historyKey, { content: [] });
    mockedApi.post.mockRejectedValue({
      response: { data: { message: 'Купон уже использован' } },
    });

    renderRedeem(queryClient);
    fireEvent.change(screen.getByPlaceholderText('CP-XXXX1234'), {
      target: { value: 'CP-USED' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Погасить$/ }));

    expect(await screen.findByText('Не удалось погасить купон')).toBeTruthy();
    expect(queryClient.getQueryState(historyKey)?.isInvalidated).toBe(false);
  });
});
