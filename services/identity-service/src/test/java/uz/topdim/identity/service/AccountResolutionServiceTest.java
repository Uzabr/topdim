package uz.topdim.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountResolutionServiceTest {

    @Mock private UserRepository users;
    @Mock private PasswordEncoder encoder;

    @InjectMocks
    private AccountResolutionService svc;

    // ==================== resolveByPhone ====================

    @Test
    @DisplayName("resolveByPhone: существующий аккаунт по номеру → вход без создания")
    void resolveByPhone_existing_returnsSame() {
        User a = User.builder().id(1L).phone("+998901112233").phoneVerified(true).build();
        when(users.findByPhone("+998901112233")).thenReturn(Optional.of(a));

        assertThat(svc.resolveByPhone("+998901112233").getId()).isEqualTo(1L);
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("resolveByPhone: номера нет в базе → создать новый аккаунт, phoneVerified=true, роль USER")
    void resolveByPhone_new_createsVerified() {
        when(users.findByPhone("+998901112233")).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("h");
        when(users.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(9L);
            return u;
        });

        User r = svc.resolveByPhone("+998901112233");

        assertThat(r.isPhoneVerified()).isTrue();
        assertThat(r.getRole()).isEqualTo(Role.USER);
        assertThat(r.getPhone()).isEqualTo("+998901112233");
        assertThat(r.isEmailVerified()).isFalse();
        assertThat(r.isEnabled()).isTrue();
        assertThat(r.getEmail()).isEqualTo("phone_+998901112233@topdim.uz");
    }

    @Test
    @DisplayName("resolveByPhone: verified-владелец найден по номеру → войти в тот же аккаунт, новый не создаётся")
    void resolveByPhone_verifiedExisting_returnsIt() {
        User a = User.builder().id(1L).phone("+998901112233").phoneVerified(true).build();
        when(users.findByPhone("+998901112233")).thenReturn(Optional.of(a));

        User r = svc.resolveByPhone("+998901112233");

        assertThat(r.getId()).isEqualTo(1L);
        verify(users, never()).save(any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("resolveByPhone: номер занят НЕподтверждённым аккаунтом (сквоттер) → освободить у него номер, создать НОВЫЙ verified-аккаунт (не пускать жертву в чужой аккаунт)")
    void resolveByPhone_unverifiedSquatter_releasesAndCreatesNew() {
        User squatter = User.builder().id(1L).phone("+998901112233").phoneVerified(false).build();
        when(users.findByPhone("+998901112233")).thenReturn(Optional.of(squatter));
        when(encoder.encode(anyString())).thenReturn("h");
        when(users.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(users.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            if (u.getId() == null) u.setId(9L);
            return u;
        });

        User r = svc.resolveByPhone("+998901112233");

        // старый аккаунт (сквоттер) — номер освобождён, сам не входит
        assertThat(squatter.getPhone()).isNull();
        assertThat(squatter.isPhoneVerified()).isFalse();

        // новый аккаунт — доказанный владелец номера
        assertThat(r.getId()).isNotEqualTo(1L);
        assertThat(r.getPhone()).isEqualTo("+998901112233");
        assertThat(r.isPhoneVerified()).isTrue();
        assertThat(r.getRole()).isEqualTo(Role.USER);

        // saveAndFlush освобождения — строго до save() нового аккаунта (иначе INSERT нарушит unique(phone) в реальной БД)
        var order = inOrder(users);
        order.verify(users).saveAndFlush(squatter);
        order.verify(users).save(any(User.class));
    }

    // ==================== linkPhone ====================

    @Test
    @DisplayName("linkPhone: номер уже занят другим аккаунтом → отказ, без слияния")
    void linkPhone_takenByOther_throws() {
        User current = User.builder().id(1L).build();
        when(users.findByPhone("+998901112233")).thenReturn(Optional.of(User.builder().id(2L).build()));

        assertThatThrownBy(() -> svc.linkPhone(current, "+998901112233"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Номер уже занят");

        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("linkPhone: номер свободен → привязать к текущему аккаунту, пометить verified")
    void linkPhone_free_linksToCurrent() {
        User current = User.builder().id(1L).phoneVerified(false).build();
        when(users.findByPhone("+998901112233")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User r = svc.linkPhone(current, "+998901112233");

        assertThat(r.getPhone()).isEqualTo("+998901112233");
        assertThat(r.isPhoneVerified()).isTrue();
        verify(users).save(current);
    }

    @Test
    @DisplayName("linkPhone: номер уже принадлежит этому же аккаунту → не отказ, привязка идемпотентна")
    void linkPhone_alreadyOwnedBySelf_succeeds() {
        User current = User.builder().id(1L).phone("+998901112233").phoneVerified(true).build();
        when(users.findByPhone("+998901112233")).thenReturn(Optional.of(current));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User r = svc.linkPhone(current, "+998901112233");

        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.isPhoneVerified()).isTrue();
    }

    // ==================== resolveByGoogle ====================

    @Test
    @DisplayName("resolveByGoogle: sub уже привязан → вход в тот же аккаунт")
    void resolveByGoogle_matchBySub_returnsSame() {
        User a = User.builder().id(1L).googleSub("s").build();
        when(users.findByGoogleSub("s")).thenReturn(Optional.of(a));

        User r = svc.resolveByGoogle("s", "g@topdim.uz", true);

        assertThat(r.getId()).isEqualTo(1L);
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("resolveByGoogle: sub не найден, email совпал и подтверждён → войти + привязать sub")
    void resolveByGoogle_matchVerifiedEmail_linksSub() {
        User a = User.builder().id(1L).email("g@topdim.uz").emailVerified(true).build();
        when(users.findByGoogleSub("s")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("g@topdim.uz")).thenReturn(Optional.of(a));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User r = svc.resolveByGoogle("s", "g@topdim.uz", true);

        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getGoogleSub()).isEqualTo("s");
    }

    @Test
    @DisplayName("resolveByGoogle: email совпал, но НЕ подтверждён у владельца → освободить email у старого, создать новый Google-аккаунт")
    void resolveByGoogle_matchUnverifiedEmail_releasesAndCreatesNew() {
        User a = User.builder().id(1L).email("g@topdim.uz").emailVerified(false).build();
        when(users.findByGoogleSub("s")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("g@topdim.uz")).thenReturn(Optional.of(a));
        when(encoder.encode(anyString())).thenReturn("h");
        when(users.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            if (u.getId() == null) u.setId(9L);
            return u;
        });

        User r = svc.resolveByGoogle("s", "g@topdim.uz", true);

        assertThat(a.getEmail()).isNotEqualTo("g@topdim.uz");
        assertThat(a.isEmailVerified()).isFalse();

        assertThat(r.getId()).isNotEqualTo(1L);
        assertThat(r.getGoogleSub()).isEqualTo("s");
        assertThat(r.getEmail()).isEqualTo("g@topdim.uz");
        assertThat(r.isEmailVerified()).isTrue();
        assertThat(r.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("resolveByGoogle: ни sub, ни email не найдены → создать новый аккаунт")
    void resolveByGoogle_noMatch_createsNew() {
        when(users.findByGoogleSub("s")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("new@topdim.uz")).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("h");
        when(users.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(10L);
            return u;
        });

        User r = svc.resolveByGoogle("s", "new@topdim.uz", true);

        assertThat(r.getId()).isEqualTo(10L);
        assertThat(r.getGoogleSub()).isEqualTo("s");
        assertThat(r.getEmail()).isEqualTo("new@topdim.uz");
        assertThat(r.isEmailVerified()).isTrue();
        assertThat(r.getRole()).isEqualTo(Role.USER);
        assertThat(r.isEnabled()).isTrue();
    }
}
