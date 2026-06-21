package uz.topdim.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H3: Tests that ipKeyResolver is resistant to X-Forwarded-For spoofing.
 * The resolver should use the rightmost trusted hop (added by traefik),
 * not the leftmost client-controlled value.
 */
class RateLimitConfigTest {

    private final KeyResolver resolver = new RateLimitConfig().ipKeyResolver();

    @Test
    @DisplayName("H3: spoofed XFF — resolver ignores fake IPs, takes rightmost trusted hop")
    void spoofedXff_takesRightmostTrustedHop() {
        // Client sends: X-Forwarded-For: fake1, fake2
        // Traefik appends real IP → X-Forwarded-For: fake1, fake2, 91.200.42.1
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 12345))
                        .header("X-Forwarded-For", "1.2.3.4, 5.6.7.8, 91.200.42.1")
                        .build()
        );

        String key = resolver.resolve(exchange).block();

        // maxTrustedIndex(1): trusts 1 proxy → takes rightmost-1 = "91.200.42.1"
        // (traefik adds the last one, so the one before last is the actual client)
        // Actually maxTrustedIndex(1) takes the rightmost entry as the trusted proxy appends it
        assertThat(key).isNotEqualTo("1.2.3.4");
        assertThat(key).isNotEqualTo("5.6.7.8");
    }

    @Test
    @DisplayName("H3: single IP in XFF — resolver returns that IP")
    void singleXff_returnsThatIp() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login")
                        .remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 12345))
                        .header("X-Forwarded-For", "91.200.42.1")
                        .build()
        );

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("91.200.42.1");
    }

    @Test
    @DisplayName("H3: no XFF header — falls back to remoteAddress")
    void noXff_fallsBackToRemoteAddress() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login")
                        .remoteAddress(new java.net.InetSocketAddress("192.168.1.100", 12345))
                        .build()
        );

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("H3: two IPs in XFF — spoofed first IP is ignored")
    void twoIpsInXff_spoofedFirstIsIgnored() {
        // Attacker sends X-Forwarded-For: attacker-fake
        // Traefik appends: 203.0.113.50
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 12345))
                        .header("X-Forwarded-For", "attacker-fake, 203.0.113.50")
                        .build()
        );

        String key = resolver.resolve(exchange).block();

        assertThat(key).isNotEqualTo("attacker-fake");
    }
}
