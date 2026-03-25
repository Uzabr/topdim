package uz.topdim.bazaar.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.bazaar.dto.BazaarResponse;
import uz.topdim.bazaar.dto.CreateBazaarRequest;
import uz.topdim.bazaar.entity.Bazaar;
import uz.topdim.bazaar.entity.BazaarType;
import uz.topdim.bazaar.exception.ResourceNotFoundException;
import uz.topdim.bazaar.repository.BazaarMapRepository;
import uz.topdim.bazaar.repository.BazaarRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BazaarServiceTest {

    @Mock private BazaarRepository bazaarRepository;
    @Mock private BazaarMapRepository bazaarMapRepository;

    @InjectMocks
    private BazaarService bazaarService;

    private Bazaar createTestBazaar() {
        return Bazaar.builder()
                .id(1L).name("Чорсу").nameUz("Chorsu")
                .type(BazaarType.BAZAAR).address("ул. Навои 10")
                .city("Ташкент").latitude(41.3275).longitude(69.2350)
                .description("Крупнейший базар").active(true)
                .shops(new ArrayList<>()).maps(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("Все базары: без фильтра — возвращает все активные")
    void getAllBazaars_noCity_returnsAll() {
        when(bazaarRepository.findByActiveTrue()).thenReturn(List.of(createTestBazaar()));

        List<BazaarResponse> result = bazaarService.getAllBazaars(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Чорсу");
    }

    @Test
    @DisplayName("Все базары: фильтр по городу")
    void getAllBazaars_withCity_filtersCorrectly() {
        when(bazaarRepository.findByCityAndActiveTrue("Ташкент")).thenReturn(List.of(createTestBazaar()));

        List<BazaarResponse> result = bazaarService.getAllBazaars("Ташкент");

        assertThat(result).hasSize(1);
        verify(bazaarRepository).findByCityAndActiveTrue("Ташкент");
        verify(bazaarRepository, never()).findByActiveTrue();
    }

    @Test
    @DisplayName("Получение по ID: найден")
    void getBazaarById_found_returnsBazaar() {
        when(bazaarRepository.findById(1L)).thenReturn(Optional.of(createTestBazaar()));

        BazaarResponse result = bazaarService.getBazaarById(1L);

        assertThat(result.getCity()).isEqualTo("Ташкент");
        assertThat(result.getLatitude()).isEqualTo(41.3275);
    }

    @Test
    @DisplayName("Получение по ID: не найден → ResourceNotFoundException")
    void getBazaarById_notFound_throwsException() {
        when(bazaarRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bazaarService.getBazaarById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Создание базара: успешное")
    void createBazaar_success() {
        CreateBazaarRequest request = new CreateBazaarRequest();
        request.setName("Новый базар");
        request.setType("MARKET");
        request.setCity("Самарканд");
        request.setLatitude(39.65);
        request.setLongitude(66.96);

        Bazaar saved = Bazaar.builder()
                .id(2L).name("Новый базар").type(BazaarType.MARKET)
                .city("Самарканд").latitude(39.65).longitude(66.96)
                .active(true).shops(new ArrayList<>()).maps(new ArrayList<>())
                .build();

        when(bazaarRepository.save(any(Bazaar.class))).thenReturn(saved);

        BazaarResponse result = bazaarService.createBazaar(request);

        assertThat(result.getName()).isEqualTo("Новый базар");
        assertThat(result.getCity()).isEqualTo("Самарканд");
    }
}
