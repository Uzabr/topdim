import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

describe('admin test environment', () => {
  it('renders React in jsdom', () => {
    render(<button type="button">Купоны</button>);

    expect(screen.getByRole('button', { name: 'Купоны' })).toBeTruthy();
  });
});
