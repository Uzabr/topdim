package uz.topdim.coupon.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * CQRS Read-Side: Elasticsearch поиск купонов.
 * Активируется при: elasticsearch.enabled=true в application.yml.
 *
 * Синхронизация: PostgreSQL (write) → Elasticsearch (read)
 * При создании/обновлении купона в PostgreSQL → вызвать indexCoupon().
 *
 * Для поиска: вместо SQL LIKE → Elasticsearch full-text query.
 * Скорость: <5ms для 1M+ документов.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class CouponSearchService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${elasticsearch.url:http://localhost:9200}")
    private String elasticsearchUrl;

    private static final String INDEX = "coupon_offers";

    /**
     * Индексирует купон в Elasticsearch.
     * Вызывать при CREATE/UPDATE купона в PostgreSQL.
     *
     * @param document данные купона для индексации
     */
    public void indexCoupon(CouponSearchDocument document) {
        try {
            String url = elasticsearchUrl + "/" + INDEX + "/_doc/" + document.getId();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String json = objectMapper.writeValueAsString(document);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            log.debug("Indexed coupon {} in Elasticsearch", document.getId());
        } catch (Exception e) {
            log.warn("Failed to index coupon {} in Elasticsearch: {}", document.getId(), e.getMessage());
        }
    }

    /**
     * Удаляет купон из индекса.
     *
     * @param couponId ID купона
     */
    public void deleteCoupon(Long couponId) {
        try {
            String url = elasticsearchUrl + "/" + INDEX + "/_doc/" + couponId;
            restTemplate.delete(url);
            log.debug("Deleted coupon {} from Elasticsearch", couponId);
        } catch (Exception e) {
            log.warn("Failed to delete coupon {} from Elasticsearch: {}", couponId, e.getMessage());
        }
    }

    /**
     * Полнотекстовый поиск купонов.
     * Ищет по title, offerDescription, merchantName, categoryName.
     *
     * @param query поисковый запрос
     * @param categoryId фильтр по категории (nullable)
     * @param from offset (пагинация)
     * @param size размер страницы
     * @return список ID найденных купонов (для загрузки из PostgreSQL)
     */
    @SuppressWarnings("unchecked")
    public List<Long> searchCouponIds(String query, Long categoryId, int from, int size) {
        try {
            String url = elasticsearchUrl + "/" + INDEX + "/_search";

            Map<String, Object> bool = new HashMap<>();
            List<Map<String, Object>> must = new ArrayList<>();
            List<Map<String, Object>> filter = new ArrayList<>();

            // Full-text query
            if (query != null && !query.isBlank()) {
                must.add(Map.of("multi_match", Map.of(
                        "query", query,
                        "fields", List.of("title^3", "offerDescription^2", "merchantName", "categoryName"),
                        "type", "best_fields",
                        "fuzziness", "AUTO"
                )));
            }

            // Category filter
            if (categoryId != null) {
                filter.add(Map.of("term", Map.of("categoryId", categoryId)));
            }

            // Only ACTIVE coupons
            filter.add(Map.of("term", Map.of("status", "ACTIVE")));

            bool.put("must", must.isEmpty() ? List.of(Map.of("match_all", Map.of())) : must);
            bool.put("filter", filter);

            Map<String, Object> body = Map.of(
                    "query", Map.of("bool", bool),
                    "from", from,
                    "size", size,
                    "sort", List.of(Map.of("totalSold", Map.of("order", "desc"))),
                    "_source", List.of("id")
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String json = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();

            if (responseBody == null) return List.of();

            Map<String, Object> hits = (Map<String, Object>) responseBody.get("hits");
            List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");

            return hitList.stream()
                    .map(hit -> {
                        Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                        return ((Number) source.get("id")).longValue();
                    })
                    .toList();

        } catch (Exception e) {
            log.warn("Elasticsearch search failed, falling back to PostgreSQL: {}", e.getMessage());
            return List.of(); // Service layer falls back to PostgreSQL
        }
    }

    /**
     * Создаёт индекс с маппингом (вызвать один раз при старте).
     */
    public void createIndexIfNotExists() {
        try {
            String url = elasticsearchUrl + "/" + INDEX;
            restTemplate.getForEntity(url, String.class);
            log.info("Elasticsearch index '{}' already exists", INDEX);
        } catch (Exception e) {
            try {
                String url = elasticsearchUrl + "/" + INDEX;
                String mapping = """
                    {
                      "mappings": {
                        "properties": {
                          "id": { "type": "long" },
                          "title": { "type": "text", "analyzer": "russian" },
                          "offerDescription": { "type": "text", "analyzer": "russian" },
                          "categoryName": { "type": "text" },
                          "categorySlug": { "type": "keyword" },
                          "categoryId": { "type": "long" },
                          "merchantName": { "type": "text" },
                          "merchantId": { "type": "long" },
                          "oldPrice": { "type": "scaled_float", "scaling_factor": 100 },
                          "fromPrice": { "type": "scaled_float", "scaling_factor": 100 },
                          "discountPercent": { "type": "integer" },
                          "status": { "type": "keyword" },
                          "totalSold": { "type": "integer" },
                          "address": { "type": "text" },
                          "createdAt": { "type": "date" },
                          "buyUntil": { "type": "date" },
                          "useUntil": { "type": "date" }
                        }
                      }
                    }
                    """;
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(mapping, headers);
                restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
                log.info("Created Elasticsearch index '{}'", INDEX);
            } catch (Exception ex) {
                log.warn("Failed to create Elasticsearch index: {}", ex.getMessage());
            }
        }
    }
}
