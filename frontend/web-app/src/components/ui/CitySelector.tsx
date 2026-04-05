import { useEffect } from 'react';
import { MapPin } from 'lucide-react';
import { useCityStore, CITIES } from '../../store/cityStore';
import { useTranslation } from 'react-i18next';
import Select from './Select';

export default function CitySelector() {
  const { selectedCity, setCity, detectCity } = useCityStore();
  const { i18n, t } = useTranslation();

  useEffect(() => {
    detectCity();
  }, [detectCity]);

  const getCityName = (city: typeof CITIES[0]) =>
    i18n.language === 'uz' ? city.nameUz : city.name;

  const options = CITIES.map((city) => ({
    id: city.id,
    label: getCityName(city),
    icon: <MapPin size={14} />,
  }));

  return (
    <Select
      options={options}
      value={selectedCity.id}
      onChange={(val) => {
        const city = CITIES.find((c) => c.id === val);
        if (city) setCity(city);
      }}
      triggerIcon={<MapPin size={15} />}
      headerTitle={t('header.city')}
      minWidth="140px"
    />
  );
}
