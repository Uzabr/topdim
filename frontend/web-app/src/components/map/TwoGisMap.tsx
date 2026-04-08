import React, { useEffect, useRef, useCallback, useId } from 'react';
import { load } from '@2gis/mapgl';
import type { Bazaar } from '../../api/bazaars';
import './TwoGisMap.css';

const MAPGL_KEY = 'REMOVED_MAP_API_KEY';

/** Memoized map container — prevents re-render per 2GIS React docs */
const MapContainer = React.memo(
  ({ id, className }: { id: string; className: string }) => (
    <div id={id} className={className} />
  ),
  () => true,
);

interface TwoGisMapProps {
  center?: [number, number];
  zoom?: number;
  bazaars?: Bazaar[];
  selectedBazaarId?: number | null;
  onBazaarClick?: (id: number) => void;
  isDrawing?: boolean;
  onAreaSelect?: (bounds: { minLat: number; maxLat: number; minLon: number; maxLon: number }) => void;
  className?: string;
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
  // Stable unique ID for the container (survives StrictMode re-mounts)
  const reactId = useId();
  const containerId = `twogis-${reactId.replace(/:/g, '')}`;

  const mapRef = useRef<any>(null);
  const mapglRef = useRef<any>(null);
  const markersRef = useRef<any[]>([]);
  const drawPolygonRef = useRef<any>(null);
  const drawPointsRef = useRef<[number, number][]>([]);
  const tempMarkersRef = useRef<any[]>([]);

  // Initialize map — handle StrictMode double-mount
  useEffect(() => {
    let cancelled = false;

    load().then((mapglAPI) => {
      if (cancelled) return;

      // Destroy any previous map in this container (StrictMode safety)
      if (mapRef.current) {
        mapRef.current.destroy();
        mapRef.current = null;
      }

      mapglRef.current = mapglAPI;
      const map = new mapglAPI.Map(containerId, {
        center,
        zoom,
        key: MAPGL_KEY,
      });
      mapRef.current = map;
    });

    return () => {
      cancelled = true;
      if (mapRef.current) {
        mapRef.current.destroy();
        mapRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [containerId]);

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

    const timer = setTimeout(() => {
      markersRef.current.forEach((m) => m.destroy());
      markersRef.current = [];

      bazaars.forEach((b) => {
        // Временно скрываем маркеры на карте, так как координаты некорректны
        /*
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
        */
      });
    }, 500);

    return () => {
      clearTimeout(timer);
      markersRef.current.forEach((m) => m.destroy());
      markersRef.current = [];
    };
  }, [bazaars, selectedBazaarId, onBazaarClick]);

  // Drawing mode
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

    if (!map || !mapgl) return;

    if (!isDrawing) {
      clearDrawing();
      map.setOption('disableDragging', false);
      return;
    }

    const el = document.getElementById(containerId);
    if (el) el.style.cursor = 'crosshair';

    let isDragging = false;

    const handleMouseDown = (e: any) => {
      isDragging = true;
      map.setOption('disableDragging', true);
      clearDrawing(); // Reset previous polygon
      const [lon, lat] = e.lngLat;
      drawPointsRef.current.push([lon, lat]);
    };

    const handleMouseMove = (e: any) => {
      if (!isDragging) return;
      
      const [lon, lat] = e.lngLat;
      // MapGL might fire mousemove very frequently, we can throttle or just push
      drawPointsRef.current.push([lon, lat]);

      // Draw polygon with current points
      if (drawPointsRef.current.length > 2) {
        drawPolygonRef.current?.destroy();
        const coords = [...drawPointsRef.current, drawPointsRef.current[0]];
        drawPolygonRef.current = new mapgl.Polygon(map, {
          coordinates: [coords],
          color: 'rgba(255, 102, 96, 0.15)',
          strokeColor: '#FF6660',
          strokeWidth: 3,
        });
      }
    };

    const handleMouseUp = () => {
      if (!isDragging) return;
      isDragging = false;
      map.setOption('disableDragging', false);

      if (drawPointsRef.current.length < 3) return;
      
      const lats = drawPointsRef.current.map((p) => p[1]);
      const lons = drawPointsRef.current.map((p) => p[0]);
      onAreaSelect?.({
        minLat: Math.min(...lats),
        maxLat: Math.max(...lats),
        minLon: Math.min(...lons),
        maxLon: Math.max(...lons),
      });
    };

    map.on('mousedown', handleMouseDown);
    map.on('mousemove', handleMouseMove);
    map.on('mouseup', handleMouseUp);
    
    // Also handle cases where mouse leaves map
    document.addEventListener('mouseup', handleMouseUp);

    return () => {
      map.off('mousedown', handleMouseDown);
      map.off('mousemove', handleMouseMove);
      map.off('mouseup', handleMouseUp);
      document.removeEventListener('mouseup', handleMouseUp);
      map.setOption('disableDragging', false);
      const el = document.getElementById(containerId);
      if (el) el.style.cursor = '';
    };
  }, [isDrawing, onAreaSelect, clearDrawing, containerId]);

  // Sync 3D mode 
  const [is3DMode, setIs3DMode] = React.useState(false);
  const toggle3D = useCallback(() => {
    const map = mapRef.current;
    if (!map) return;
    if (!is3DMode) {
      map.setPitch(45);
      map.setRotation(30);
    } else {
      map.setPitch(0);
      map.setRotation(0);
    }
    setIs3DMode(!is3DMode);
  }, [is3DMode]);

  // Center on selected bazaar
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !selectedBazaarId) return;
    const b = bazaars.find(b => b.id === selectedBazaarId);
    if (b) {
      map.setCenter([b.longitude, b.latitude]);
      map.setZoom(15);
    }
  }, [selectedBazaarId, bazaars]);

  const selectedBazaar = bazaars.find(b => b.id === selectedBazaarId);

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%' }}>
      <MapContainer id={containerId} className={`twogis-map ${className}`} />
      
      {/* 3D Control */}
      <div className="twogis-controls">
        <button 
          className={`twogis-btn-3d ${is3DMode ? 'active' : ''}`} 
          onClick={toggle3D}
          title="Переключить 3D вид"
        >
          3D
        </button>
      </div>

      {/* Directory Selected Bazaar Route Popup */}
      {selectedBazaar && (
        <div className="twogis-route-popup">
          <h4>{selectedBazaar.name}</h4>
          {selectedBazaar.address && <p>{selectedBazaar.address}</p>}
          <a 
            href={`https://2gis.uz/routeSearch/rsType/car/to/${selectedBazaar.longitude},${selectedBazaar.latitude}`}
            target="_blank" 
            rel="noreferrer"
            className="twogis-route-btn"
          >
            Маршрут (2ГИС)
          </a>
        </div>
      )}

      {/* Static Detail Page Route Popup */}
      {!selectedBazaar && staticMarker && (
        <div className="twogis-route-popup">
          <h4>{staticMarker.title || 'Локация'}</h4>
          <a 
            href={`https://2gis.uz/routeSearch/rsType/car/to/${staticMarker.lon},${staticMarker.lat}`}
            target="_blank" 
            rel="noreferrer"
            className="twogis-route-btn"
          >
            Маршрут (2ГИС)
          </a>
        </div>
      )}
    </div>
  );
}
