---
description: Spring Boot 3.5 CRUD patterns with feature-based architecture, DDD, JPA, and REST controllers.
---

# Spring Boot CRUD Patterns

$ARGUMENTS

## Feature-Based Structure

```
feature/<name>/
  domain/
    model/          # Aggregates, value objects (no Spring annotations)
    repository/     # Interfaces (ports)
  application/
    service/        # @Service, @Transactional use cases
    dto/            # Request/response records
  infrastructure/
    persistence/    # JPA entities, Spring Data adapters
  presentation/
    rest/           # @RestController
```

## Domain Layer

```java
// Aggregate — framework-free
public class Product {
    private final ProductId id;
    private String name;
    private Money price;

    public static Product create(String name, Money price) {
        // validate invariants here
        return new Product(ProductId.generate(), name, price);
    }
}
```

## Application Layer

```java
@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {
    private final ProductRepository repository;
    private final ProductMapper mapper;

    public ProductResponse create(CreateProductRequest request) {
        Product product = Product.create(request.name(), new Money(request.price()));
        repository.save(product);
        return mapper.toResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toResponse);
    }
}
```

## Presentation Layer

```java
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(201).body(service.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

## Rules

- ❌ Never expose JPA entities in controllers (lazy-loading + serialization issues)
- ❌ Never use field injection
- ❌ Never put business logic in controllers
- ✅ DTOs as Java records (immutable)
- ✅ Validate input with `@Valid`
- ✅ Handle errors with `@ControllerAdvice` or `ResponseStatusException`
- ✅ Paginate list endpoints
- ✅ Schema migrations with Flyway/Liquibase

## Testing

```java
@DataJpaTest         // repository layer
@WebMvcTest(ProductController.class)  // controller layer
@SpringBootTest      // integration (use sparingly)
```
