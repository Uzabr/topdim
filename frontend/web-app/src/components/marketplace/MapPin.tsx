import { Marker, Popup } from 'react-leaflet';
import { divIcon } from 'leaflet';
import type { BazaarSpot } from '../../data/topdim';
import './MapPin.css';

interface MapPinProps {
  spot: BazaarSpot;
  active: boolean;
  onSelect: (id: number) => void;
}

export default function MapPin({ spot, active, onSelect }: MapPinProps) {
  const icon = divIcon({
    className: '',
    html: `<div class="topdim-map-pin ${active ? 'topdim-map-pin--active' : ''}"><span>${spot.discountLabel}</span></div>`,
    iconSize: [74, 36],
    iconAnchor: [37, 18],
  });

  return (
    <Marker
      position={[spot.latitude ?? 41.3111, spot.longitude ?? 69.2797]}
      icon={icon}
      eventHandlers={{
        click: () => onSelect(spot.id),
      }}
    >
      <Popup>
        <strong>{spot.name}</strong>
        <br />
        {spot.spotlight}
      </Popup>
    </Marker>
  );
}
