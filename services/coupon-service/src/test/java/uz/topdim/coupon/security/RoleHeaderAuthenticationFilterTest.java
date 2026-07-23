package uz.topdim.coupon.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет анти-спуф контракт gateway↔сервис (#1): X-User-* принимаются только с валидным
 * X-Gateway-Auth, /internal/** защищён секретом; при пустом секрете — старое поведение.
 */
class RoleHeaderAuthenticationFilterTest {

    private static final String SECRET = "s3cr3t-gateway-value";

    private final RoleHeaderAuthenticationFilter filter = new RoleHeaderAuthenticationFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void setSecret(String value) {
        ReflectionTestUtils.setField(filter, "gatewaySecret", value);
    }

    @Test
    @DisplayName("Секрет пуст → X-User-* принимаются (обратная совместимость)")
    void secretDisabled_userHeaders_authenticated() throws Exception {
        setSecret("");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/coupons");
        req.addHeader("X-User-Id", "42");
        req.addHeader("X-User-Role", "USER");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Секрет задан + нет X-Gateway-Auth → личность проигнорирована (аноним)")
    void secretEnabled_missingGatewayAuth_identityIgnored() throws Exception {
        setSecret(SECRET);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/coupons");
        req.addHeader("X-User-Id", "42");
        req.addHeader("X-User-Role", "SUPER_ADMIN"); // попытка спуфа
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // пропущен как аноним
    }

    @Test
    @DisplayName("Секрет задан + валидный X-Gateway-Auth → аутентифицирован")
    void secretEnabled_validGatewayAuth_authenticated() throws Exception {
        setSecret(SECRET);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/coupons");
        req.addHeader("X-User-Id", "42");
        req.addHeader("X-User-Role", "USER");
        req.addHeader("X-Gateway-Auth", SECRET);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("Секрет задан + /internal без X-Gateway-Auth → 403")
    void secretEnabled_internalPath_noAuth_forbidden() throws Exception {
        setSecret(SECRET);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/internal/coupons/1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull(); // цепочка не продолжена
    }

    @Test
    @DisplayName("Секрет задан + /internal с валидным X-Gateway-Auth → пропущен")
    void secretEnabled_internalPath_validAuth_passes() throws Exception {
        setSecret(SECRET);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/internal/coupons/1");
        req.addHeader("X-Gateway-Auth", SECRET);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }
}
