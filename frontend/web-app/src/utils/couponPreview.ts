export function deriveCouponPreview(offerDescription?: string, maxLength: number = 150): string | undefined {
  const text = offerDescription?.trim();
  if (!text) return undefined;

  const line = text
    .split('\n')
    .map((part) => part.trim())
    .find((part) => part && !part.startsWith('##'));

  if (!line) return undefined;
  return line.length <= maxLength ? line : `${line.slice(0, maxLength - 1)}…`;
}
