package uz.topdim.bazaar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.bazaar.dto.ShopResponse;
import uz.topdim.bazaar.dto.UpdateShopRequest;
import uz.topdim.bazaar.entity.Shop;
import uz.topdim.bazaar.exception.ResourceNotFoundException;
import uz.topdim.bazaar.repository.ShopRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис партнёра — управление СВОИМ магазином.
 * Привязка через Shop.userId.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerShopService {

    private final ShopRepository shopRepository;

    /** Все магазины партнёра. */
    @Transactional(readOnly = true)
    public List<ShopResponse> getMyShops(Long userId) {
        return shopRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /** Детали конкретного магазина (с проверкой владения). */
    @Transactional(readOnly = true)
    public ShopResponse getMyShop(Long userId, Long shopId) {
        Shop shop = shopRepository.findByUserIdAndId(userId, shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Магазин не найден или не принадлежит вам"));
        return mapToResponse(shop);
    }

    /** Обновить свой магазин (только редактируемые поля). */
    @Transactional
    public ShopResponse updateMyShop(Long userId, Long shopId, UpdateShopRequest request) {
        Shop shop = shopRepository.findByUserIdAndId(userId, shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Магазин не найден или не принадлежит вам"));

        if (request.getPhone() != null) shop.setPhone(request.getPhone());
        if (request.getWorkingHours() != null) shop.setWorkingHours(request.getWorkingHours());
        if (request.getGoodsDescription() != null) shop.setGoodsDescription(request.getGoodsDescription());
        if (request.getPhotoUrl() != null) shop.setPhotoUrl(request.getPhotoUrl());

        shop = shopRepository.save(shop);
        log.info("PARTNER: userId={} обновил магазин {}", userId, shopId);

        return mapToResponse(shop);
    }

    private ShopResponse mapToResponse(Shop shop) {
        return ShopResponse.builder()
                .id(shop.getId())
                .bazaarId(shop.getBazaar() != null ? shop.getBazaar().getId() : null)
                .bazaarName(shop.getBazaar() != null ? shop.getBazaar().getName() : null)
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
                .createdAt(shop.getCreatedAt())
                .build();
    }
}
