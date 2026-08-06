package uz.topdim.coupon.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import uz.topdim.coupon.entity.Category;
import uz.topdim.coupon.entity.CouponOffer;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.entity.Merchant;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryRepositoryTest extends AbstractIntegrationTest {

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CouponOfferRepository couponOfferRepository;
    @Autowired private MerchantRepository merchantRepository;

    @Test
    void adminQueries_findDuplicatesIgnoringCaseAndKeepDeterministicOrder() {
        Category second = categoryRepository.saveAndFlush(category("Beauty", "beauty", 2, true));
        Category first = categoryRepository.saveAndFlush(category("Food", "food", 1, false));
        Category secondTie = categoryRepository.saveAndFlush(category("Health", "health", 2, true));

        assertThat(categoryRepository.existsByNameIgnoreCase("BEAUTY")).isTrue();
        assertThat(categoryRepository.existsBySlugIgnoreCase("BeAuTy")).isTrue();
        assertThat(categoryRepository.existsByNameIgnoreCaseAndIdNot("beauty", second.getId()))
                .isFalse();
        assertThat(categoryRepository.existsBySlugIgnoreCaseAndIdNot("FOOD", second.getId()))
                .isTrue();
        assertThat(categoryRepository.findAllByOrderBySortOrderAscIdAsc())
                .extracting(Category::getId)
                .containsExactly(first.getId(), second.getId(), secondTie.getId());
    }

    @Test
    void categoryReferenceCheck_detectsCouponUsingCategory() {
        Category category = categoryRepository.save(category("Food", "food", 1, true));
        Merchant merchant = merchantRepository.save(Merchant.builder()
                .name("Cafe")
                .userId(77L)
                .active(true)
                .build());
        couponOfferRepository.saveAndFlush(CouponOffer.builder()
                .title("Lunch")
                .merchant(merchant)
                .category(category)
                .fromPrice(BigDecimal.valueOf(100_000))
                .status(CouponStatus.DRAFT)
                .build());

        assertThat(couponOfferRepository.existsByCategoryId(category.getId())).isTrue();
    }

    private Category category(String name, String slug, int sortOrder, boolean active) {
        return Category.builder()
                .name(name)
                .slug(slug)
                .sortOrder(sortOrder)
                .active(active)
                .build();
    }
}
