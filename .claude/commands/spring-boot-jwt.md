---
description: JWT authentication and authorization for Spring Boot 3.5 with JJWT, Bearer/cookie auth, Spring Security 6.x, RBAC.
---

# Spring Boot JWT Security

$ARGUMENTS

**Versions**: Spring Boot 3.5, Spring Security 6.x, JJWT 0.12.6

## Dependencies (Maven)

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

## Security Config

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/swagger-ui/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
```

## JWT Service

```java
@Service
public class JwtService {
    @Value("${jwt.secret}") private String secret;
    @Value("${jwt.expiration}") private long expiration;

    public String generateToken(UserDetails user) {
        return Jwts.builder()
            .subject(user.getUsername())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiration))
            .claim("authorities", getAuthorities(user))
            .signWith(getKey())
            .compact();
    }

    public boolean isValid(String token, UserDetails user) {
        try {
            String username = Jwts.parser().verifyWith(getKey()).build()
                .parseSignedClaims(token).getPayload().getSubject();
            return username.equals(user.getUsername()) && !isExpired(token);
        } catch (JwtException e) { return false; }
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
```

## RBAC

```java
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAuthority('USER_READ')")
@PreAuthorize("hasPermission(#id, 'Document', 'WRITE') or hasRole('ADMIN')")
```

## Security Checklist

- [ ] HTTPS in production
- [ ] HttpOnly + Secure + SameSite cookies
- [ ] Secret key ≥ 256 bits
- [ ] Token expiration set
- [ ] Input validation on all auth endpoints
- [ ] Rate limiting on /auth endpoints
- [ ] Never store sensitive data in JWT claims
- [ ] Refresh token rotation
- [ ] Token blacklisting for logout
- [ ] Different secrets per environment
