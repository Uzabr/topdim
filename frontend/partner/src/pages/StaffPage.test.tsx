import { App as AntApp } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../api';
import StaffPage from './StaffPage';

vi.mock('../api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));

const mockedApi = vi.mocked(api);

describe('StaffPage manager creation', () => {
  beforeEach(() => {
    mockedApi.get.mockImplementation(async (url: string) => {
      if (url.endsWith('/locations')) {
        return {
          data: {
            data: [{ id: 7, title: 'Главный филиал', address: 'Ташкент' }],
          },
        };
      }
      return { data: { data: [] } };
    });
  });

  it('offers manager role and removes the cashier location requirement', async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <AntApp>
          <StaffPage />
        </AntApp>
      </QueryClientProvider>,
    );

    await user.click(await screen.findByRole('button', { name: /Добавить/i }));
    const roleSelect = document.querySelector('#staff-role');
    expect(roleSelect).toBeTruthy();
    fireEvent.mouseDown(roleSelect!);
    await user.click(await screen.findByText('Менеджер'));

    expect(document.querySelector('#staff-location')).toBeNull();
    expect(document.querySelector('#staff-email')).toBeTruthy();
    expect(document.querySelector('#staff-password')).toBeTruthy();
  });
});
