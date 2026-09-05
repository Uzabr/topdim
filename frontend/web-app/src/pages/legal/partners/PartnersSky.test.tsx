// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import PartnersSky from './PartnersSky';

describe('PartnersSky', () => {
  it('places sizbiz letters and the ticket logo in an inert background layer', () => {
    render(<PartnersSky />);

    const sky = screen.getByTestId('partners-sky');
    expect(sky.getAttribute('aria-hidden')).toBe('true');
    expect(sky.textContent?.replace(/\s/g, '')).toBe('sizbiz');
    expect(sky.querySelectorAll('svg')).toHaveLength(2);
  });
});
