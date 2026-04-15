package uz.topdim.identity.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.repository.UserRepository;

/**
 * Начальное заполнение БД при первом запуске.
 * Создаёт SUPER_ADMIN если его ещё нет.
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
        boolean superAdminExists = userRepository.findByEmailIgnoreCase("admin@topdim.uz").isPresent();

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
