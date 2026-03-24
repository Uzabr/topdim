package uz.topdim.bazaar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.bazaar.dto.BazaarResponse;
import uz.topdim.bazaar.dto.CreateBazaarRequest;
import uz.topdim.bazaar.entity.*;
import uz.topdim.bazaar.exception.ResourceNotFoundException;
import uz.topdim.bazaar.repository.BazaarMapRepository;
import uz.topdim.bazaar.repository.BazaarRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис управления базарами.
 * CRUD операции, получение с координатами для карты Leaflet.
 */
@Service
@RequiredArgsConstructor
public class BazaarService {

    private final BazaarRepository bazaarRepository;
    private final BazaarMapRepository bazaarMapRepository;

    /**
     * Получает все базары, опционально с фильтром по городу.
     *
     * @param city фильтр по городу (null = все)
     * @return список базаров с координатами
     */
    @Transactional(readOnly = true)
    public List<BazaarResponse> getAllBazaars(String city) {
        List<Bazaar> bazaars = city != null
                ? bazaarRepository.findByCityAndActiveTrue(city)
                : bazaarRepository.findByActiveTrue();
        return bazaars.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Получает базар по ID.
     *
     * @param id ID базара
     * @return данные базара
     * @throws ResourceNotFoundException если не найден
     */
    @Transactional(readOnly = true)
    public BazaarResponse getBazaarById(Long id) {
        Bazaar bazaar = bazaarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Базар не найден"));
        return mapToResponse(bazaar);
    }

    /**
     * Получает внутренние карты базара.
     *
     * @param bazaarId ID базара
     * @return список карт (SVG/изображения)
     */
    @Transactional(readOnly = true)
    public List<BazaarMap> getBazaarMaps(Long bazaarId) {
        return bazaarMapRepository.findByBazaarId(bazaarId);
    }

    /**
     * Создаёт новый базар (Admin).
     *
     * @param request name, address, lat, lng, description
     * @return созданный базар
     */
    @Transactional
    public BazaarResponse createBazaar(CreateBazaarRequest request) {
        BazaarType type;
        try {
            type = BazaarType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            type = BazaarType.BAZAAR;
        }

        Bazaar bazaar = Bazaar.builder()
                .name(request.getName())
                .nameUz(request.getNameUz())
                .type(type)
                .address(request.getAddress())
                .city(request.getCity())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .description(request.getDescription())
                .coverImageUrl(request.getCoverImageUrl())
                .workingHours(request.getWorkingHours())
                .phone(request.getPhone())
                .active(true)
                .build();
        return mapToResponse(bazaarRepository.save(bazaar));
    }

    /**
     * Обновляет данные базара (Admin).
     *
     * @param id ID базара
     * @param request обновлённые данные
     * @return обновлённый базар
     */
    @Transactional
    public BazaarResponse updateBazaar(Long id, CreateBazaarRequest request) {
        Bazaar bazaar = bazaarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Базар не найден"));

        bazaar.setName(request.getName());
        if (request.getNameUz() != null) bazaar.setNameUz(request.getNameUz());
        if (request.getAddress() != null) bazaar.setAddress(request.getAddress());
        if (request.getCity() != null) bazaar.setCity(request.getCity());
        if (request.getLatitude() != null) bazaar.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) bazaar.setLongitude(request.getLongitude());
        if (request.getDescription() != null) bazaar.setDescription(request.getDescription());
        if (request.getCoverImageUrl() != null) bazaar.setCoverImageUrl(request.getCoverImageUrl());
        if (request.getWorkingHours() != null) bazaar.setWorkingHours(request.getWorkingHours());
        if (request.getPhone() != null) bazaar.setPhone(request.getPhone());

        return mapToResponse(bazaarRepository.save(bazaar));
    }

    @Transactional
    public BazaarMap uploadMap(Long bazaarId, BazaarMap map) {
        Bazaar bazaar = bazaarRepository.findById(bazaarId)
                .orElseThrow(() -> new ResourceNotFoundException("Базар не найден"));
        map.setBazaar(bazaar);
        return bazaarMapRepository.save(map);
    }

    private BazaarResponse mapToResponse(Bazaar bazaar) {
        return BazaarResponse.builder()
                .id(bazaar.getId())
                .name(bazaar.getName())
                .nameUz(bazaar.getNameUz())
                .type(bazaar.getType().name())
                .address(bazaar.getAddress())
                .city(bazaar.getCity())
                .latitude(bazaar.getLatitude())
                .longitude(bazaar.getLongitude())
                .description(bazaar.getDescription())
                .coverImageUrl(bazaar.getCoverImageUrl())
                .workingHours(bazaar.getWorkingHours())
                .phone(bazaar.getPhone())
                .shopCount(bazaar.getShops() != null ? bazaar.getShops().size() : 0)
                .mapCount(bazaar.getMaps() != null ? bazaar.getMaps().size() : 0)
                .createdAt(bazaar.getCreatedAt())
                .build();
    }
}
