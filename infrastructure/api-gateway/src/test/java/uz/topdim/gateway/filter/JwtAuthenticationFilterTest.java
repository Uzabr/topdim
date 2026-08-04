package uz.topdim.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import uz.topdim.gateway.service.ReactiveTokenValidationService;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET = "dG9wZGltLXNlY3JldC1rZXktZm9yLWp3dC10b2tlbi1zaWduaW5nLTI1Ni1iaXQ=";

    @Mock
    private ReactiveTokenValidationService tokenValidationService;

    @Mock
    private GatewayFilterChain gatewayFilterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("confirm/request: без токена gateway больше не считает endpoint публичным")
    void confirmRequest_withoutToken_returnsUnauthorized() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/confirm/request").build()
        );

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(gatewayFilterChain, never()).filter(any());
    }

    @Test
    @DisplayName("защищённый запрос: валидный JWT прокидывает X-User-* headers downstream")
    void protectedRequest_withValidJwt_forwardsGatewayHeaders() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        String token = createToken("7", "USER", "user@topdim.uz", "jti-123", 2L);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Id", "attacker-value")
                        .build()
        );

        when(tokenValidationService.isTokenInvalid("jti-123", "7", 2L, false)).thenReturn(Mono.just(false));
        when(gatewayFilterChain.filter(any())).thenReturn(Mono.empty());

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        ArgumentCaptor<org.springframework.web.server.ServerWebExchange> exchangeCaptor =
                ArgumentCaptor.forClass(org.springframework.web.server.ServerWebExchange.class);
        verify(gatewayFilterChain).filter(exchangeCaptor.capture());

        HttpHeaders forwardedHeaders = exchangeCaptor.getValue().getRequest().getHeaders();
        assertThat(forwardedHeaders.getFirst("X-User-Id")).isEqualTo("7");
        assertThat(forwardedHeaders.getFirst("X-User-Email")).isEqualTo("user@topdim.uz");
        assertThat(forwardedHeaders.getFirst("X-User-Role")).isEqualTo("USER");
    }

    @Test
    @DisplayName("защищённый запрос: внешний X-Merchant-Id удаляется из downstream")
    void protectedRequest_externalMerchantId_isStripped() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        String token = createToken("7", "PARTNER", "partner@topdim.uz", "jti-456", 1L);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/partner/redemptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Merchant-Id", "spoofed-merchant")
                        .build()
        );

        when(tokenValidationService.isTokenInvalid("jti-456", "7", 1L, false)).thenReturn(Mono.just(false));
        when(gatewayFilterChain.filter(any())).thenReturn(Mono.empty());

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        ArgumentCaptor<org.springframework.web.server.ServerWebExchange> exchangeCaptor =
                ArgumentCaptor.forClass(org.springframework.web.server.ServerWebExchange.class);
        verify(gatewayFilterChain).filter(exchangeCaptor.capture());

        HttpHeaders forwardedHeaders = exchangeCaptor.getValue().getRequest().getHeaders();
        assertThat(forwardedHeaders.getFirst("X-User-Id")).isEqualTo("7");
        assertThat(forwardedHeaders.getFirst("X-User-Role")).isEqualTo("PARTNER");
        assertThat(forwardedHeaders.containsKey("X-Merchant-Id")).isFalse();
    }

    @Test
    @DisplayName("защищённый запрос: invalidated token отклоняется до downstream")
    void protectedRequest_withInvalidatedToken_returnsUnauthorized() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        String token = createToken("7", "USER", "user@topdim.uz", "jti-123", 2L);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );

        when(tokenValidationService.isTokenInvalid("jti-123", "7", 2L, false)).thenReturn(Mono.just(true));

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(gatewayFilterChain, never()).filter(any());
    }

    // ==================== L3: prefix-matching boundary ====================

    @Test
    @DisplayName("L3: /api/v1/couponsX не считается open endpoint (suffix не от segment boundary)")
    void prefixBoundary_pathWithTrailingChars_requiresAuth() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/couponsXYZ").build()
        );

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(gatewayFilterChain, never()).filter(any());
    }

    @Test
    @DisplayName("L3: /api/v1/coupons/123 всё ещё считается open endpoint")
    void prefixBoundary_pathWithSubpath_isOpen() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/coupons/123").build()
        );

        when(gatewayFilterChain.filter(any())).thenReturn(Mono.empty());

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        verify(gatewayFilterChain).filter(any());
    }

    @Test
    @DisplayName("L3: /api/v1/coupons (точное совпадение) считается open endpoint")
    void prefixBoundary_exactMatch_isOpen() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/coupons").build()
        );

        when(gatewayFilterChain.filter(any())).thenReturn(Mono.empty());

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        verify(gatewayFilterChain).filter(any());
    }

    @Test
    @DisplayName("L3: /api/v1/bot/coupons/1/approve считается open endpoint (bot webhook)")
    void prefixBoundary_botWebhookPath_isOpen() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/bot/coupons/1/approve").build()
        );

        when(gatewayFilterChain.filter(any())).thenReturn(Mono.empty());

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        verify(gatewayFilterChain).filter(any());
    }

    @Test
    @DisplayName("L3: /api/v1/situations — публичный open endpoint (без токена)")
    void situations_publicList_isOpen() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/situations").build()
        );

        when(gatewayFilterChain.filter(any())).thenReturn(Mono.empty());

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        verify(gatewayFilterChain).filter(any());
    }

    @Test
    @DisplayName("L3: /api/v1/admin/situations НЕ open — требует аутентификации")
    void adminSituations_requiresAuth() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/situations").build()
        );

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(gatewayFilterChain, never()).filter(any());
    }

    @Test
    @DisplayName("questions: публичный GET /coupon/{id} проходит без токена")
    void questionsCouponGet_isOpen() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/questions/coupon/10").build()
        );

        when(gatewayFilterChain.filter(any())).thenReturn(Mono.empty());

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        verify(gatewayFilterChain).filter(any());
    }

    @Test
    @DisplayName("questions: POST требует JWT, чтобы gateway проставил X-User-Id")
    void questionsPost_requiresAuth() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/questions").build()
        );

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(gatewayFilterChain, never()).filter(any());
    }

    @Test
    @DisplayName("questions: GET /my требует JWT")
    void questionsMy_requiresAuth() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/questions/my").build()
        );

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(gatewayFilterChain, never()).filter(any());
    }

    // ==================== T8: phone-OTP / google auth — новые open endpoints ====================

    @Test
    @DisplayName("T8: /api/v1/auth/phone/request без токена — open endpoint")
    void phoneRequest_isOpen() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/phone/request").build()
        );

        when(gatewayFilterChain.filter(any())).thenReturn(Mono.empty());

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        verify(gatewayFilterChain).filter(any());
    }

    @Test
    @DisplayName("T8: /api/v1/auth/phone/confirm без токена — open endpoint")
    void phoneConfirm_isOpen() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/phone/confirm").build()
        );

        when(gatewayFilterChain.filter(any())).thenReturn(Mono.empty());

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        verify(gatewayFilterChain).filter(any());
    }

    @Test
    @DisplayName("T8: /api/v1/auth/google без токена — open endpoint")
    void googleAuth_isOpen() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/google").build()
        );

        when(gatewayFilterChain.filter(any())).thenReturn(Mono.empty());

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        verify(gatewayFilterChain).filter(any());
    }

    @Test
    @DisplayName("T8: /api/v1/auth/guest остаётся open endpoint (не удалён) — иначе 401 вместо 410 от identity")
    void guest_stillOpen_notRemoved() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/guest").build()
        );

        when(gatewayFilterChain.filter(any())).thenReturn(Mono.empty());

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        verify(gatewayFilterChain).filter(any());
    }

    // ==================== T7a: email-change — новый open endpoint (confirm), request требует JWT ====================

    @Test
    @DisplayName("T7a: /api/v1/auth/email-change/confirm без токена — open endpoint (токен сам аутентифицирует)")
    void emailChangeConfirm_isOpen() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/email-change/confirm").build()
        );

        when(gatewayFilterChain.filter(any())).thenReturn(Mono.empty());

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        verify(gatewayFilterChain).filter(any());
    }

    @Test
    @DisplayName("T7a: /api/v1/auth/email-change/request без токена — требует JWT (НЕ open endpoint)")
    void emailChangeRequest_requiresAuth() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/email-change/request").build()
        );

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(gatewayFilterChain, never()).filter(any());
    }

    // ==================== T8a: phone/link — привязка телефона, требует JWT ====================

    @Test
    @DisplayName("T8a: /api/v1/auth/phone/link без токена — требует JWT (НЕ open endpoint)")
    void phoneLink_requiresAuth() {
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/phone/link").build()
        );

        jwtAuthenticationFilter.filter(exchange, gatewayFilterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(gatewayFilterChain, never()).filter(any());
    }

    private String createToken(String subject, String role, String email, String jti, long securityVersion) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        return Jwts.builder()
                .subject(subject)
                .id(jti)
                .claim("email", email)
                .claim("role", role)
                .claim("securityVersion", securityVersion)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }
}
