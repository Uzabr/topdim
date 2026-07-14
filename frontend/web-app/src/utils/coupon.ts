/** Полезная нагрузка QR купона — формат, который читает partner-панель при погашении. */
export function buildQrPayload(qrToken: string): string {
  return `TOPDIM-QR:${qrToken}`;
}
