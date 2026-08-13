import { App as AntApp } from 'antd';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import CompanyLocationFields, { type EditorCompanyLocation } from './CompanyLocationFields';

const primaryLocation: EditorCompanyLocation = {
  clientId: 71,
  sourceLocationId: 71,
  title: 'Главный филиал',
  address: 'Ташкент, ул. Амира Темура, 1',
  phone: '+998 90 111 22 33',
  workingHours: '09:00–21:00',
  latitude: 41.311,
  longitude: 69.279,
  primary: true,
  active: true,
};

function renderFields(
  location: EditorCompanyLocation,
  overrides: Partial<React.ComponentProps<typeof CompanyLocationFields>> = {},
) {
  const onChange = vi.fn();
  const onMakePrimary = vi.fn();
  const onRemoveNew = vi.fn();
  render(
    <AntApp>
      <CompanyLocationFields
        index={0}
        location={location}
        readOnly={false}
        onChange={onChange}
        onMakePrimary={onMakePrimary}
        onRemoveNew={onRemoveNew}
        {...overrides}
      />
    </AntApp>,
  );
  return { onChange, onMakePrimary, onRemoveNew };
}

describe('CompanyLocationFields', () => {
  it('refuses to disable the active primary location before another primary is selected', async () => {
    const { onChange } = renderFields(primaryLocation);

    fireEvent.click(screen.getByRole('button', { name: /Отключить филиал/ }));

    expect(await screen.findByText('Сначала выберите другой основной филиал')).toBeTruthy();
    expect(onChange).not.toHaveBeenCalled();
  });

  it('keeps a persisted non-primary location and marks it inactive', () => {
    const branch = { ...primaryLocation, clientId: 72, sourceLocationId: 72, primary: false };
    const { onChange, onRemoveNew } = renderFields(branch);

    fireEvent.click(screen.getByRole('button', { name: /Отключить филиал/ }));

    expect(onChange).toHaveBeenCalledWith({ active: false, primary: false });
    expect(onRemoveNew).not.toHaveBeenCalled();
    expect(screen.getByDisplayValue('Главный филиал')).toBeTruthy();
  });

  it('allows a newly added location to be removed instead of persisted', () => {
    const newLocation = {
      ...primaryLocation,
      clientId: -1,
      sourceLocationId: null,
      primary: false,
    };
    const { onRemoveNew } = renderFields(newLocation);

    fireEvent.click(screen.getByRole('button', { name: /Удалить новый филиал/ }));

    expect(onRemoveNew).toHaveBeenCalledOnce();
  });
});
