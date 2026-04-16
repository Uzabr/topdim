package uz.topdim.coupon.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import uz.topdim.coupon.entity.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private CouponOfferRepository couponOfferRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private CouponOffer testCoupon;

    @BeforeEach
    void setUp() {
        Category category = Category.builder()
                .name("Food")
                .slug("food")
                .active(true)
                .build();
        categoryRepository.save(category);

        Merchant merchant = Merchant.builder()
                .name("Pizza Pub")
                .description("Good pizza")
                .active(true)
                .userId(1L)
                .build();
        merchantRepository.save(merchant);

        testCoupon = CouponOffer.builder()
                .title("Pizza Discount")
                .fromPrice(BigDecimal.valueOf(100))
                .status(CouponStatus.ACTIVE)
                .merchant(merchant)
                .category(category)
                .buyUntil(LocalDateTime.now().plusDays(10))
                .useUntil(LocalDateTime.now().plusDays(20))
                .build();
        couponOfferRepository.save(testCoupon);
    }

    @Test
    void shouldCalculateAverageRatingAndCountApprovedReviews() {
        // Arrange
        Review approvedReview1 = Review.builder()
                .couponOffer(testCoupon)
                .userId(1L)
                .userName("John")
                .rating(5)
                .comment("Отлично!")
                .status(ReviewStatus.APPROVED)
                .build();

        Review approvedReview2 = Review.builder()
                .couponOffer(testCoupon)
                .userId(2L)
                .userName("Anna")
                .rating(4)
                .comment("Хорошо")
                .status(ReviewStatus.APPROVED)
                .build();

        Review pendingReview = Review.builder()
                .couponOffer(testCoupon)
                .userId(3L)
                .userName("Mike")
                .rating(1) // Should be ignored
                .comment("Плохо")
                .status(ReviewStatus.PENDING)
                .build();

        reviewRepository.save(approvedReview1);
        reviewRepository.save(approvedReview2);
        reviewRepository.save(pendingReview);

        // Act
        double averageRating = reviewRepository.getAverageRatingByCouponId(testCoupon.getId());
        int count = reviewRepository.countApprovedByCouponId(testCoupon.getId());

        // Assert
        assertThat(averageRating).isEqualTo(4.5);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldReturnZeroExpectedWhenNoApprovedReviews() {
        // Act
        double averageRating = reviewRepository.getAverageRatingByCouponId(testCoupon.getId());
        int count = reviewRepository.countApprovedByCouponId(testCoupon.getId());

        // Assert
        assertThat(averageRating).isEqualTo(0.0);
        assertThat(count).isEqualTo(0);
    }
}
