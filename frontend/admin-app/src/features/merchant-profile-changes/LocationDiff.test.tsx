import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { LocationDiff } from './LocationDiff';

describe('LocationDiff', () => {
  it('matches an existing location by sourceLocationId and marks its requested disable', () => {
    render(
      <LocationDiff
        currentLocations={[{
          id: 11,
          title: 'Чиланзар',
          address: 'ул. Катартал, 1',
          phone: '+998909999999',
          workingHours: '09:00-22:00',
          latitude: 41.27,
          longitude: 69.20,
          primary: true,
          active: true,
        }]}
        proposedLocations={[{
          id: 701,
          sourceLocationId: 11,
          title: 'Чиланзар',
          address: 'ул. Катартал, 1',
          phone: '+998901111111',
          workingHours: '09:00-22:00',
          latitude: 41.27,
          longitude: 69.20,
          primary: false,
          active: false,
          sortOrder: 0,
        }]}
      />,
    );

    expect(screen.getAllByText('Чиланзар').length).toBeGreaterThan(0);
    expect(screen.getByText('Филиал будет отключён')).toBeTruthy();
    expect(screen.getByText('+998901111111')).toBeTruthy();
    expect(screen.getByText('+998909999999')).toBeTruthy();
  });

  it('identifies a new location by missing sourceLocationId, not by request id', () => {
    render(
      <LocationDiff
        currentLocations={[]}
        proposedLocations={[{
          id: 902,
          sourceLocationId: null,
          title: 'Новый филиал',
          address: 'ул. Навои, 7',
          phone: '+998902222222',
          workingHours: null,
          latitude: null,
          longitude: null,
          primary: true,
          active: true,
          sortOrder: 0,
        }]}
      />,
    );

    expect(screen.getAllByText('Новый филиал').length).toBeGreaterThan(0);
    expect(screen.getByText('Будет добавлен')).toBeTruthy();
  });
});
