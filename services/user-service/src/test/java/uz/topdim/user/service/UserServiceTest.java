package uz.topdim.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.user.dto.FavoriteResponse;
import uz.topdim.user.dto.UpdateProfileRequest;
import uz.topdim.user.dto.UserProfileResponse;
import uz.topdim.user.entity.Favorite;
import uz.topdim.user.entity.User;
import uz.topdim.user.exception.UserNotFoundException;
import uz.topdim.user.repository.FavoriteRepository;
import uz.topdim.user.repository.UserRepository;

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

    // ==================== Profile ====================

    @Test
    @DisplayName("Профиль: найден — возвращает данные")
    void getProfile_found_returnsProfile() {
        User user = createTestUser(1L, "test@topdim.uz", "Иван", "Иванов");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserProfileResponse result = userService.getProfile(1L);

        assertThat(result.getEmail()).isEqualTo("test@topdim.uz");
        assertThat(result.getFirstName()).isEqualTo("Иван");
    }

    @Test
    @DisplayName("Профиль: не найден → UserNotFoundException")
    void getProfile_notFound_throwsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(999L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("Обновление профиля: обновляет указанные поля")
    void updateProfile_updatesProvidedFields() {
        User user = createTestUser(1L, "test@topdim.uz", "Старое", "Имя");

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFirstName("Новое");
        request.setLastName("Фамилия");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse result = userService.updateProfile(1L, request);

        assertThat(result.getFirstName()).isEqualTo("Новое");
        assertThat(result.getLastName()).isEqualTo("Фамилия");
    }

    // ==================== Favorites ====================

    @Test
    @DisplayName("Избранное: добавление — создаёт запись")
    void addFavorite_success_createsFavorite() {
        when(favoriteRepository.existsByUserIdAndCouponOfferId(1L, 5L)).thenReturn(false);
        when(favoriteRepository.save(any(Favorite.class))).thenAnswer(inv -> {
            Favorite f = inv.getArgument(0);
            f.setId(10L);
            return f;
        });

        FavoriteResponse result = userService.addFavorite(1L, 5L);

        assertThat(result.getCouponOfferId()).isEqualTo(5L);
        verify(favoriteRepository).save(any());
    }

    @Test
    @DisplayName("Избранное: дубликат → IllegalStateException")
    void addFavorite_duplicate_throwsException() {
        when(favoriteRepository.existsByUserIdAndCouponOfferId(1L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> userService.addFavorite(1L, 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже в избранном");

        verify(favoriteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Избранное: удаление — вызывает delete")
    void removeFavorite_callsDelete() {
        userService.removeFavorite(1L, 5L);

        verify(favoriteRepository).deleteByUserIdAndCouponOfferId(1L, 5L);
    }

    private User createTestUser(Long id, String email, String firstName, String lastName) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole("USER");
        return user;
    }
}
