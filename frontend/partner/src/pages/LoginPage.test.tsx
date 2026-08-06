import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../api';
import LoginPage from './LoginPage';

vi.mock('../api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

const mockedApi = vi.mocked(api);

function renderLogin() {
  render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<div>Partner portal</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function submitCredentials() {
  fireEvent.change(screen.getByPlaceholderText('Email'), {
    target: { value: 'cashier@sizbiz.uz' },
  });
  fireEvent.change(screen.getByPlaceholderText('Пароль'), {
    target: { value: 'secret123' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Войти' }));
}

describe('LoginPage partner access context', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    mockedApi.post.mockResolvedValue({
      data: {
        data: {
          accessToken: 'new-access-token',
          user: { id: 15, email: 'cashier@sizbiz.uz' },
        },
      },
    });
  });

  it('does not create an owner session when access context loading fails', async () => {
    mockedApi.get.mockRejectedValue(new Error('identity service unavailable'));
    renderLogin();

    submitCredentials();

    await waitFor(() => expect(mockedApi.get).toHaveBeenCalled());
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
    expect(localStorage.getItem('partnerContext')).toBeNull();
    expect(screen.queryByText('Partner portal')).toBeNull();
    expect(screen.getByRole('button', { name: 'Войти' })).toBeTruthy();
  });

  it('stores the validated partner context before opening the portal', async () => {
    mockedApi.get.mockResolvedValue({
      data: {
        data: {
          role: 'CASHIER',
          merchantId: 8,
          merchantLocationId: 21,
          staffId: 15,
          staffName: 'Кассир',
          canViewDashboard: false,
          canRedeem: true,
        },
      },
    });
    renderLogin();

    submitCredentials();

    expect(await screen.findByText('Partner portal')).toBeTruthy();
    expect(localStorage.getItem('token')).toBe('new-access-token');
    expect(JSON.parse(localStorage.getItem('partnerContext') || '{}')).toMatchObject({
      role: 'CASHIER',
      merchantId: 8,
      merchantLocationId: 21,
    });
  });

  it('does not persist a partial session when the context payload is invalid', async () => {
    mockedApi.get.mockResolvedValue({
      data: {
        data: {
          role: 'OWNER',
          canViewDashboard: true,
          canRedeem: true,
        },
      },
    });
    renderLogin();

    submitCredentials();

    await waitFor(() => expect(mockedApi.get).toHaveBeenCalled());
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
    expect(localStorage.getItem('partnerContext')).toBeNull();
    expect(screen.queryByText('Partner portal')).toBeNull();
  });
});
