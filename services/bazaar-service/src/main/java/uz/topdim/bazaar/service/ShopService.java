package uz.topdim.bazaar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.bazaar.dto.CreateShopRequest;
import uz.topdim.bazaar.dto.ShopResponse;
import uz.topdim.bazaar.entity.*;
import uz.topdim.bazaar.exception.ResourceNotFoundException;
import uz.topdim.bazaar.repository.BazaarRepository;
import uz.topdim.bazaar.repository.ShopCategoryRepository;
import uz.topdim.bazaar.repository.ShopRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис управления магазинами.
 * CRUD магазинов, привязка к базару и категории.
 */
@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;
    private final BazaarRepository bazaarRepository;
    private final ShopCategoryRepository shopCategoryRepository;

    /**
     * Получает магазины базара.
     *
     * @param bazaarId ID базара
     * @param hasCoupon фильтр: только с купонами (null = все)
     * @return список магазинов
     */
    @Transactional(readOnly = true)
    public List<ShopResponse> getShopsByBazaar(Long bazaarId, Boolean hasCoupon) {
        List<Shop> shops = Boolean.TRUE.equals(hasCoupon)
                ? shopRepository.findByBazaarIdAndHasCouponTrueAndActiveTrue(bazaarId)
                : shopRepository.findByBazaarIdAndActiveTrue(bazaarId);
        return shops.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Получает магазин по ID.
     *
     * @param id ID магазина
     * @return данные магазина
     * @throws ResourceNotFoundException если не найден
     */
    @Transactional(readOnly = true)
    public ShopResponse getShopById(Long id) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Магазин не найден"));
        return mapToResponse(shop);
    }

    /**
     * Поиск магазинов по названию и тегам.
     *
     * @param query поисковый запрос
     * @return список найденных магазинов
     */
    @Transactional(readOnly = true)
    public List<ShopResponse> searchShops(String query) {
        return shopRepository.search(query).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Получает все категории магазинов.
     *
     * @return список ShopCategory
     */
    @Transactional(readOnly = true)
    public List<ShopCategory> getAllCategories() {
        return shopCategoryRepository.findByActiveTrue();
    }

    /**
     * Создаёт новый магазин в базаре (Admin).
     *
     * @param request name, floor, section, phone, bazaarId, categoryId
     * @return созданный магазин
     */
    @Transactional
    public ShopResponse createShop(CreateShopRequest request) {
        Bazaar bazaar = bazaarRepository.findById(request.getBazaarId())
                .orElseThrow(() -> new ResourceNotFoundException("Базар не найден"));

        ShopCategory category = null;
        if (request.getCategoryId() != null) {
            category = shopCategoryRepository.findById(request.getCategoryId()).orElse(null);
        }

        Shop shop = Shop.builder()
                .bazaar(bazaar)
                .name(request.getName())
                .rowNumber(request.getRowNumber())
                .shopNumber(request.getShopNumber())
                .category(category)
                .goodsDescription(request.getGoodsDescription())
                .workingHours(request.getWorkingHours())
                .phone(request.getPhone())
                .photoUrl(request.getPhotoUrl())
                .floorNumber(request.getFloorNumber())
                .zoneId(request.getZoneId())
                .active(true)
                .build();

        shop = shopRepository.save(shop);

        // Add product tags
        if (request.getProductTags() != null) {
            for (String tag : request.getProductTags()) {
                ShopProductTag productTag = new ShopProductTag();
                productTag.setShop(shop);
                productTag.setTag(tag);
                shop.getProductTags().add(productTag);
            }
            shop = shopRepository.save(shop);
        }

        return mapToResponse(shop);
    }

    /**
     * Обновляет данные магазина (Admin).
     *
     * @param id ID магазина
     * @param request обновлённые данные
     * @return обновлённый магазин
     */
    @Transactional
    public ShopResponse updateShop(Long id, CreateShopRequest request) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Магазин не найден"));

        shop.setName(request.getName());
        if (request.getRowNumber() != null) shop.setRowNumber(request.getRowNumber());
        if (request.getShopNumber() != null) shop.setShopNumber(request.getShopNumber());
        if (request.getGoodsDescription() != null) shop.setGoodsDescription(request.getGoodsDescription());
        if (request.getWorkingHours() != null) shop.setWorkingHours(request.getWorkingHours());
        if (request.getPhone() != null) shop.setPhone(request.getPhone());
        if (request.getPhotoUrl() != null) shop.setPhotoUrl(request.getPhotoUrl());

        return mapToResponse(shopRepository.save(shop));
    }

    private ShopResponse mapToResponse(Shop shop) {
        return ShopResponse.builder()
                .id(shop.getId())
                .bazaarId(shop.getBazaar().getId())
                .bazaarName(shop.getBazaar().getName())
                .name(shop.getName())
                .rowNumber(shop.getRowNumber())
                .shopNumber(shop.getShopNumber())
                .categoryName(shop.getCategory() != null ? shop.getCategory().getName() : null)
                .goodsDescription(shop.getGoodsDescription())
                .workingHours(shop.getWorkingHours())
                .phone(shop.getPhone())
                .photoUrl(shop.getPhotoUrl())
                .hasCoupon(shop.isHasCoupon())
                .linkedCouponOfferId(shop.getLinkedCouponOfferId())
                .floorNumber(shop.getFloorNumber())
                .zoneId(shop.getZoneId())
                .productTags(shop.getProductTags().stream()
                        .map(ShopProductTag::getTag)
                        .collect(Collectors.toList()))
                .createdAt(shop.getCreatedAt())
                .build();
    }
}
