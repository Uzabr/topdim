/**
 * Звук «плюп» капли — Web Audio, два слоя: удар 160→70 Гц (60 мс) и плинк
 * 380→1350 Гц (150 мс). Громкость ≤ 0.14 (design_handoff_sizbiz → playDrop()).
 *
 * Контекст создаётся лениво и переиспользуется: браузер разрешает звук только
 * после жеста пользователя, а вызов у нас всегда по клику.
 */
let ctx: AudioContext | null = null;

export function playDrop(): void {
  if (typeof window === 'undefined') return;
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;

  try {
    ctx ??= new AudioContext();
    if (ctx.state === 'suspended') void ctx.resume();

    const t = ctx.currentTime;

    const thump = ctx.createOscillator();
    const thumpGain = ctx.createGain();
    thump.type = 'sine';
    thump.frequency.setValueAtTime(160, t);
    thump.frequency.exponentialRampToValueAtTime(70, t + 0.05);
    thumpGain.gain.setValueAtTime(0.1, t);
    thumpGain.gain.exponentialRampToValueAtTime(0.0001, t + 0.06);
    thump.connect(thumpGain).connect(ctx.destination);
    thump.start(t);
    thump.stop(t + 0.07);

    const plink = ctx.createOscillator();
    const plinkGain = ctx.createGain();
    plink.type = 'sine';
    plink.frequency.setValueAtTime(380, t + 0.02);
    plink.frequency.exponentialRampToValueAtTime(1350, t + 0.13);
    plinkGain.gain.setValueAtTime(0.0001, t + 0.02);
    plinkGain.gain.exponentialRampToValueAtTime(0.14, t + 0.035);
    plinkGain.gain.exponentialRampToValueAtTime(0.0001, t + 0.17);
    plink.connect(plinkGain).connect(ctx.destination);
    plink.start(t + 0.02);
    plink.stop(t + 0.18);
  } catch {
    // Звук — украшение: если AudioContext недоступен, молча пропускаем.
  }
}
