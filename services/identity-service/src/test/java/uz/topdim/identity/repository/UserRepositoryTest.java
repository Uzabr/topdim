package uz.topdim.identity.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest extends AbstractIntegrationTest {
    @Autowired UserRepository repo;

    @Test
    @DisplayName("findByGoogleSub: находит пользователя по google_sub")
    void findByGoogleSub_returnsUser() {
        User u = User.builder()
                .email("g@topdim.uz")
                .password("x")
                .firstName("G")
                .role(Role.USER)
                .enabled(true)
                .googleSub("google-123")
                .build();
        repo.save(u);

        assertThat(repo.findByGoogleSub("google-123")).isPresent();
        assertThat(repo.findByGoogleSub("nope")).isEmpty();
    }

    @Test
    @DisplayName("admin search: combines role with phone search")
    void searchForAdmin_combinesRoleAndPhone() {
        repo.save(User.builder()
                .email("partner@topdim.uz")
                .phone("+998901111111")
                .password("x")
                .firstName("Partner")
                .role(Role.PARTNER)
                .enabled(true)
                .build());
        repo.save(User.builder()
                .email("user@topdim.uz")
                .phone("+998902222222")
                .password("x")
                .firstName("User")
                .role(Role.USER)
                .enabled(true)
                .build());

        var result = repo.searchForAdmin(Role.PARTNER, "99890", PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(User::getEmail)
                .containsExactly("partner@topdim.uz");
    }
}
