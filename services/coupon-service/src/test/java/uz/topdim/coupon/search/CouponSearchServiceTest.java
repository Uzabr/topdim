package uz.topdim.coupon.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CouponSearchServiceTest {

    private CouponSearchService couponSearchService;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        couponSearchService = new CouponSearchService();
        ReflectionTestUtils.setField(couponSearchService, "elasticsearchUrl", "http://localhost:9200");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(couponSearchService, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    @DisplayName("searchCouponIds: secondary search использует canonical offerDescription вместо legacy short/full description")
    void searchCouponIds_usesCanonicalOfferDescriptionField() {
        server.expect(once(), requestTo("http://localhost:9200/coupon_offers/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("\"offerDescription^2\""),
                        not(containsString("\"shortDescription^2\"")),
                        not(containsString("\"fullDescription\""))
                )))
                .andRespond(withSuccess("""
                        {
                          "hits": {
                            "hits": [
                              { "_source": { "id": 42 } }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<Long> result = couponSearchService.searchCouponIds("spa", null, 0, 10);

        assertThat(result).containsExactly(42L);
        server.verify();
    }

    @Test
    @DisplayName("createIndexIfNotExists: индекс объявляет canonical offerDescription и не возвращает legacy text fields в mapping")
    void createIndexIfNotExists_usesCanonicalOfferDescriptionMapping() {
        server.expect(once(), requestTo("http://localhost:9200/coupon_offers"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        server.expect(once(), requestTo("http://localhost:9200/coupon_offers"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(allOf(
                        containsString("\"offerDescription\""),
                        not(containsString("\"shortDescription\"")),
                        not(containsString("\"fullDescription\""))
                )))
                .andRespond(withSuccess());

        couponSearchService.createIndexIfNotExists();

        server.verify();
    }
}
