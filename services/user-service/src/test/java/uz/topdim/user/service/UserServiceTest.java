package uz.topdim.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uz.topdim.user.dto.*;
import uz.topdim.user.entity.Favorite;
import uz.topdim.user.entity.User;
import uz.topdim.user.exception.UserNotFoundException;
import uz.topdim.user.repository.FavoriteRepository;
import uz.topdim.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private FavoriteRepository favoriteRepository;

    @InjectMocks
    private UserService userService;

    private User createTestUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setPhone("+998901234567");
        user.setFirstName("Алишер");
        user.setLastName("Каримов");
        user.setRole("USER");
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setPhoneVerified(false);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    // ==================== Profile ====================

    @Test
    @DisplayName("Профиль: найден — возвращает")
    void getProfile_found_returnsProfile() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(createTestUser()));

        UserProfileResponse result = userService.getProfile(1L);

        assertThat(result.getEmail()).isEqualTo("user@test.com");
        assertThat(result.getFirstName()).isEqualTo("Алишер");
    }

    @Test
    @DisplayName("Профиль: не найден → UserNotFoundException")
    void getProfile_notFound_throws() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(999L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("Обновление профиля: меняет только переданные поля")
    void updateProfile_partialUpdate_success() {
        User user = createTestUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFirstName("Бобур");

        UserProfileResponse result = userService.updateProfile(1L, request);

        assertThat(result.getFirstName()).isEqualTo("Бобур");
        assertThat(result.getLastName()).isEqualTo("Каримов"); // не менялось
    }

    // ==================== Favorites ====================

    @Test
    @DisplayName("Добавление в избранное: успешное")
    void addFavorite_success() {
        when(favoriteRepository.existsByUserIdAndCouponOfferId(1L, 5L)).thenReturn(false);
        when(favoriteRepository.save(any())).thenAnswer(inv -> {
            Favorite f = inv.getArgument(0);
            f.setId(10L);
            return f;
        });

        FavoriteResponse result = userService.addFavorite(1L, 5L);

        assertThat(result.getCouponOfferId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Добавление в избранное: дубликат → IllegalStateException")
    void addFavorite_duplicate_throws() {
        when(favoriteRepository.existsByUserIdAndCouponOfferId(1L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> userService.addFavorite(1L, 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже в избранном");
    }

    // ==================== Admin: getAllUsers ====================

    @Test
    @DisplayName("Admin: все пользователи без фильтра")
    void getAllUsers_noFilter_returnsAll() {
        Page<User> page = new PageImpl<>(List.of(createTestUser()));
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<AdminUserResponse> result = userService.getAllUsers(null, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("Admin: фильтр по роли USER")
    void getAllUsers_filterByRole_filtersCorrectly() {
        Page<User> page = new PageImpl<>(List.of(createTestUser()));
        when(userRepository.findByRole(eq("USER"), any(Pageable.class))).thenReturn(page);

        Page<AdminUserResponse> result = userService.getAllUsers("user", null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        verify(userRepository).findByRole(eq("USER"), any(Pageable.class));
    }

    @Test
    @DisplayName("Admin: поиск по email/имени")
    void getAllUsers_search_usesSearchQuery() {
        Page<User> page = new PageImpl<>(List.of(createTestUser()));
        when(userRepository.searchByEmailOrName(eq("Алишер"), any(Pageable.class))).thenReturn(page);

        Page<AdminUserResponse> result = userService.getAllUsers(null, "Алишер", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        verify(userRepository).searchByEmailOrName(eq("Алишер"), any());
    }

    // ==================== Admin: blockUser ====================

    @Test
    @DisplayName("Admin: блокировка пользователя — ставит enabled=false")
    void blockUser_regularUser_blocksSuccessfully() {
        User user = createTestUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse result = userService.blockUser(1L, true);

        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Admin: разблокировка — ставит enabled=true")
    void blockUser_unblock_enablesUser() {
        User user = createTestUser();
        user.setEnabled(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse result = userService.blockUser(1L, false);

        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Admin: блокировка ADMIN → IllegalStateException")
    void blockUser_adminUser_throws() {
        User admin = createTestUser();
        admin.setRole("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.blockUser(1L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Невозможно заблокировать администратора");
    }

    @Test
    @DisplayName("Admin: блокировка SUPER_ADMIN → IllegalStateException")
    void blockUser_superAdmin_throws() {
        User sa = createTestUser();
        sa.setRole("SUPER_ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(sa));

        assertThatThrownBy(() -> userService.blockUser(1L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Невозможно заблокировать администратора");
    }

    @Test
    @DisplayName("Admin: блокировка несуществующего → UserNotFoundException")
    void blockUser_notFound_throws() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.blockUser(999L, true))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ==================== Admin: getUserByIdAdmin ====================

    @Test
    @DisplayName("Admin: получение пользователя по ID")
    void getUserByIdAdmin_found_returnsAdminResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(createTestUser()));

        AdminUserResponse result = userService.getUserByIdAdmin(1L);

        assertThat(result.getEmail()).isEqualTo("user@test.com");
        assertThat(result.isEnabled()).isTrue();
    }
}
