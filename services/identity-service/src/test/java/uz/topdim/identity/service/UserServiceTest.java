package uz.topdim.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uz.topdim.identity.dto.AdminUserResponse;
import uz.topdim.identity.dto.UpdateProfileRequest;
import uz.topdim.identity.dto.UserProfileResponse;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.TrustLevel;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.UserNotFoundException;
import uz.topdim.identity.repository.FavoriteRepository;
import uz.topdim.identity.repository.RefreshTokenRepository;
import uz.topdim.identity.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private SecurityVersionService securityVersionService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TrustService trustService;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUpTrustServiceDefault() {
        // Тесты, не относящиеся к trustLevel, не должны падать с NPE на .name();
        // lenient(), т.к. не все тесты вызывают mapToProfile (например, getProfile_notFound_throws).
        lenient().when(trustService.computeTrustLevel(any(User.class))).thenReturn(TrustLevel.L0);
    }

    private User createUser() {
        return User.builder()
                .id(1L)
                .email("user@topdim.uz")
                .phone("+998901234567")
                .firstName("Ali")
                .lastName("Valiyev")
                .role(Role.USER)
                .enabled(true)
                .emailVerified(true)
                .phoneVerified(false)
                .build();
    }

    @Test
    @DisplayName("updateProfile: не даёт установить телефон, уже занятый другим пользователем")
    void updateProfile_duplicatePhone_throws() {
        User user = createUser();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhone("+998909999999");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByPhone("+998909999999")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Телефон уже зарегистрирован");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("updateProfile: нормализует телефон перед сохранением")
    void updateProfile_normalizesPhoneBeforeSave() {
        User user = createUser();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhone(" +998901112233 ");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByPhone("+998901112233")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.updateProfile(1L, request);

        assertThat(response.getPhone()).isEqualTo("+998901112233");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateProfile: смена подтверждённого телефона сбрасывает его верификацию")
    void updateProfile_changedVerifiedPhone_resetsVerification() {
        User user = createUser();
        user.setPhoneVerified(true);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhone("+998901112233");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByPhone("+998901112233")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.updateProfile(1L, request);

        assertThat(response.isPhoneVerified()).isFalse();
    }

    @Test
    @DisplayName("updateProfile: неизменённый телефон сохраняет верификацию")
    void updateProfile_unchangedPhone_preservesVerification() {
        User user = createUser();
        user.setPhoneVerified(true);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhone(" +998901234567 ");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.updateProfile(1L, request);

        assertThat(response.isPhoneVerified()).isTrue();
        verify(userRepository, never()).existsByPhone(any());
    }

    @Test
    @DisplayName("updateProfile: пустая фамилия очищает сохранённое значение")
    void updateProfile_emptyLastName_clearsStoredLastName() {
        User user = createUser();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setLastName("   ");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.updateProfile(1L, request);

        assertThat(response.getLastName()).isNull();
    }

    @Test
    @DisplayName("updateProfile: отсутствующая фамилия не меняет сохранённое значение")
    void updateProfile_nullLastName_preservesStoredLastName() {
        User user = createUser();
        UpdateProfileRequest request = new UpdateProfileRequest();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.updateProfile(1L, request);

        assertThat(response.getLastName()).isEqualTo("Valiyev");
    }

    @Test
    @DisplayName("getProfile: несуществующий пользователь -> UserNotFoundException")
    void getProfile_notFound_throws() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(999L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("getProfile: отдаёт trustLevel, вычисленный через TrustService (не хранимую колонку)")
    void getProfile_returnsComputedTrustLevel() {
        User user = createUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(trustService.computeTrustLevel(user)).thenReturn(TrustLevel.L1);

        UserProfileResponse response = userService.getProfile(1L);

        assertThat(response.getTrustLevel()).isEqualTo("L1");
        verify(trustService).computeTrustLevel(user);
    }

    @Test
    @DisplayName("markPaid: ставит paidAt один раз (идемпотентно — повторный вызов не перезатирает)")
    void markPaid_setsPaidAtOnce() {
        User u = User.builder().id(3L).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(u));

        userService.markPaid(3L);

        assertThat(u.getPaidAt()).isNotNull();
        var first = u.getPaidAt();

        userService.markPaid(3L); // идемпотентно — не перезатирает

        assertThat(u.getPaidAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("getAllUsers: одновременно применяет роль и поиск")
    void getAllUsers_roleAndSearch_combinesFilters() {
        User partner = createUser();
        partner.setRole(Role.PARTNER);
        when(userRepository.searchForAdmin(eq(Role.PARTNER), eq("99890"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(partner)));

        var result = userService.getAllUsers("partner", " 99890 ", 0, 20);

        assertThat(result.getContent())
                .extracting(AdminUserResponse::getRole)
                .containsExactly("PARTNER");
        verify(userRepository).searchForAdmin(eq(Role.PARTNER), eq("99890"), any(Pageable.class));
        verify(userRepository, never()).searchByEmailOrName(any(), any());
        verify(userRepository, never()).findByRole(any(), any());
    }
}
