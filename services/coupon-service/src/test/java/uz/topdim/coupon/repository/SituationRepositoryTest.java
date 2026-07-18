package uz.topdim.coupon.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import uz.topdim.coupon.entity.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SituationRepositoryTest extends AbstractIntegrationTest {

    @Autowired private SituationRepository situationRepository;
    @Autowired private SituationCouponRepository situationCouponRepository;
    @Autowired private CouponOfferRepository couponOfferRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Merchant merchant;
    private Category category;

    @BeforeEach
    void setUp() {
        category = categoryRepository.save(Category.builder()
                .name("Family")
                .slug("family")
                .active(true)
                .build());
        merchant = merchantRepository.save(Merchant.builder()
                .name("Family Park")
                .active(true)
                .userId(7L)
                .build());
    }

    @Test
    void countActiveCouponsBySituation_countsOnlyActiveAndNotExpiredCoupons() {
        Situation kids = situationRepository.save(situation("kids", true, 0));
        Situation beauty = situationRepository.save(situation("beauty", true, 1));
        Situation hidden = situationRepository.save(situation("hidden", false, 2));
        LocalDateTime now = LocalDateTime.now();

        CouponOffer activeFuture = coupon("Active future", CouponStatus.ACTIVE, now.plusDays(3));
        CouponOffer activeNoDeadline = coupon("Active no deadline", CouponStatus.ACTIVE, null);
        CouponOffer expired = coupon("Expired", CouponStatus.ACTIVE, now.minusDays(1));
        CouponOffer draft = coupon("Draft", CouponStatus.DRAFT, now.plusDays(3));
        CouponOffer beautyActive = coupon("Beauty active", CouponStatus.ACTIVE, now.plusDays(3));
        CouponOffer hiddenActive = coupon("Hidden active", CouponStatus.ACTIVE, now.plusDays(3));

        link(kids, activeFuture, 0);
        link(kids, activeNoDeadline, 1);
        link(kids, expired, 2);
        link(kids, draft, 3);
        link(beauty, beautyActive, 0);
        link(hidden, hiddenActive, 0);

        Map<Long, Long> counts = situationCouponRepository
                .countActiveCouponsBySituation(CouponStatus.ACTIVE, now)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        assertThat(counts).containsEntry(kids.getId(), 2L);
        assertThat(counts).containsEntry(beauty.getId(), 1L);
        assertThat(counts).doesNotContainKey(hidden.getId());
    }

    @Test
    void findCouponIdsBySituationId_returnsCuratorOrder() {
        Situation kids = situationRepository.save(situation("kids", true, 0));
        CouponOffer first = coupon("First", CouponStatus.ACTIVE, null);
        CouponOffer second = coupon("Second", CouponStatus.ACTIVE, null);
        CouponOffer third = coupon("Third", CouponStatus.ACTIVE, null);

        link(kids, third, 2);
        link(kids, first, 0);
        link(kids, second, 1);

        List<Long> ids = situationCouponRepository.findCouponIdsBySituationId(kids.getId());

        assertThat(ids).containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void findPublicBySituationSlug_returnsOnlyActiveCouponsFromActiveSituation() {
        Situation activeSituation = situationRepository.save(situation("kids", true, 0));
        Situation inactiveSituation = situationRepository.save(situation("hidden", false, 1));
        LocalDateTime now = LocalDateTime.now();
        CouponOffer active = coupon("Visible", CouponStatus.ACTIVE, now.plusDays(1));
        CouponOffer expired = coupon("Expired", CouponStatus.ACTIVE, now.minusDays(1));
        CouponOffer inactiveSituationCoupon = coupon("Hidden", CouponStatus.ACTIVE, now.plusDays(1));

        link(activeSituation, active, 0);
        link(activeSituation, expired, 1);
        link(inactiveSituation, inactiveSituationCoupon, 0);

        Page<CouponOffer> kidsPage = couponOfferRepository.findPublicBySituationSlug(
                CouponStatus.ACTIVE, "kids", now, PageRequest.of(0, 10));
        Page<CouponOffer> hiddenPage = couponOfferRepository.findPublicBySituationSlug(
                CouponStatus.ACTIVE, "hidden", now, PageRequest.of(0, 10));

        assertThat(kidsPage.getContent()).extracting(CouponOffer::getTitle)
                .containsExactly("Visible");
        assertThat(hiddenPage.getContent()).isEmpty();
    }

    @Test
    void deleteBySituationId_removesJoinRows() {
        Situation kids = situationRepository.save(situation("kids", true, 0));
        CouponOffer coupon = coupon("Visible", CouponStatus.ACTIVE, null);
        link(kids, coupon, 0);

        situationCouponRepository.deleteBySituationId(kids.getId());
        situationCouponRepository.flush();

        assertThat(situationCouponRepository.findBySituationId(kids.getId())).isEmpty();
    }

    private Situation situation(String slug, boolean active, int sortOrder) {
        return Situation.builder()
                .slug(slug)
                .title(slug)
                .active(active)
                .sortOrder(sortOrder)
                .featured(sortOrder == 0)
                .build();
    }

    private CouponOffer coupon(String title, CouponStatus status, LocalDateTime buyUntil) {
        return couponOfferRepository.save(CouponOffer.builder()
                .title(title)
                .merchant(merchant)
                .category(category)
                .fromPrice(BigDecimal.valueOf(1000))
                .status(status)
                .buyUntil(buyUntil)
                .build());
    }

    private void link(Situation situation, CouponOffer coupon, int sortOrder) {
        situationCouponRepository.save(SituationCoupon.builder()
                .situation(situation)
                .coupon(coupon)
                .sortOrder(sortOrder)
                .build());
    }
}
