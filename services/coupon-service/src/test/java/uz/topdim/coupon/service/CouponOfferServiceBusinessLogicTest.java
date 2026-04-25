package uz.topdim.coupon.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.coupon.dto.BotLeadRequest;
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.entity.Category;
import uz.topdim.coupon.entity.CouponOffer;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.repository.CategoryRepository;
import uz.topdim.coupon.repository.CouponImageRepository;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.CouponOptionRepository;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantRepository;
import uz.topdim.coupon.repository.ReviewRepository;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponOfferServiceBusinessLogicTest {

    @Mock private CouponOfferRepository couponOfferRepository;
    @Mock private CouponOptionRepository couponOptionRepository;
    @Mock private CouponImageRepository couponImageRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private MerchantLocationRepository merchantLocationRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private EntityManager entityManager;
    @Mock private TelegramPreviewService telegramPreviewService;

    @InjectMocks
    private CouponOfferService couponOfferService;

    private Merchant createMerchant() {
        return Merchant.builder()
                .id(1L)
                .name("Test Merchant")
                .telegramChatId("chat-1")
                .active(true)
                .build();
    }

    private CouponOffer createOffer(CouponStatus status) {
        return CouponOffer.builder()
                .id(10L)
                .title("Test Coupon")
                .merchant(createMerchant())
                .category(Category.builder().id(5L).name("Food").slug("food").build())
                .status(status)
                .revisionComment("Old revision comment")
                .options(new ArrayList<>())
                .images(new ArrayList<>())
                .build();
    }

    private BotLeadRequest createLeadRequest() {
        BotLeadRequest request = new BotLeadRequest();
        request.setCompanyName("Lead Company");
        request.setPhone("+998901234567");
        request.setFirstName("Ali");
        request.setLastName("Valiyev");
        request.setPromoDescription("Promo from Telegram");
        request.setSourceLink("https://instagram.com/lead");
        request.setTelegramChatId("chat-1");
        request.setVoiceFileId("voice-123");
        return request;
    }

    @Test
    @DisplayName("sendToApproval: из REVISION_REQUESTED очищает комментарий и отправляет превью")
    void sendToApproval_fromRevisionRequested_clearsCommentAndSendsPreview() {
        CouponOffer offer = createOffer(CouponStatus.REVISION_REQUESTED);
        when(couponOfferRepository.findById(10L)).thenReturn(Optional.of(offer));
        when(couponOfferRepository.save(any(CouponOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CouponOfferResponse result = couponOfferService.sendToApproval(10L);

        assertThat(result.getStatus()).isEqualTo("WAITING_FOR_MERCHANT");
        assertThat(result.getRevisionComment()).isNull();
        verify(telegramPreviewService).sendPreview(offer);
    }

    @Test
    @DisplayName("updateStatus: LEAD -> DRAFT разрешён")
    void updateStatus_leadToDraft_allowed() {
        CouponOffer offer = createOffer(CouponStatus.LEAD);
        when(couponOfferRepository.findById(10L)).thenReturn(Optional.of(offer));
        when(couponOfferRepository.save(any(CouponOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CouponOfferResponse result = couponOfferService.updateStatus(10L, CouponStatus.DRAFT);

        assertThat(result.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("updateStatus: WAITING_FOR_MERCHANT -> ACTIVE разрешён")
    void updateStatus_waitingForMerchantToActive_allowed() {
        CouponOffer offer = createOffer(CouponStatus.WAITING_FOR_MERCHANT);
        uz.topdim.coupon.entity.MerchantLocation location = uz.topdim.coupon.entity.MerchantLocation.builder()
                .id(5L)
                .merchant(offer.getMerchant())
                .address("Ташкент, ул. Шота Руставели, 1")
                .primary(true)
                .active(true)
                .build();
        when(couponOfferRepository.findById(10L)).thenReturn(Optional.of(offer));
        when(merchantLocationRepository.findByMerchantIdAndPrimaryTrue(1L)).thenReturn(Optional.of(location));
        when(couponOfferRepository.save(any(CouponOffer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CouponOfferResponse result = couponOfferService.updateStatus(10L, CouponStatus.ACTIVE);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("updateStatus: WAITING_FOR_MERCHANT -> ACTIVE без active primary location запрещён")
    void updateStatus_waitingForMerchantToActive_withoutPrimaryLocation_throws() {
        CouponOffer offer = createOffer(CouponStatus.WAITING_FOR_MERCHANT);
        when(couponOfferRepository.findById(10L)).thenReturn(Optional.of(offer));
        when(merchantLocationRepository.findByMerchantIdAndPrimaryTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponOfferService.updateStatus(10L, CouponStatus.ACTIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("primary location");
    }

    @Test
    @DisplayName("updateStatus: DRAFT -> ACTIVE запрещён")
    void updateStatus_draftToActive_throws() {
        CouponOffer offer = createOffer(CouponStatus.DRAFT);
        when(couponOfferRepository.findById(10L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> couponOfferService.updateStatus(10L, CouponStatus.ACTIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("запрещён");
    }

    @Test
    @DisplayName("updateStatus: ACTIVE -> ARCHIVED через generic status endpoint запрещён")
    void updateStatus_activeToArchived_throws() {
        CouponOffer offer = createOffer(CouponStatus.ACTIVE);
        when(couponOfferRepository.findById(10L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> couponOfferService.updateStatus(10L, CouponStatus.ARCHIVED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("запрещён");
    }

    @Test
    @DisplayName("createLeadFromBot: переиспользует мерчанта по telegramChatId")
    void createLeadFromBot_reusesMerchantByTelegramChatId() {
        Merchant merchant = createMerchant();
        BotLeadRequest request = createLeadRequest();

        when(merchantRepository.findByTelegramChatId("chat-1")).thenReturn(Optional.of(merchant));

        couponOfferService.createLeadFromBot(request);

        ArgumentCaptor<CouponOffer> leadCaptor = ArgumentCaptor.forClass(CouponOffer.class);
        verify(couponOfferRepository).save(leadCaptor.capture());
        verify(merchantRepository, never()).save(any(Merchant.class));

        CouponOffer lead = leadCaptor.getValue();
        assertThat(lead.getMerchant()).isEqualTo(merchant);
        assertThat(lead.getStatus()).isEqualTo(CouponStatus.LEAD);
        assertThat(lead.getTitle()).isEqualTo("Лид от: Test Merchant");
        assertThat(lead.getOfferDescription()).contains("Promo from Telegram");
        assertThat(lead.getOfferDescription()).contains("voice-123");
    }

    @Test
    @DisplayName("createLeadFromBot: если chatId не найден, ищет по телефону в merchant_locations")
    void createLeadFromBot_fallsBackToPhoneViaLocations() {
        Merchant merchant = createMerchant();
        BotLeadRequest request = createLeadRequest();
        request.setTelegramChatId("unknown-chat");

        uz.topdim.coupon.entity.MerchantLocation loc = uz.topdim.coupon.entity.MerchantLocation.builder()
                .id(1L).merchant(merchant).phone("+998901234567").primary(true).active(true).build();

        when(merchantRepository.findByTelegramChatId("unknown-chat")).thenReturn(Optional.empty());
        when(merchantLocationRepository.findFirstByPhoneAndActiveTrue("+998901234567")).thenReturn(Optional.of(loc));

        couponOfferService.createLeadFromBot(request);

        ArgumentCaptor<CouponOffer> leadCaptor = ArgumentCaptor.forClass(CouponOffer.class);
        verify(couponOfferRepository).save(leadCaptor.capture());
        verify(merchantRepository, never()).save(any(Merchant.class));

        assertThat(leadCaptor.getValue().getMerchant()).isEqualTo(merchant);
    }

    @Test
    @DisplayName("createLeadFromBot: если locations не нашли по телефону, переходит к поиску по имени")
    void createLeadFromBot_phoneNotInLocations_proceedsToNameLookup() {
        Merchant merchant = createMerchant();
        BotLeadRequest request = createLeadRequest();
        request.setTelegramChatId("unknown-chat");

        when(merchantRepository.findByTelegramChatId("unknown-chat")).thenReturn(Optional.empty());
        when(merchantLocationRepository.findFirstByPhoneAndActiveTrue("+998901234567")).thenReturn(Optional.empty());
        // Name lookup finds unique match
        when(merchantRepository.countByNameIgnoreCase("Lead Company")).thenReturn(1L);
        when(merchantRepository.findFirstByNameIgnoreCase("Lead Company")).thenReturn(Optional.of(merchant));

        couponOfferService.createLeadFromBot(request);

        verify(merchantRepository, never()).save(any(Merchant.class));
        verify(couponOfferRepository).save(any(CouponOffer.class));
    }

    @Test
    @DisplayName("createLeadFromBot: уникальное совпадение по имени — auto-link")
    void createLeadFromBot_uniqueNameMatch() {
        Merchant merchant = createMerchant();
        BotLeadRequest request = createLeadRequest();
        request.setTelegramChatId(null);
        request.setPhone(null);

        when(merchantRepository.countByNameIgnoreCase("Lead Company")).thenReturn(1L);
        when(merchantRepository.findFirstByNameIgnoreCase("Lead Company")).thenReturn(Optional.of(merchant));

        couponOfferService.createLeadFromBot(request);

        verify(merchantRepository, never()).save(any(Merchant.class));
        ArgumentCaptor<CouponOffer> leadCaptor = ArgumentCaptor.forClass(CouponOffer.class);
        verify(couponOfferRepository).save(leadCaptor.capture());
        assertThat(leadCaptor.getValue().getMerchant()).isEqualTo(merchant);
    }

    @Test
    @DisplayName("createLeadFromBot: неуникальное совпадение по имени — создаёт нового мерчанта")
    void createLeadFromBot_ambiguousNameMatch_createsNewMerchant() {
        BotLeadRequest request = createLeadRequest();
        request.setTelegramChatId(null);
        request.setPhone(null);

        when(merchantRepository.countByNameIgnoreCase("Lead Company")).thenReturn(3L);
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> {
            Merchant m = invocation.getArgument(0);
            m.setId(99L);
            return m;
        });

        couponOfferService.createLeadFromBot(request);

        verify(merchantRepository).save(any(Merchant.class));
        verify(couponOfferRepository).save(any(CouponOffer.class));
    }

    @Test
    @DisplayName("createLeadFromBot: без совпадения создаёт нового мерчанта с primary location")
    void createLeadFromBot_createsInactiveMerchantWithPrimaryLocation() {
        BotLeadRequest request = createLeadRequest();
        request.setTelegramChatId("new-chat");
        request.setPhone("+998909999999");

        when(merchantRepository.findByTelegramChatId("new-chat")).thenReturn(Optional.empty());
        when(merchantLocationRepository.findFirstByPhoneAndActiveTrue("+998909999999")).thenReturn(Optional.empty());
        when(merchantRepository.countByNameIgnoreCase("Lead Company")).thenReturn(0L);
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> {
            Merchant merchant = invocation.getArgument(0);
            merchant.setId(77L);
            return merchant;
        });

        couponOfferService.createLeadFromBot(request);

        ArgumentCaptor<Merchant> merchantCaptor = ArgumentCaptor.forClass(Merchant.class);
        ArgumentCaptor<CouponOffer> leadCaptor = ArgumentCaptor.forClass(CouponOffer.class);
        ArgumentCaptor<uz.topdim.coupon.entity.MerchantLocation> locCaptor =
                ArgumentCaptor.forClass(uz.topdim.coupon.entity.MerchantLocation.class);
        verify(merchantRepository).save(merchantCaptor.capture());
        verify(merchantLocationRepository).save(locCaptor.capture());
        verify(couponOfferRepository).save(leadCaptor.capture());

        Merchant createdMerchant = merchantCaptor.getValue();
        uz.topdim.coupon.entity.MerchantLocation createdLoc = locCaptor.getValue();
        CouponOffer createdLead = leadCaptor.getValue();

        assertThat(createdMerchant.isActive()).isFalse();
        assertThat(createdMerchant.getTelegramChatId()).isEqualTo("new-chat");
        assertThat(createdLoc.isPrimary()).isTrue();
        assertThat(createdLoc.getPhone()).isEqualTo("+998909999999");
        assertThat(createdLead.getMerchant().getId()).isEqualTo(77L);
        assertThat(createdLead.getStatus()).isEqualTo(CouponStatus.LEAD);
    }
}
