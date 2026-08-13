package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.identity.dto.ModerationAssigneeResponse;
import uz.topdim.identity.dto.ModerationAssigneeOptionResponse;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.repository.UserRepository;

import java.util.Set;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ModerationAssigneeService {

    private static final Set<Role> MODERATION_ROLES = Set.of(
            Role.MODERATOR,
            Role.ADMIN,
            Role.SUPER_ADMIN
    );

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ModerationAssigneeResponse resolve(Long userId) {
        return userRepository.findById(userId)
                .map(user -> new ModerationAssigneeResponse(
                        userId,
                        user.getRole() == null ? null : user.getRole().name(),
                        isEligible(user)))
                .orElseGet(() -> new ModerationAssigneeResponse(userId, null, false));
    }

    @Transactional(readOnly = true)
    public List<ModerationAssigneeOptionResponse> listEligible() {
        return userRepository
                .findAllByRoleInAndEnabledTrueAndDeletedFalseOrderByFirstNameAscLastNameAsc(
                        MODERATION_ROLES)
                .stream()
                .map(this::toOption)
                .toList();
    }

    private ModerationAssigneeOptionResponse toOption(User user) {
        String name = Stream.of(user.getFirstName(), user.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .reduce((first, second) -> first + " " + second)
                .orElse(user.getEmail());
        return new ModerationAssigneeOptionResponse(
                user.getId(), name, user.getEmail(), user.getRole().name());
    }

    private boolean isEligible(User user) {
        return user.isEnabled()
                && !user.isDeleted()
                && user.getRole() != null
                && MODERATION_ROLES.contains(user.getRole());
    }
}
