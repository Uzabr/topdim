package uz.topdim.coupon.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeHistory;
import uz.topdim.coupon.entity.MerchantProfileChangeLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class MerchantProfileChangeRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantLocationRepository merchantLocationRepository;
    @Autowired private MerchantProfileChangeRequestRepository requestRepository;
    @Autowired private MerchantProfileChangeLocationRepository changeLocationRepository;
    @Autowired private MerchantProfileChangeHistoryRepository historyRepository;
    @Autowired private EntityManager entityManager;

    private Merchant merchant;
    private MerchantLocation primaryLocation;

    @BeforeEach
    void setUp() {
        merchant = merchantRepository.saveAndFlush(Merchant.builder()
                .name("Published company")
                .description("Published description")
                .email("owner@sizbiz.uz")
                .active(true)
                .build());
        primaryLocation = merchantLocationRepository.saveAndFlush(MerchantLocation.builder()
                .merchant(merchant)
                .title("Main branch")
                .address("Tashkent")
                .phone("+998901234567")
                .workingHours("09:00-18:00")
                .primary(true)
                .active(true)
                .build());
    }

    @Test
    void storesFullSnapshotWithLocationsAndHistory() {
        MerchantProfileChangeRequest saved = requestRepository.saveAndFlush(requestForMerchant());
        changeLocationRepository.saveAndFlush(MerchantProfileChangeLocation.builder()
                .request(saved)
                .sourceLocationId(primaryLocation.getId())
                .title("Updated main branch")
                .address("Tashkent, Chilanzar")
                .phone("+998901234567")
                .workingHours("08:00-20:00")
                .latitude(41.2857)
                .longitude(69.2034)
                .primary(true)
                .active(true)
                .sortOrder(0)
                .build());
        historyRepository.saveAndFlush(MerchantProfileChangeHistory.builder()
                .request(saved)
                .previousStatus(null)
                .newStatus(MerchantProfileChangeStatus.DRAFT)
                .actorUserId(41L)
                .actorRole("OWNER")
                .comment("Initial draft")
                .build());
        entityManager.clear();

        MerchantProfileChangeRequest loaded = requestRepository
                .findDetailedById(saved.getId())
                .orElseThrow();

        assertThat(merchant.getProfileVersion()).isEqualTo(1L);
        assertThat(loaded.getBaseProfileVersion()).isEqualTo(1L);
        assertThat(loaded.getLocations()).singleElement().satisfies(location -> {
            assertThat(location.getSourceLocationId()).isEqualTo(primaryLocation.getId());
            assertThat(location.isPrimary()).isTrue();
            assertThat(location.isActive()).isTrue();
            assertThat(location.getSortOrder()).isZero();
        });
        assertThat(historyRepository.findByRequestIdOrderByCreatedAtAsc(saved.getId()))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getPreviousStatus()).isNull();
                    assertThat(history.getNewStatus()).isEqualTo(MerchantProfileChangeStatus.DRAFT);
                    assertThat(history.getActorUserId()).isEqualTo(41L);
                });
    }

    @Test
    void exposesLockedReadsAndCountsOnlyRequestedActiveStatuses() {
        MerchantProfileChangeRequest draft = requestRepository.saveAndFlush(requestForMerchant());
        MerchantProfileChangeRequest rejected = requestForMerchant();
        rejected.setStatus(MerchantProfileChangeStatus.REJECTED);
        requestRepository.saveAndFlush(rejected);

        assertThat(merchantRepository.findByIdForUpdate(merchant.getId())).isPresent();
        assertThat(requestRepository.findDetailedByIdForUpdate(draft.getId())).isPresent();
        assertThat(requestRepository.countByMerchantIdAndStatusIn(
                merchant.getId(), MerchantProfileChangeStatus.activeStatuses()))
                .isEqualTo(1L);
    }

    private MerchantProfileChangeRequest requestForMerchant() {
        return MerchantProfileChangeRequest.builder()
                .merchant(merchant)
                .authorUserId(41L)
                .authorRole("OWNER")
                .baseProfileVersion(1L)
                .status(MerchantProfileChangeStatus.DRAFT)
                .name("Updated company")
                .description("Updated description")
                .logoUrl("https://cdn.sizbiz.uz/logo.png")
                .coverUrl(null)
                .email("company@sizbiz.uz")
                .website("https://sizbiz.uz")
                .contactPerson("Company owner")
                .build();
    }
}
