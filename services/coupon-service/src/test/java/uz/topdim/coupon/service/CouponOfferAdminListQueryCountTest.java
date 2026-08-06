package uz.topdim.coupon.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import uz.topdim.coupon.dto.AdminCouponFilter;
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.entity.Category;
import uz.topdim.coupon.entity.CouponImage;
import uz.topdim.coupon.entity.CouponOffer;
import uz.topdim.coupon.entity.CouponOption;
import uz.topdim.coupon.entity.CouponOptionStatus;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.entity.Review;
import uz.topdim.coupon.entity.ReviewStatus;
import uz.topdim.coupon.repository.AbstractIntegrationTest;
import uz.topdim.coupon.repository.CategoryRepository;
import uz.topdim.coupon.repository.CouponImageRepository;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.CouponOptionRepository;
import uz.topdim.coupon.repository.MerchantLocationRepository;
import uz.topdim.coupon.repository.MerchantRepository;
import uz.topdim.coupon.repository.ReviewRepository;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CouponOfferService.class)
class CouponOfferAdminListQueryCountTest extends AbstractIntegrationTest {

    @Autowired private CouponOfferService couponOfferService;
    @Autowired private CouponOfferRepository couponOfferRepository;
    @Autowired private CouponOptionRepository couponOptionRepository;
    @Autowired private CouponImageRepository couponImageRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantLocationRepository merchantLocationRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @MockBean private TelegramPreviewService telegramPreviewService;
    @MockBean private CouponCoverFallbackService couponCoverFallbackService;

    @Test
    void getAllForAdmin_pageSizeOneHundred_keepsQueriesBoundedAndMapsCompleteContract() {
        Category category = categoryRepository.save(Category.builder()
                .name("Restaurants")
                .slug("restaurants")
                .build());

        for (int index = 0; index < 100; index++) {
            Merchant merchant = merchantRepository.save(Merchant.builder()
                    .name("Merchant " + index)
                    .active(true)
                    .build());
            merchantLocationRepository.save(MerchantLocation.builder()
                    .merchant(merchant)
                    .title("Primary " + index)
                    .address("Address " + index)
                    .primary(true)
                    .active(true)
                    .build());
            CouponOffer offer = couponOfferRepository.save(CouponOffer.builder()
                    .title("Lead " + index)
                    .offerDescription("Description " + index)
                    .merchant(merchant)
                    .category(category)
                    .fromPrice(BigDecimal.valueOf(10_000 + index))
                    .status(CouponStatus.LEAD)
                    .build());
            couponOptionRepository.save(CouponOption.builder()
                    .couponOffer(offer)
                    .title("Option " + index)
                    .regularPrice(BigDecimal.valueOf(20_000))
                    .couponPrice(BigDecimal.valueOf(10_000))
                    .quantityLimit(10)
                    .status(CouponOptionStatus.ACTIVE)
                    .build());
            couponImageRepository.save(CouponImage.builder()
                    .couponOffer(offer)
                    .imageUrl("https://img.sizbiz.uz/" + index)
                    .sortOrder(0)
                    .build());
            reviewRepository.save(Review.builder()
                    .userId(1_000L + index)
                    .couponOffer(offer)
                    .rating(5)
                    .comment("Approved review")
                    .status(ReviewStatus.APPROVED)
                    .build());
        }
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var result = couponOfferService.getAllForAdmin(
                new AdminCouponFilter(Set.of(CouponStatus.LEAD), null, null, null),
                0,
                100);

        long queryCount = statistics.getPrepareStatementCount();
        assertThat(result.getContent()).hasSize(100);
        assertThat(result.getContent())
                .allSatisfy(response -> assertCompleteWorkspaceRow(response));
        assertThat(queryCount)
                .as("admin list must use a constant-size batch read, actual statements: %s", queryCount)
                .isEqualTo(6);
    }

    private void assertCompleteWorkspaceRow(CouponOfferResponse response) {
        assertThat(response.getMerchant()).isNotNull();
        assertThat(response.getMerchant().getPrimaryLocation()).isNotNull();
        assertThat(response.getCategory()).isNotNull();
        assertThat(response.getOptions()).hasSize(1);
        assertThat(response.getImages()).hasSize(1);
        assertThat(response.getAverageRating()).isEqualTo(5.0);
        assertThat(response.getReviewCount()).isEqualTo(1);
    }
}
