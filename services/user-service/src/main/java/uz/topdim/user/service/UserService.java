package uz.topdim.user.service;

import lombok.RequiredArgsConstructor;
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
 */
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
     *
     * @param userId ID пользователя
     * @return список FavoriteResponse
     */
    @Transactional(readOnly = true)
    public List<FavoriteResponse> getFavorites(Long userId) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToFavorite)
                .collect(Collectors.toList());
    }

    /**
     * Добавляет купон в избранное.
     *
     * @param userId ID пользователя
     * @param couponOfferId ID купона
     * @return созданная запись избранного
     * @throws IllegalArgumentException если уже в избранном
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
     *
     * @param userId ID пользователя
     * @param couponOfferId ID купона
     */
    @Transactional
    public void removeFavorite(Long userId, Long couponOfferId) {
        favoriteRepository.deleteByUserIdAndCouponOfferId(userId, couponOfferId);
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

    private FavoriteResponse mapToFavorite(Favorite favorite) {
        return FavoriteResponse.builder()
                .id(favorite.getId())
                .couponOfferId(favorite.getCouponOfferId())
                .createdAt(favorite.getCreatedAt())
                .build();
    }
}
