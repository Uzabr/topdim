package uz.topdim.identity.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {
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
}
