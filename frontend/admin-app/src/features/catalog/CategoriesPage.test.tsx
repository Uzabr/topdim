import { App } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CategoriesPage } from './CategoriesPage';
import {
  createCategory,
  deleteCategory,
  getAdminCategories,
  updateCategory,
} from './categoriesApi';

vi.mock('./categoriesApi', () => ({
  getAdminCategories: vi.fn(),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  deleteCategory: vi.fn(),
}));

const categories = [
  {
    id: 1,
    name: 'Еда',
    nameUz: 'Ovqat',
    slug: 'food',
    iconUrl: '/food.png',
    sortOrder: 1,
    active: true,
  },
  {
    id: 2,
    name: 'Архив',
    nameUz: '',
    slug: 'archive',
    iconUrl: '',
    sortOrder: 2,
    active: false,
  },
];

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <App>
        <CategoriesPage />
      </App>
    </QueryClientProvider>,
  );
}

describe('CategoriesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAdminCategories).mockResolvedValue(categories);
    vi.mocked(createCategory).mockResolvedValue(10);
    vi.mocked(updateCategory).mockResolvedValue(categories[0]);
    vi.mocked(deleteCategory).mockResolvedValue(undefined);
  });

  it('shows inactive categories and opens a complete edit form', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Архив')).toBeTruthy();
    expect(screen.getByText('Выключена')).toBeTruthy();

    const archiveRow = screen.getByText('Архив').closest('tr');
    expect(archiveRow).not.toBeNull();
    await user.click(within(archiveRow as HTMLElement).getByRole('button', { name: 'Редактировать' }));

    expect(await screen.findByRole('dialog', { name: 'Редактирование категории' })).toBeTruthy();
    expect((screen.getByLabelText('Название (RU)') as HTMLInputElement).value).toBe('Архив');
    expect((screen.getByLabelText('Название (UZ)') as HTMLInputElement).value).toBe('');
    expect((screen.getByLabelText('Slug') as HTMLInputElement).value).toBe('archive');
    expect((screen.getByLabelText('URL иконки') as HTMLInputElement).value).toBe('');
    expect((screen.getByLabelText('Порядок сортировки') as HTMLInputElement).value).toBe('2');
    expect(screen.getByRole('button', { name: 'Загрузить иконку' })).toBeTruthy();
  });

  it('submits all editable fields to the update endpoint', async () => {
    const user = userEvent.setup();
    renderPage();

    const foodRow = (await screen.findByText('Еда')).closest('tr');
    await user.click(within(foodRow as HTMLElement).getByRole('button', { name: 'Редактировать' }));
    const nameInput = await screen.findByLabelText('Название (RU)');
    await user.clear(nameInput);
    await user.type(nameInput, 'Кафе');
    await user.click(screen.getByRole('button', { name: 'Сохранить' }));

    await waitFor(() => {
      expect(updateCategory).toHaveBeenCalledWith(1, {
        name: 'Кафе',
        nameUz: 'Ovqat',
        slug: 'food',
        iconUrl: '/food.png',
        sortOrder: 1,
        active: true,
      });
    });
  });

  it('keeps the category visible and shows the backend conflict on referenced delete', async () => {
    const user = userEvent.setup();
    vi.mocked(deleteCategory).mockRejectedValue({
      response: { data: { message: 'Категория используется купонами' } },
    });
    renderPage();

    const archiveRow = (await screen.findByText('Архив')).closest('tr');
    await user.click(within(archiveRow as HTMLElement).getByRole('button', { name: 'Удалить' }));
    await user.click(await screen.findByRole('button', { name: 'Да, удалить' }));

    expect(await screen.findByText('Категория используется купонами')).toBeTruthy();
    expect(screen.getByText('Архив')).toBeTruthy();
  });
});
