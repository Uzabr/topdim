package uz.topdim.coupon.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import uz.topdim.coupon.entity.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CouponQuestionRepositoryTest extends AbstractIntegrationTest {

    @Autowired private CouponQuestionRepository questionRepository;
    @Autowired private CouponOfferRepository couponOfferRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private CategoryRepository categoryRepository;

    private CouponOffer coupon;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(Category.builder()
                .name("Food")
                .slug("food")
                .active(true)
                .build());

        Merchant merchant = merchantRepository.save(Merchant.builder()
                .name("Pizza Pub")
                .description("Good pizza")
                .active(true)
                .userId(1L)
                .build());

        coupon = couponOfferRepository.save(CouponOffer.builder()
                .title("Pizza Discount")
                .fromPrice(BigDecimal.valueOf(100))
                .status(CouponStatus.ACTIVE)
                .merchant(merchant)
                .category(category)
                .buyUntil(LocalDateTime.now().plusDays(10))
                .useUntil(LocalDateTime.now().plusDays(20))
                .build());
    }

    @Test
    @DisplayName("save: вопрос сохраняется со статусом PENDING")
    void save_pendingQuestion_persists() {
        CouponQuestion saved = questionRepository.save(CouponQuestion.builder()
                .couponOffer(coupon)
                .userId(42L)
                .userName("Иван")
                .question("Можно ли использовать в выходные?")
                .status(QuestionStatus.PENDING)
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(QuestionStatus.PENDING);
        assertThat(saved.getCouponOffer().getId()).isEqualTo(coupon.getId());
    }

    @Test
    @DisplayName("visible questions: возвращает только PUBLISHED с непустым answer по купону")
    void findVisible_returnsOnlyPublishedWithAnswerForCoupon() {
        CouponQuestion published = CouponQuestion.builder()
                .couponOffer(coupon)
                .userId(10L)
                .userName("Анна")
                .question("Есть парковка?")
                .answer("Да, парковка есть.")
                .status(QuestionStatus.PUBLISHED)
                .build();
        CouponQuestion pending = CouponQuestion.builder()
                .couponOffer(coupon)
                .userId(11L)
                .question("Скрытый вопрос")
                .status(QuestionStatus.PENDING)
                .build();
        CouponQuestion blankAnswer = CouponQuestion.builder()
                .couponOffer(coupon)
                .userId(12L)
                .question("Пустой ответ")
                .answer("   ")
                .status(QuestionStatus.PUBLISHED)
                .build();
        questionRepository.save(published);
        questionRepository.save(pending);
        questionRepository.save(blankAnswer);

        var result = questionRepository.findVisibleByCouponOfferIdAndStatus(
                coupon.getId(), QuestionStatus.PUBLISHED, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(CouponQuestion::getQuestion)
                .containsExactly("Есть парковка?");
    }

    @Test
    @DisplayName("moderation queue: возвращает PENDING вопросы")
    void findByStatus_pending_returnsQueue() {
        questionRepository.save(CouponQuestion.builder()
                .couponOffer(coupon)
                .userId(10L)
                .question("Когда можно прийти?")
                .status(QuestionStatus.PENDING)
                .build());
        questionRepository.save(CouponQuestion.builder()
                .couponOffer(coupon)
                .userId(11L)
                .question("Опубликованный вопрос")
                .answer("После 10:00.")
                .status(QuestionStatus.PUBLISHED)
                .build());

        var result = questionRepository.findByStatusOrderByCreatedAtDesc(
                QuestionStatus.PENDING, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(QuestionStatus.PENDING);
    }
}
