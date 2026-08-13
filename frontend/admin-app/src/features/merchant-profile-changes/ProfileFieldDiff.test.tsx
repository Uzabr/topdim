import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ProfileFieldDiff } from './ProfileFieldDiff';

describe('ProfileFieldDiff', () => {
  it('shows literal current and proposed values and marks a changed field', () => {
    render(<ProfileFieldDiff label="Название" before="Safia" after="Safia Cafe" />);

    expect(screen.getByText('Сейчас')).toBeTruthy();
    expect(screen.getByText('Предлагается')).toBeTruthy();
    expect(screen.getByText('Safia')).toBeTruthy();
    expect(screen.getByText('Safia Cafe')).toBeTruthy();
    expect(screen.getByText('Изменено')).toBeTruthy();
  });

  it('renders an empty value explicitly without marking equal values as changed', () => {
    render(<ProfileFieldDiff label="Сайт" before={null} after={null} />);

    expect(screen.getAllByText('—')).toHaveLength(2);
    expect(screen.queryByText('Изменено')).toBeNull();
  });
});
