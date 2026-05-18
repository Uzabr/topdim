---
description: Spring Boot testing patterns — unit tests, slice tests (@DataJpaTest, @WebMvcTest), integration tests with Testcontainers.
---

# Spring Boot Testing Patterns

$ARGUMENTS

## Test Pyramid

```
        /\
       /E2E\         ← Few, critical paths only
      /─────\
     /Integr \       ← @SpringBootTest + Testcontainers
    /──────────\
   /Slice Tests \    ← @DataJpaTest, @WebMvcTest
  /──────────────\
 /  Unit Tests   \   ← Mockito, no Spring context
/────────────────── \
```

## Unit Test (fastest, no Spring)

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductRepository repository;
    @InjectMocks ProductService service;

    @Test
    void shouldCreateProduct() {
        var request = new CreateProductRequest("Widget", new BigDecimal("9.99"));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.create(request);

        assertThat(result.name()).isEqualTo("Widget");
        verify(repository).save(any());
    }
}
```

## Repository Slice (@DataJpaTest)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE) // use real DB
@Testcontainers
class ProductRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired ProductRepository repository;

    @Test
    void shouldFindByStatus() {
        repository.save(Product.create("Widget", ACTIVE));
        var result = repository.findByStatus(ACTIVE, Pageable.unpaged());
        assertThat(result).hasSize(1);
    }
}
```

## Controller Slice (@WebMvcTest)

```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean ProductService service;
    @Autowired ObjectMapper mapper;

    @Test
    void shouldReturn201OnCreate() throws Exception {
        var request = new CreateProductRequest("Widget", new BigDecimal("9.99"));
        when(service.create(any())).thenReturn(new ProductResponse("1", "Widget"));

        mockMvc.perform(post("/api/products")
                .contentType(APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Widget"));
    }
}
```

## Integration Test (@SpringBootTest)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class ProductIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired TestRestTemplate restTemplate;

    @Test
    void createAndRetrieveProduct() {
        var created = restTemplate.postForEntity("/api/products",
            new CreateProductRequest("Widget", new BigDecimal("9.99")),
            ProductResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(CREATED);
        assertThat(created.getBody().name()).isEqualTo("Widget");
    }
}
```

## Rules

- Unit tests: < 50ms each (no Spring context)
- Slice tests: < 100ms each
- Integration tests: < 500ms each
- Use `@ServiceConnection` (Spring Boot 3.5+) instead of `@DynamicPropertySource`
- Reuse containers across tests with `static`
- `@Transactional` on tests auto-rolls back DB changes
