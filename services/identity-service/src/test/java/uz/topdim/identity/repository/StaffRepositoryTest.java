package uz.topdim.identity.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import uz.topdim.identity.entity.Staff;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StaffRepositoryTest extends AbstractIntegrationTest {

    @Autowired private StaffRepository repository;

    @Test
    void activeLocationQueryReturnsDistinctLocationsForRequestedMerchantOnly() {
        repository.save(staff(7L, 11L, true));
        repository.save(staff(7L, 11L, true));
        repository.save(staff(7L, 12L, false));
        repository.save(staff(7L, null, true));
        repository.save(staff(8L, 13L, true));

        assertThat(repository.findActiveLocationIdsByMerchantId(7L))
                .containsExactly(11L);
    }

    private Staff staff(Long merchantId, Long locationId, boolean active) {
        return Staff.builder()
                .userId(41L)
                .merchantId(merchantId)
                .merchantLocationId(locationId)
                .name("Employee")
                .phone("+998901234567")
                .role("CASHIER")
                .active(active)
                .build();
    }
}
