package uz.topdim.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationAssigneeServiceTest {

    @Mock private UserRepository userRepository;
    private ModerationAssigneeService service;

    @BeforeEach
    void setUp() {
        service = new ModerationAssigneeService(userRepository);
    }

    @Test
    void moderatorAdminAndSuperAdminAreEligibleWhenActive() {
        for (Role role : new Role[]{Role.MODERATOR, Role.ADMIN, Role.SUPER_ADMIN}) {
            User user = user(10L + role.ordinal(), role, true, false);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            var result = service.resolve(user.getId());

            assertThat(result.userId()).isEqualTo(user.getId());
            assertThat(result.role()).isEqualTo(role.name());
            assertThat(result.eligible()).isTrue();
        }
    }

    @Test
    void partnerRoleIsNotEligible() {
        when(userRepository.findById(20L))
                .thenReturn(Optional.of(user(20L, Role.PARTNER, true, false)));

        assertThat(service.resolve(20L).eligible()).isFalse();
    }

    @Test
    void disabledOrDeletedStaffIsNotEligible() {
        when(userRepository.findById(21L))
                .thenReturn(Optional.of(user(21L, Role.MODERATOR, false, false)));
        when(userRepository.findById(22L))
                .thenReturn(Optional.of(user(22L, Role.ADMIN, true, true)));

        assertThat(service.resolve(21L).eligible()).isFalse();
        assertThat(service.resolve(22L).eligible()).isFalse();
    }

    @Test
    void missingUserReturnsIneligibleContextWithoutInventingRole() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        var result = service.resolve(999L);

        assertThat(result.userId()).isEqualTo(999L);
        assertThat(result.role()).isNull();
        assertThat(result.eligible()).isFalse();
    }

    @Test
    void listsOnlyActiveModerationStaffWithHumanReadableNames() {
        User moderator = user(31L, Role.MODERATOR, true, false);
        moderator.setFirstName("  Ali  ");
        moderator.setLastName("Valiyev");
        User admin = user(32L, Role.ADMIN, true, false);
        admin.setFirstName(" ");
        admin.setLastName(null);
        when(userRepository.findAllByRoleInAndEnabledTrueAndDeletedFalseOrderByFirstNameAscLastNameAsc(
                Set.of(Role.MODERATOR, Role.ADMIN, Role.SUPER_ADMIN)))
                .thenReturn(List.of(moderator, admin));

        var options = service.listEligible();

        assertThat(options).hasSize(2);
        assertThat(options.get(0).name()).isEqualTo("Ali Valiyev");
        assertThat(options.get(0).email()).isEqualTo("staff31@topdim.uz");
        assertThat(options.get(1).name()).isEqualTo("staff32@topdim.uz");
    }

    private User user(Long id, Role role, boolean enabled, boolean deleted) {
        return User.builder()
                .id(id)
                .email("staff" + id + "@topdim.uz")
                .password("hash")
                .firstName("Staff")
                .role(role)
                .enabled(enabled)
                .deleted(deleted)
                .build();
    }
}
