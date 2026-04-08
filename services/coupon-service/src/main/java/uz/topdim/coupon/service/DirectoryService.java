package uz.topdim.coupon.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.*;
import uz.topdim.coupon.entity.*;
import uz.topdim.coupon.repository.BazaarRepository;
import uz.topdim.coupon.repository.ShopRepository;

import java.util.Collections;
import java.util.List;

/**
 * Сервис справочника базаров и магазинов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DirectoryService {

    private final BazaarRepository bazaarRepository;
    private final ShopRepository shopRepository;
    private final ObjectMapper objectMapper;

    // ═══════════════════════════════════════════
    // Bazaars
    // ═══════════════════════════════════════════

    public Page<BazaarResponse> getBazaars(String search, String type, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("name"));

        Page<Bazaar> bazaars;
        if (search != null && !search.isBlank()) {
            bazaars = bazaarRepository.searchByName(search.trim(), BazaarStatus.ACTIVE, pageable);
        } else if (type != null && !type.isBlank() && !"Все".equals(type)) {
            bazaars = bazaarRepository.findByTypeAndStatus(type, BazaarStatus.ACTIVE, pageable);
        } else {
            bazaars = bazaarRepository.findByStatus(BazaarStatus.ACTIVE, pageable);
        }

        return bazaars.map(this::toBazaarResponse);
    }

    public BazaarResponse getBazaarById(Long id) {
        var bazaar = bazaarRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bazaar not found: " + id));
        return toBazaarResponse(bazaar);
    }

    @Transactional
    public BazaarResponse createBazaar(CreateBazaarRequest request) {
        var bazaar = Bazaar.builder()
                .name(request.getName())
                .nameUz(request.getNameUz())
                .type(request.getType())
                .description(request.getDescription())
                .address(request.getAddress())
                .city(request.getCity())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .coverImageUrl(request.getCoverImageUrl())
                .workingHours(request.getWorkingHours())
                .phone(request.getPhone())
                .status(BazaarStatus.ACTIVE)
                .build();
        return toBazaarResponse(bazaarRepository.save(bazaar));
    }

    @Transactional
    public BazaarResponse updateBazaar(Long id, CreateBazaarRequest request) {
        var bazaar = bazaarRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bazaar not found: " + id));
        bazaar.setName(request.getName());
        bazaar.setNameUz(request.getNameUz());
        bazaar.setType(request.getType());
        bazaar.setDescription(request.getDescription());
        bazaar.setAddress(request.getAddress());
        bazaar.setCity(request.getCity());
        bazaar.setLatitude(request.getLatitude());
        bazaar.setLongitude(request.getLongitude());
        bazaar.setCoverImageUrl(request.getCoverImageUrl());
        bazaar.setWorkingHours(request.getWorkingHours());
        bazaar.setPhone(request.getPhone());
        return toBazaarResponse(bazaarRepository.save(bazaar));
    }

    // ═══════════════════════════════════════════
    // Shops
    // ═══════════════════════════════════════════

    public Page<ShopResponse> getShops(String search, String category, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("name"));

        Page<Shop> shops;
        if (search != null && !search.isBlank()) {
            shops = shopRepository.search(search.trim(), ShopStatus.ACTIVE, pageable);
        } else {
            shops = shopRepository.findByStatus(ShopStatus.ACTIVE, pageable);
        }

        return shops.map(this::toShopResponse);
    }

    public ShopResponse getShopById(Long id) {
        var shop = shopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shop not found: " + id));
        return toShopResponse(shop);
    }

    public List<ShopResponse> getShopsByBazaarId(Long bazaarId, String search) {
        List<Shop> shops;
        if (search != null && !search.isBlank()) {
            shops = shopRepository.searchInBazaar(bazaarId, search.trim(), ShopStatus.ACTIVE);
        } else {
            shops = shopRepository.findByBazaarIdAndStatus(bazaarId, ShopStatus.ACTIVE);
        }
        return shops.stream().map(this::toShopResponse).toList();
    }

    @Transactional
    public ShopResponse createShop(CreateShopRequest request) {
        var builder = Shop.builder()
                .merchantId(request.getMerchantId())
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .subcategory(request.getSubcategory())
                .goodsDescription(request.getGoodsDescription())
                .phone(request.getPhone())
                .workingHours(request.getWorkingHours())
                .locationType(LocationType.valueOf(request.getLocationType()))
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .pavilion(request.getPavilion())
                .sector(request.getSector())
                .rowNumber(request.getRowNumber())
                .shopNumber(request.getShopNumber())
                .floorNumber(request.getFloorNumber());

        // Photos
        if (request.getPhotos() != null && !request.getPhotos().isEmpty()) {
            try {
                builder.photos(objectMapper.writeValueAsString(request.getPhotos()));
            } catch (Exception e) {
                log.warn("Failed to serialize photos", e);
            }
        }

        // Bazaar link
        if (request.getBazaarId() != null) {
            var bazaar = bazaarRepository.findById(request.getBazaarId())
                    .orElseThrow(() -> new RuntimeException("Bazaar not found: " + request.getBazaarId()));
            builder.bazaar(bazaar);
        }

        // Status
        if (request.getStatus() != null) {
            builder.status(ShopStatus.valueOf(request.getStatus()));
        } else {
            builder.status(ShopStatus.ACTIVE);
        }

        return toShopResponse(shopRepository.save(builder.build()));
    }

    @Transactional
    public ShopResponse updateShop(Long id, CreateShopRequest request) {
        var shop = shopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shop not found: " + id));

        shop.setName(request.getName());
        shop.setDescription(request.getDescription());
        shop.setCategory(request.getCategory());
        shop.setSubcategory(request.getSubcategory());
        shop.setGoodsDescription(request.getGoodsDescription());
        shop.setPhone(request.getPhone());
        shop.setWorkingHours(request.getWorkingHours());
        shop.setLocationType(LocationType.valueOf(request.getLocationType()));
        shop.setAddress(request.getAddress());
        shop.setLatitude(request.getLatitude());
        shop.setLongitude(request.getLongitude());
        shop.setPavilion(request.getPavilion());
        shop.setSector(request.getSector());
        shop.setRowNumber(request.getRowNumber());
        shop.setShopNumber(request.getShopNumber());
        shop.setFloorNumber(request.getFloorNumber());

        if (request.getBazaarId() != null) {
            shop.setBazaar(bazaarRepository.findById(request.getBazaarId()).orElse(null));
        } else {
            shop.setBazaar(null);
        }

        if (request.getPhotos() != null) {
            try {
                shop.setPhotos(objectMapper.writeValueAsString(request.getPhotos()));
            } catch (Exception e) {
                log.warn("Failed to serialize photos", e);
            }
        }

        return toShopResponse(shopRepository.save(shop));
    }

    // ═══════════════════════════════════════════
    // Area search
    // ═══════════════════════════════════════════

    public AreaSearchResponse searchInArea(double minLat, double maxLat, double minLon, double maxLon) {
        var bazaars = bazaarRepository.findInBoundingBox(minLat, maxLat, minLon, maxLon)
                .stream().map(this::toBazaarResponse).toList();
        var shops = shopRepository.findInBoundingBox(minLat, maxLat, minLon, maxLon)
                .stream().map(this::toShopResponse).toList();

        return AreaSearchResponse.builder()
                .bazaars(bazaars)
                .shops(shops)
                .build();
    }

    // ═══════════════════════════════════════════
    // Mappers
    // ═══════════════════════════════════════════

    private BazaarResponse toBazaarResponse(Bazaar b) {
        int shopCount = shopRepository.countByBazaarIdAndStatus(b.getId(), ShopStatus.ACTIVE);
        return BazaarResponse.builder()
                .id(b.getId())
                .name(b.getName())
                .nameUz(b.getNameUz())
                .type(b.getType())
                .description(b.getDescription())
                .address(b.getAddress())
                .city(b.getCity())
                .latitude(b.getLatitude())
                .longitude(b.getLongitude())
                .coverImageUrl(b.getCoverImageUrl())
                .workingHours(b.getWorkingHours())
                .phone(b.getPhone())
                .status(b.getStatus().name())
                .shopCount(shopCount)
                .build();
    }

    private ShopResponse toShopResponse(Shop s) {
        ShopResponse.BazaarSummary bazaarSummary = null;
        if (s.getBazaar() != null) {
            bazaarSummary = ShopResponse.BazaarSummary.builder()
                    .id(s.getBazaar().getId())
                    .name(s.getBazaar().getName())
                    .build();
        }

        List<String> photosList = Collections.emptyList();
        if (s.getPhotos() != null && !s.getPhotos().isBlank()) {
            try {
                photosList = objectMapper.readValue(s.getPhotos(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to parse shop photos for shop {}", s.getId(), e);
            }
        }

        return ShopResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .category(s.getCategory())
                .subcategory(s.getSubcategory())
                .goodsDescription(s.getGoodsDescription())
                .phone(s.getPhone())
                .workingHours(s.getWorkingHours())
                .photos(photosList)
                .locationType(s.getLocationType().name())
                .bazaar(bazaarSummary)
                .pavilion(s.getPavilion())
                .sector(s.getSector())
                .rowNumber(s.getRowNumber())
                .shopNumber(s.getShopNumber())
                .floorNumber(s.getFloorNumber())
                .address(s.getAddress())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .status(s.getStatus().name())
                .build();
    }
}
