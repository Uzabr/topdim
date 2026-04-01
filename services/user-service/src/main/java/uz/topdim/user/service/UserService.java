package uz.topdim.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.user.dto.*;
import uz.topdim.user.entity.Favorite;
import uz.topdim.user.entity.User;
import uz.topdim.user.exception.UserNotFoundException;
import uz.topdim.user.repository.FavoriteRepository;
import uz.topdim.user.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис управления пользователями.
 * Получение/обновление профиля, управление избранным.
 * Admin: список пользователей, блокировка.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FavoriteRepository favoriteRepository;

    // ==================== Profile ====================

    /**
     * Получает профиль пользователя.
     *
     * @param userId ID пользователя из JWT
     * @return данные профиля
     * @throws UserNotFoundException если не найден
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));
        return mapToProfile(user);
    }

    /**
     * Обновляет профиль пользователя.
     *
     * @param userId ID пользователя
     * @param request firstName, lastName, phone, avatarUrl
     * @return обновлённый профиль
     */
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        user = userRepository.save(user);
        return mapToProfile(user);
    }

    // ==================== Favorites ====================

    /**
     * Получает список избранных купонов пользователя.
     */
    @Transactional(readOnly = true)
    public List<FavoriteResponse> getFavorites(Long userId) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToFavorite)
                .collect(Collectors.toList());
    }

    /**
     * Добавляет купон в избранное.
     */
    @Transactional
    public FavoriteResponse addFavorite(Long userId, Long couponOfferId) {
        if (favoriteRepository.existsByUserIdAndCouponOfferId(userId, couponOfferId)) {
            throw new IllegalStateException("Купон уже в избранном");
        }

        Favorite favorite = Favorite.builder()
                .userId(userId)
                .couponOfferId(couponOfferId)
                .build();
        favorite = favoriteRepository.save(favorite);
        return mapToFavorite(favorite);
    }

    /**
     * Удаляет купон из избранного.
     */
    @Transactional
    public void removeFavorite(Long userId, Long couponOfferId) {
        favoriteRepository.deleteByUserIdAndCouponOfferId(userId, couponOfferId);
    }

    // ==================== Admin ====================

    /**
     * Получает список всех пользователей с пагинацией (Admin).
     * Опциональные фильтры: по роли, по поиску (email/имя).
     *
     * @param role фильтр по роли (null = все)
     * @param search поиск по email/имени (null = все)
     * @param page номер страницы
     * @param size размер страницы
     * @return страница пользователей
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getAllUsers(String role, String search, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<User> users;
        if (search != null && !search.isBlank()) {
            users = userRepository.searchByEmailOrName(search.trim(), pageable);
        } else if (role != null && !role.isBlank()) {
            users = userRepository.findByRole(role.toUpperCase(), pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        return users.map(this::mapToAdminUser);
    }

    /**
     * Блокирует или разблокирует пользователя (Admin).
     * Нельзя заблокировать ADMIN или SUPER_ADMIN.
     *
     * @param userId ID пользователя
     * @param blocked true = заблокировать, false = разблокировать
     * @return обновлённый AdminUserResponse
     */
    @Transactional
    public AdminUserResponse blockUser(Long userId, boolean blocked) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));

        // Защита: нельзя блокировать админов
        if ("ADMIN".equals(user.getRole()) || "SUPER_ADMIN".equals(user.getRole())) {
            throw new IllegalStateException("Невозможно заблокировать администратора");
        }

        user.setEnabled(!blocked);
        user = userRepository.save(user);

        log.info("ADMIN: Пользователь {} (email: {}) {}", userId,
                user.getEmail(), blocked ? "заблокирован" : "разблокирован");

        return mapToAdminUser(user);
    }

    /**
     * Получает пользователя по ID без ограничений (Admin).
     */
    @Transactional(readOnly = true)
    public AdminUserResponse getUserByIdAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));
        return mapToAdminUser(user);
    }

    // ==================== Mapping ====================

    private UserProfileResponse mapToProfile(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .avatarUrl(user.getAvatarUrl())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private AdminUserResponse mapToAdminUser(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private FavoriteResponse mapToFavorite(Favorite favorite) {
        return FavoriteResponse.builder()
                .id(favorite.getId())
                .couponOfferId(favorite.getCouponOfferId())
                .createdAt(favorite.getCreatedAt())
                .build();
    }
}
