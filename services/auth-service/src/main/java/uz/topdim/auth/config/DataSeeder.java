package uz.topdim.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import uz.topdim.auth.entity.Role;
import uz.topdim.auth.entity.User;
import uz.topdim.auth.repository.UserRepository;

/**
 * Сидер начальных данных.
 * При первом запуске создаёт аккаунт SUPER_ADMIN,
 * если в системе ещё нет ни одного суперадмина.
 *
 * Данные по умолчанию:
 *   email:    admin@topdim.uz
 *   пароль:   Admin123!
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedSuperAdmin();
    }

    private void seedSuperAdmin() {
        // Проверяем, есть ли уже SUPER_ADMIN в базе
        boolean superAdminExists = userRepository.findByEmail("admin@topdim.uz").isPresent();

        if (superAdminExists) {
            log.info("✅ SUPER_ADMIN уже существует, пропускаем создание");
            return;
        }

        User superAdmin = User.builder()
                .email("admin@topdim.uz")
                .phone("+998900000000")
                .password(passwordEncoder.encode("Admin123!"))
                .firstName("Super")
                .lastName("Admin")
                .role(Role.SUPER_ADMIN)
                .emailVerified(true)
                .phoneVerified(true)
                .enabled(true)
                .build();

        userRepository.save(superAdmin);

        log.info("🔑 ====================================================");
        log.info("🔑  Создан начальный SUPER_ADMIN:");
        log.info("🔑  Email:    admin@topdim.uz");
        log.info("🔑  Пароль:   Admin123!");
        log.info("🔑  ⚠️ ОБЯЗАТЕЛЬНО смените пароль после первого входа!");
        log.info("🔑 ====================================================");
    }
}
