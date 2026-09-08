export const PRODUCT_LETTERS = ['s', 'i', 'z', 'b'] as const;
export const LETTER_COLORS = ['#ffd23c', '#ffffff', '#ffe38a', '#d8d8d8'] as const;
const LETTERS_PER_CHAR = 35;
const LOGO_COUNT = 23;

export type SkyMark =
  | { kind: 'letter'; ch: (typeof PRODUCT_LETTERS)[number]; color: string; size: number; x: number; y: number; z: number }
  | { kind: 'logo'; size: number; x: number; y: number; z: number };

function mulberry32(seed: number) {
  let a = seed;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function pointOnSphere(rand: () => number, radius: number) {
  const r = radius * Math.cbrt(rand());
  const theta = rand() * Math.PI * 2;
  const phi = Math.acos(2 * rand() - 1);
  return {
    x: r * Math.sin(phi) * Math.cos(theta),
    y: r * Math.sin(phi) * Math.sin(theta),
    z: r * Math.cos(phi),
  };
}

function letterSize(rand: () => number) {
  return rand() < 0.18 ? 0.72 + rand() * 0.5 : 0.16 + rand() * 0.34;
}

function logoSize(rand: () => number) {
  return rand() < 0.2 ? 0.7 + rand() * 0.45 : 0.26 + rand() * 0.32;
}

/** Облако букв s/i/z/b поровну и логотипов-купонов — вместо звёзд. */
export function generateSkyMarks(
  lettersPerChar = LETTERS_PER_CHAR,
  logoCount = LOGO_COUNT,
  seed = 42,
): SkyMark[] {
  const rand = mulberry32(seed);
  const marks: SkyMark[] = [];

  for (const ch of PRODUCT_LETTERS) {
    for (let i = 0; i < lettersPerChar; i++) {
      marks.push({
        kind: 'letter',
        ch,
        color: LETTER_COLORS[Math.floor(rand() * LETTER_COLORS.length)],
        size: letterSize(rand),
        ...pointOnSphere(rand, 11),
      });
    }
  }

  for (let i = 0; i < logoCount; i++) {
    marks.push({
      kind: 'logo',
      size: logoSize(rand),
      ...pointOnSphere(rand, 11),
    });
  }

  return marks;
}
