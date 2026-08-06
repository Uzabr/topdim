package uz.topdim.coupon.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import uz.topdim.coupon.dto.AdminCouponFilter;
import uz.topdim.coupon.entity.CouponOffer;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.entity.Merchant;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CouponOfferAdminFilterTest extends AbstractIntegrationTest {

    @Autowired private CouponOfferRepository couponOfferRepository;
    @Autowired private MerchantRepository merchantRepository;

    private Merchant pizzaLab;
    private Merchant spaHouse;

    @BeforeEach
    void setUp() {
        pizzaLab = merchantRepository.save(Merchant.builder()
                .name("PizzaLab")
                .active(true)
                .userId(10L)
                .build());
        spaHouse = merchantRepository.save(Merchant.builder()
                .name("Spa House")
                .active(true)
                .userId(20L)
                .build());
    }

    @Test
    @DisplayName("admin filter: комбинирует статусы, поиск, партнёра и ответственного")
    void findAll_combinedAdminFilters_returnsOnlyExactMatches() {
        couponOfferRepository.save(coupon(
                "Pizza family coupon", pizzaLab, CouponStatus.ACTIVE, 77L, "Moderator 77"));
        couponOfferRepository.save(coupon(
                "Pizza paused by another", pizzaLab, CouponStatus.PAUSED, 88L, "Moderator 88"));
        couponOfferRepository.save(coupon(
                "Spa family coupon", spaHouse, CouponStatus.ACTIVE, 77L, "Moderator 77"));
        couponOfferRepository.save(coupon(
                "Pizza draft coupon", pizzaLab, CouponStatus.DRAFT, 77L, "Moderator 77"));

        Page<CouponOffer> result = couponOfferRepository.findAll(
                CouponOfferSpecifications.forAdmin(new AdminCouponFilter(
                        Set.of(CouponStatus.ACTIVE, CouponStatus.PAUSED),
                        "pizza",
                        pizzaLab.getId(),
                        77L)),
                PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(CouponOffer::getTitle)
                .containsExactly("Pizza family coupon");
    }

    @Test
    @DisplayName("admin filter: поиск по имени партнёра не зависит от регистра")
    void findAll_merchantNameSearch_isCaseInsensitive() {
        couponOfferRepository.save(coupon(
                "Relax package", spaHouse, CouponStatus.PAUSED, null, null));
        couponOfferRepository.save(coupon(
                "Pizza package", pizzaLab, CouponStatus.PAUSED, null, null));

        Page<CouponOffer> result = couponOfferRepository.findAll(
                CouponOfferSpecifications.forAdmin(new AdminCouponFilter(
                        Set.of(CouponStatus.PAUSED),
                        "sPa HoUsE",
                        null,
                        null)),
                PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(CouponOffer::getTitle)
                .containsExactly("Relax package");
    }

    @Test
    @DisplayName("admin pagination: записи после первых 500 остаются доступны")
    void findAll_pageAfterFirstFiveHundred_returnsServerPage() {
        List<CouponOffer> offers = new ArrayList<>();
        for (int index = 0; index < 521; index++) {
            offers.add(coupon(
                    "bulk-" + index,
                    pizzaLab,
                    CouponStatus.LEAD,
                    77L,
                    "Moderator 77"));
        }
        couponOfferRepository.saveAllAndFlush(offers);

        Page<CouponOffer> result = couponOfferRepository.findAll(
                CouponOfferSpecifications.forAdmin(new AdminCouponFilter(
                        Set.of(CouponStatus.LEAD), null, null, null)),
                PageRequest.of(25, 20, Sort.by("id").ascending()));

        assertThat(result.getTotalElements()).isEqualTo(521);
        assertThat(result.getContent()).hasSize(20);
        assertThat(result.getContent().getFirst().getTitle()).isEqualTo("bulk-500");
        assertThat(result.getContent().getLast().getTitle()).isEqualTo("bulk-519");
    }

    @Test
    @DisplayName("assignee directory: один moderator ID не дублируется из-за старого display name")
    void findDistinctAssignees_sameModeratorWithChangedName_returnsOneOption() {
        couponOfferRepository.save(coupon(
                "Old assignment", pizzaLab, CouponStatus.DRAFT, 77L, "A-old@sizbiz.uz"));
        couponOfferRepository.save(coupon(
                "New assignment", spaHouse, CouponStatus.DRAFT, 77L, "Z-new@sizbiz.uz"));

        var result = couponOfferRepository.findDistinctAssignees();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(77L);
        assertThat(result.getFirst().name()).isEqualTo("Z-new@sizbiz.uz");
    }

    private CouponOffer coupon(
            String title,
            Merchant merchant,
            CouponStatus status,
            Long moderatorId,
            String moderatorName
    ) {
        return CouponOffer.builder()
                .title(title)
                .offerDescription(title + " description")
                .merchant(merchant)
                .fromPrice(BigDecimal.valueOf(10_000))
                .status(status)
                .assignedModeratorId(moderatorId)
                .assignedModeratorName(moderatorName)
                .build();
    }
}
