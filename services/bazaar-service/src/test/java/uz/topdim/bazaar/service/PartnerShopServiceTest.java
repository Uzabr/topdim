package uz.topdim.bazaar.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.bazaar.dto.ShopResponse;
import uz.topdim.bazaar.dto.UpdateShopRequest;
import uz.topdim.bazaar.entity.Shop;
import uz.topdim.bazaar.exception.ResourceNotFoundException;
import uz.topdim.bazaar.repository.ShopRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerShopServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @InjectMocks
    private PartnerShopService partnerShopService;

    private Shop createShop() {
        return Shop.builder()
                .id(100L)
                .userId(10L)
                .name("Test Shop")
                .phone("123456")
                .workingHours("09:00-18:00")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("getMyShops: возвращает список магазинов партнёра")
    void getMyShops_returnsPartnerShops() {
        when(shopRepository.findByUserId(10L)).thenReturn(List.of(createShop()));

        List<ShopResponse> result = partnerShopService.getMyShops(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test Shop");
    }

    @Test
    @DisplayName("getMyShop: возвращает свой магазин")
    void getMyShop_ownShop_returnsIt() {
        when(shopRepository.findByUserIdAndId(10L, 100L)).thenReturn(Optional.of(createShop()));

        ShopResponse result = partnerShopService.getMyShop(10L, 100L);

        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getMyShop: чужой или несуществующий магазин выбрасывает ResourceNotFoundException")
    void getMyShop_otherPartnerOrNotFound_throws() {
        when(shopRepository.findByUserIdAndId(10L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerShopService.getMyShop(10L, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("не найден или не принадлежит");
    }

    @Test
    @DisplayName("updateMyShop: успешное обновление полей магазина")
    void updateMyShop_success() {
        Shop shop = createShop();
        when(shopRepository.findByUserIdAndId(10L, 100L)).thenReturn(Optional.of(shop));
        when(shopRepository.save(any(Shop.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateShopRequest req = new UpdateShopRequest();
        req.setPhone("987654");
        req.setWorkingHours("10:00-20:00");
        req.setGoodsDescription("New goods");

        ShopResponse result = partnerShopService.updateMyShop(10L, 100L, req);

        assertThat(result.getPhone()).isEqualTo("987654");
        assertThat(result.getWorkingHours()).isEqualTo("10:00-20:00");
        assertThat(result.getGoodsDescription()).isEqualTo("New goods");
        // name remains the same since it's not updatable
        assertThat(result.getName()).isEqualTo("Test Shop");
    }

    @Test
    @DisplayName("updateMyShop: чужой магазин выбрасывает ResourceNotFoundException")
    void updateMyShop_otherPartner_throws() {
        when(shopRepository.findByUserIdAndId(10L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerShopService.updateMyShop(10L, 999L, new UpdateShopRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
