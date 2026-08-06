package uz.topdim.coupon.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import uz.topdim.coupon.entity.Merchant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MerchantRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private MerchantRepository repository;

    @Test
    void onboardingUserId_cannotOwnTwoMerchants() {
        repository.saveAndFlush(merchant("First", 10L));

        assertThatThrownBy(() -> repository.saveAndFlush(merchant("Second", 10L)))
                .isInstanceOf(RuntimeException.class);
    }

    private Merchant merchant(String name, Long userId) {
        return Merchant.builder()
                .name(name)
                .userId(userId)
                .active(true)
                .build();
    }
}
