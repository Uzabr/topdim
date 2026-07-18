import { useEffect, useRef, useCallback } from 'react';
import { load } from '@2gis/mapgl';
import type { Bazaar } from '../../api/bazaars';
import './TwoGisMap.css';

import type { Map, Marker, Polygon, MapPointerEvent } from '@2gis/mapgl/types';

const MAPGL_KEY = '3b3d04f2-7dc0-46bc-899b-2c8652fd4813'; // public demo key

interface TwoGisMapProps {
  center?: [number, number]; // [lon, lat]
  zoom?: number;
  bazaars?: Bazaar[];
  selectedBazaarId?: number | null;
  onBazaarClick?: (id: number) => void;
  isDrawing?: boolean;
  onAreaSelect?: (bounds: { minLat: number; maxLat: number; minLon: number; maxLon: number }) => void;
  className?: string;
  /** Static single marker mode (for detail pages) */
  staticMarker?: { lat: number; lon: number; title?: string };
}

export default function TwoGisMap({
  center = [69.2797, 41.3111],
  zoom = 12,
  bazaars = [],
  selectedBazaarId,
  onBazaarClick,
  isDrawing = false,
  onAreaSelect,
  className = '',
  staticMarker,
}: TwoGisMapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<Map | null>(null);
  const mapglRef = useRef<Awaited<ReturnType<typeof load>> | null>(null);
  const markersRef = useRef<Marker[]>([]);
  const drawPolygonRef = useRef<Polygon | null>(null);
  const drawPointsRef = useRef<[number, number][]>([]);
  const tempMarkersRef = useRef<Marker[]>([]);

  // Initialize map
  useEffect(() => {
    let destroyed = false;

    load().then((mapgl) => {
      if (destroyed || !containerRef.current) return;

      mapglRef.current = mapgl;
      const map = new mapgl.Map(containerRef.current, {
        center,
        zoom,
        key: MAPGL_KEY,
      });
      mapRef.current = map;
    });

    return () => {
      destroyed = true;
      mapRef.current?.destroy();
      mapRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Static marker (for detail pages)
  useEffect(() => {
    const map = mapRef.current;
    const mapgl = mapglRef.current;
    if (!map || !mapgl || !staticMarker) return;

    const marker = new mapgl.Marker(map, {
      coordinates: [staticMarker.lon, staticMarker.lat],
      label: { text: staticMarker.title || '' },
    });

    map.setCenter([staticMarker.lon, staticMarker.lat]);
    map.setZoom(15);

    return () => marker.destroy();
  }, [staticMarker]);

  // Bazaar markers
  useEffect(() => {
    const map = mapRef.current;
    const mapgl = mapglRef.current;
    if (!map || !mapgl) return;

    // Give map time to load
    const timer = setTimeout(() => {
      // Clear old markers
      markersRef.current.forEach((m) => m.destroy());
      markersRef.current = [];

      bazaars.forEach((b) => {
        const isSelected = b.id === selectedBazaarId;
        const marker = new mapgl.Marker(map, {
          coordinates: [b.longitude, b.latitude],
          label: {
            text: b.name,
            offset: [0, -60],
            relativeAnchor: [0.5, 0],
          },
          size: isSelected ? [40, 40] : [32, 32],
        });

        marker.on('click', () => onBazaarClick?.(b.id));

        markersRef.current.push(marker);
      });
    }, 500);

    return () => {
      clearTimeout(timer);
      markersRef.current.forEach((m) => m.destroy());
      markersRef.current = [];
    };
  }, [bazaars, selectedBazaarId, onBazaarClick]);

  // Drawing mode — simple click-based polygon
  const clearDrawing = useCallback(() => {
    drawPointsRef.current = [];
    tempMarkersRef.current.forEach((m) => m.destroy());
    tempMarkersRef.current = [];
    drawPolygonRef.current?.destroy();
    drawPolygonRef.current = null;
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    const mapgl = mapglRef.current;
    const container = containerRef.current;
    if (!map || !mapgl) return;

    if (!isDrawing) {
      clearDrawing();
      return;
    }

    // Change cursor
    if (container) {
      container.style.cursor = 'crosshair';
    }

    const handleClick = (e: MapPointerEvent) => {
      const [lon, lat] = e.lngLat;
      drawPointsRef.current.push([lon, lat]);

      // Add point marker
      const pointMarker = new mapgl.Marker(map, {
        coordinates: [lon, lat],
        size: [12, 12],
      });
      tempMarkersRef.current.push(pointMarker);

      // Update polygon visualization
      if (drawPointsRef.current.length >= 3) {
        drawPolygonRef.current?.destroy();
        const coords = [...drawPointsRef.current, drawPointsRef.current[0]];
        drawPolygonRef.current = new mapgl.Polygon(map, {
          coordinates: [coords],
          color: 'rgba(20, 20, 20, 0.12)',
          strokeColor: '#141414',
          strokeWidth: 3,
        });
      }
    };

    const handleDblClick = () => {
      if (drawPointsRef.current.length < 3) return;

      // Calculate bounding box from polygon points
      const lats = drawPointsRef.current.map((p) => p[1]);
      const lons = drawPointsRef.current.map((p) => p[0]);

      const bounds = {
        minLat: Math.min(...lats),
        maxLat: Math.max(...lats),
        minLon: Math.min(...lons),
        maxLon: Math.max(...lons),
      };

      onAreaSelect?.(bounds);

      if (container) {
        container.style.cursor = '';
      }
    };

    map.on('click', handleClick);
    // @ts-expect-error MapGL typing is missing dblclick event
    map.on('dblclick', handleDblClick);

    return () => {
      map.off('click', handleClick);
      // @ts-expect-error MapGL typing is missing dblclick event
      map.off('dblclick', handleDblClick);
      if (container) {
        container.style.cursor = '';
      }
    };
  }, [isDrawing, onAreaSelect, clearDrawing]);

  return <div ref={containerRef} className={`twogis-map ${className}`} />;
}
