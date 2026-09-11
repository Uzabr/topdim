import { useMemo, useRef } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import { generateSkyMarks, PRODUCT_LETTERS, LETTER_COLORS } from './partnersSkyMarks';
const SKY_MARKS = generateSkyMarks();

function letterTexture(ch: string, color: string): THREE.CanvasTexture {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return new THREE.CanvasTexture(canvas);
  }
  ctx.clearRect(0, 0, 128, 128);
  ctx.font = '800 92px Helvetica, Arial, sans-serif';
  ctx.fillStyle = color;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(ch, 64, 70);
  const texture = new THREE.CanvasTexture(canvas);
  texture.needsUpdate = true;
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

function logoTexture(): THREE.CanvasTexture {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 256;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return new THREE.CanvasTexture(canvas);
  }
  ctx.clearRect(0, 0, 256, 256);

  const round = (x: number, y: number, w: number, h: number, r: number) => {
    ctx.beginPath();
    if (typeof ctx.roundRect === 'function') {
      ctx.roundRect(x, y, w, h, r);
    } else {
      ctx.rect(x, y, w, h);
    }
  };

  ctx.fillStyle = '#ffd23c';
  round(24, 64, 100, 128, 28);
  ctx.fill();
  ctx.fillStyle = '#141414';
  round(132, 64, 100, 128, 28);
  ctx.fill();
  ctx.fillStyle = '#ffd23c';
  ctx.beginPath();
  ctx.arc(128, 128, 24, 0, Math.PI * 2);
  ctx.fill();

  ctx.strokeStyle = '#ffffff';
  ctx.lineWidth = 2;
  ctx.lineJoin = 'round';
  ctx.lineCap = 'round';
  round(24, 64, 208, 128, 28);
  ctx.stroke();

  ctx.globalCompositeOperation = 'destination-out';
  ctx.beginPath();
  ctx.arc(24, 128, 12, 0, Math.PI * 2);
  ctx.arc(232, 128, 12, 0, Math.PI * 2);
  ctx.fill();
  ctx.globalCompositeOperation = 'source-over';

  const texture = new THREE.CanvasTexture(canvas);
  texture.needsUpdate = true;
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

function BrandGalaxy() {
  const group = useRef<THREE.Group>(null);
  const reduceMotion = useMemo(
    () =>
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    [],
  );

  const textures = useMemo(() => {
    const map = new Map<string, THREE.CanvasTexture>();
    for (const ch of PRODUCT_LETTERS) {
      for (const color of LETTER_COLORS) {
        map.set(`${ch}:${color}`, letterTexture(ch, color));
      }
    }
    map.set('logo', logoTexture());
    return map;
  }, []);

  useFrame((_, delta) => {
    if (!group.current || reduceMotion) return;
    group.current.rotation.x -= delta / 10;
    group.current.rotation.y -= delta / 15;
    group.current.position.z = (window.scrollY * 0.005) % 10;
  });

  return (
    <group ref={group} rotation={[0, 0, Math.PI / 4]}>
      {SKY_MARKS.map((mark, i) => {
        const map =
          mark.kind === 'logo'
            ? textures.get('logo')
            : textures.get(`${mark.ch}:${mark.color}`);
        return (
          <sprite key={`${mark.kind}-${i}`} position={[mark.x, mark.y, mark.z]} scale={[mark.size, mark.size, 1]}>
            <spriteMaterial map={map} transparent depthWrite={false} toneMapped={false} />
          </sprite>
        );
      })}
    </group>
  );
}

/**
 * Космос лендинга: вместо звёзд летают буквы sizbiz и логотип-купон.
 * Не перехватывает клики, скрыт от скринридеров.
 */
export default function PartnersSky() {
  return (
    <div className="canvas-container" data-testid="partners-sky" aria-hidden="true">
      <Canvas camera={{ position: [0, 0, 5], fov: 60 }} dpr={[1, 1.75]}>
        <BrandGalaxy />
      </Canvas>
    </div>
  );
}
