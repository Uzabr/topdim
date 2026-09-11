// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import PartnersSky from './PartnersSky';
import { generateSkyMarks, PRODUCT_LETTERS } from './partnersSkyMarks';

vi.mock('@react-three/fiber', () => ({
  Canvas: () => <div data-testid="partners-sky-canvas" />,
  useFrame: () => undefined,
}));

describe('PartnersSky', () => {
  it('renders an inert full-screen cosmos layer', () => {
    render(<PartnersSky />);

    const sky = screen.getByTestId('partners-sky');
    expect(sky.getAttribute('aria-hidden')).toBe('true');
    expect(sky.className).toContain('canvas-container');
    expect(screen.getByTestId('partners-sky-canvas')).toBeTruthy();
  });
});

describe('generateSkyMarks', () => {
  it('places equal counts of s, i, z, b and mixed ticket logos', () => {
    const marks = generateSkyMarks();
    const letters = marks.filter((mark) => mark.kind === 'letter');
    const logos = marks.filter((mark) => mark.kind === 'logo');
    const counts = Object.fromEntries(
      PRODUCT_LETTERS.map((ch) => [ch, letters.filter((mark) => mark.ch === ch).length]),
    );

    expect(logos.length).toBe(23);
    expect(counts).toEqual({ s: 35, i: 35, z: 35, b: 35 });
  });
});
