package uz.topdim.bazaar.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.bazaar.dto.CreateShopRequest;
import uz.topdim.bazaar.dto.ShopResponse;
import uz.topdim.bazaar.entity.*;
import uz.topdim.bazaar.exception.ResourceNotFoundException;
import uz.topdim.bazaar.repository.BazaarRepository;
import uz.topdim.bazaar.repository.ShopCategoryRepository;
import uz.topdim.bazaar.repository.ShopRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock private ShopRepository shopRepository;
    @Mock private BazaarRepository bazaarRepository;
    @Mock private ShopCategoryRepository shopCategoryRepository;

    @InjectMocks
    private ShopService shopService;

    private Bazaar createTestBazaar() {
        return Bazaar.builder().id(1L).name("Чорсу").type(BazaarType.BAZAAR).build();
    }

    private Shop createTestShop() {
        return Shop.builder()
                .id(1L).bazaar(createTestBazaar())
                .name("Магазин тканей").rowNumber("3")
                .shopNumber("45").goodsDescription("Шёлк, хлопок")
                .active(true).hasCoupon(true).linkedCouponOfferId(5L)
                .productTags(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("Магазины базара: все активные")
    void getShopsByBazaar_allActive_returnsList() {
        when(shopRepository.findByBazaarIdAndActiveTrue(1L)).thenReturn(List.of(createTestShop()));

        List<ShopResponse> result = shopService.getShopsByBazaar(1L, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Магазин тканей");
    }

    @Test
    @DisplayName("Магазины базара: только с купонами")
    void getShopsByBazaar_withCouponFilter_returnsFiltered() {
        when(shopRepository.findByBazaarIdAndHasCouponTrueAndActiveTrue(1L)).thenReturn(List.of(createTestShop()));

        List<ShopResponse> result = shopService.getShopsByBazaar(1L, true);

        assertThat(result).hasSize(1);
        verify(shopRepository).findByBazaarIdAndHasCouponTrueAndActiveTrue(1L);
    }

    @Test
    @DisplayName("Получение по ID: найден")
    void getShopById_found_returnsShop() {
        when(shopRepository.findById(1L)).thenReturn(Optional.of(createTestShop()));

        ShopResponse result = shopService.getShopById(1L);

        assertThat(result.getBazaarName()).isEqualTo("Чорсу");
        assertThat(result.isHasCoupon()).isTrue();
    }

    @Test
    @DisplayName("Создание магазина: успешное")
    void createShop_success() {
        Bazaar bazaar = createTestBazaar();
        ShopCategory category = ShopCategory.builder().id(1L).name("Ткани").build();

        CreateShopRequest request = new CreateShopRequest();
        request.setName("Новый магазин");
        request.setBazaarId(1L);
        request.setCategoryId(1L);
        request.setRowNumber("5");
        request.setShopNumber("12");

        Shop saved = Shop.builder()
                .id(10L).bazaar(bazaar).name("Новый магазин")
                .category(category).rowNumber("5").shopNumber("12")
                .active(true).productTags(new ArrayList<>())
                .build();

        when(bazaarRepository.findById(1L)).thenReturn(Optional.of(bazaar));
        when(shopCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(shopRepository.save(any(Shop.class))).thenReturn(saved);

        ShopResponse result = shopService.createShop(request);

        assertThat(result.getName()).isEqualTo("Новый магазин");
        verify(shopRepository).save(any(Shop.class));
    }
}
