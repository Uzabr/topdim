package uz.topdim.coupon.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Сервис резервных обложек по категориям.
 * Когда партнёр не загрузил фото, а админ отправляет купон на согласование —
 * система подставляет fallback cover из категории.
 */
@Slf4j
@Service
public class CouponCoverFallbackService {

    /**
     * Категория slug → URL обложки по умолчанию.
     * Используются статичные заглушки TopDim для всех 9 демо-категорий.
     */
    private static final Map<String, String> CATEGORY_COVERS = Map.of(
            "cafe-and-restaurants", "/defaults/covers/cafe-restaurants.jpg",
            "beauty",              "/defaults/covers/beauty.jpg",
            "health",              "/defaults/covers/health.jpg",
            "spa",                 "/defaults/covers/spa.jpg",
            "fitness",             "/defaults/covers/fitness.jpg",
            "entertainment",       "/defaults/covers/entertainment.jpg",
            "education",           "/defaults/covers/education.jpg",
            "services",            "/defaults/covers/services.jpg",
            "shops",               "/defaults/covers/shops.jpg"
    );

    private static final String DEFAULT_COVER = "/defaults/covers/topdim-default.jpg";

    /**
     * Возвращает fallback cover URL для указанного slug категории.
     *
     * @param categorySlug slug категории (например, "beauty")
     * @return URL fallback cover или дефолтный TopDim cover
     */
    public String getFallbackCover(String categorySlug) {
        if (categorySlug == null || categorySlug.isBlank()) {
            log.warn("Запрошен fallback cover без slug категории, используем дефолт");
            return DEFAULT_COVER;
        }
        String cover = CATEGORY_COVERS.getOrDefault(categorySlug, DEFAULT_COVER);
        log.info("Fallback cover для категории '{}': {}", categorySlug, cover);
        return cover;
    }
}
