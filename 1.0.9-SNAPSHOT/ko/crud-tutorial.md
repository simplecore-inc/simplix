# CRUD Tutorial

SimpliX를 사용하여 Entity, Repository, Service, Controller, DTO를 구현하는 튜토리얼입니다.

## 목차

- [1. Entity 구현](#1-entity-구현)
- [2. Repository 구현](#2-repository-구현)
- [3. DTO 구현](#3-dto-구현)
- [4. Service 구현](#4-service-구현)
- [5. Controller 구현](#5-controller-구현)
- [6. API 테스트](#6-api-테스트)

---

## 1. Entity 구현

### Product.java

```java
package com.example.myapp.domain.entity;

import dev.simplecore.simplix.core.hibernate.UuidV7Generator;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;

@Entity
@Table(
    name = "products",
    indexes = {
        @Index(name = "idx_product_name", columnList = "name"),
        @Index(name = "idx_product_category", columnList = "category")
    }
)
@Comment("Product catalog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID, generator = "uuid-v7")
    @UuidV7Generator
    @Column(name = "product_id", nullable = false, updatable = false)
    private String productId;

    @Column(name = "name", nullable = false, length = 200)
    @Comment("Product name")
    private String name;

    @Column(name = "description", length = 2000)
    @Comment("Product description")
    private String description;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    @Comment("Product price")
    private BigDecimal price;

    @Column(name = "category", length = 100)
    @Comment("Product category")
    private String category;

    @Column(name = "stock_quantity", nullable = false)
    @Comment("Available stock quantity")
    @Builder.Default
    private Integer stockQuantity = 0;

    @Column(name = "is_active", nullable = false)
    @Comment("Product active status")
    @Builder.Default
    private Boolean isActive = true;

    // SimpliXBaseEntity 구현
    @Override
    public String getId() {
        return productId;
    }

    @Override
    public void setId(String id) {
        this.productId = id;
    }
}
```

### 엔티티 설계 포인트

| 항목 | 설명 |
|------|------|
| **@UuidV7Generator** | 시간 순서가 보장되는 UUID v7 생성 |
| **BaseEntity 상속** | 공통 audit 필드 (createdAt, updatedAt, version) 포함 |
| **@Comment** | DDL에 컬럼 설명 추가 (문서화 목적) |
| **@Builder.Default** | 기본값이 있는 필드의 빌더 패턴 지원 |

---

## 2. Repository 구현

### ProductRepository.java

```java
package com.example.myapp.domain.repository;

import com.example.myapp.domain.entity.Product;
import dev.simplecore.simplix.core.repository.SimpliXBaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends SimpliXBaseRepository<Product, String> {

    // 이름으로 검색 (단일)
    Optional<Product> findByName(String name);

    // 카테고리로 검색 (목록)
    List<Product> findByCategory(String category);

    // 활성 상품만 검색
    List<Product> findByIsActiveTrue();

    // 카테고리별 활성 상품 (페이징)
    Page<Product> findByCategoryAndIsActiveTrue(String category, Pageable pageable);

    // 이름 포함 검색 (대소문자 무시)
    List<Product> findByNameContainingIgnoreCase(String keyword);

    // 재고 있는 상품
    List<Product> findByStockQuantityGreaterThan(Integer quantity);

    // 존재 여부 확인
    boolean existsByName(String name);
}
```

### SimpliXBaseRepository 제공 메서드

`SimpliXBaseRepository`는 `JpaRepository`와 `JpaSpecificationExecutor`를 확장합니다:

```java
// 기본 CRUD
save(entity)
saveAll(entities)
findById(id)
findAll()
findAllById(ids)
deleteById(id)
delete(entity)
deleteAll(entities)
count()
existsById(id)

// 페이징/정렬
findAll(Pageable)
findAll(Sort)

// Specification 기반 동적 쿼리
findAll(Specification)
findAll(Specification, Pageable)
count(Specification)
```

---

## 3. DTO 구현

### ProductDTOs.java

하나의 파일에 관련 DTO를 모두 정의하는 패턴:

```java
package com.example.myapp.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.Instant;

public class ProductDTOs {

    // ==========================================
    // 검색 DTO
    // ==========================================

    @Data
    @Schema(description = "Product search criteria")
    public static class ProductSearchDTO {

        @Schema(description = "Product name keyword")
        private String name;

        @Schema(description = "Category filter")
        private String category;

        @Schema(description = "Active status filter")
        private Boolean isActive;

        @Schema(description = "Minimum price")
        private BigDecimal minPrice;

        @Schema(description = "Maximum price")
        private BigDecimal maxPrice;
    }

    // ==========================================
    // 생성 DTO
    // ==========================================

    @Data
    @Schema(description = "Product creation request")
    public static class ProductCreateDTO {

        @Schema(description = "Product name", example = "MacBook Pro 14")
        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be less than 200 characters")
        private String name;

        @Schema(description = "Product description")
        @Size(max = 2000, message = "Description must be less than 2000 characters")
        private String description;

        @Schema(description = "Product price", example = "2499000")
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0", message = "Price must be positive")
        private BigDecimal price;

        @Schema(description = "Product category", example = "Electronics")
        @Size(max = 100)
        private String category;

        @Schema(description = "Initial stock quantity", example = "100")
        @Min(value = 0, message = "Stock quantity must be non-negative")
        private Integer stockQuantity;
    }

    // ==========================================
    // 수정 DTO
    // ==========================================

    @Data
    @EqualsAndHashCode(callSuper = true)
    @Schema(description = "Product update request")
    public static class ProductUpdateDTO extends ProductCreateDTO {

        @Schema(description = "Product ID")
        @NotBlank(message = "Product ID is required")
        private String productId;

        @Schema(description = "Active status")
        private Boolean isActive;
    }

    // ==========================================
    // 목록 조회 DTO (간략 정보)
    // ==========================================

    @Data
    @Schema(description = "Product list item")
    public static class ProductListDTO {

        @Schema(description = "Product ID")
        private String productId;

        @Schema(description = "Product name")
        private String name;

        @Schema(description = "Product price")
        private BigDecimal price;

        @Schema(description = "Category")
        private String category;

        @Schema(description = "Stock quantity")
        private Integer stockQuantity;

        @Schema(description = "Active status")
        private Boolean isActive;
    }

    // ==========================================
    // 상세 조회 DTO (전체 정보)
    // ==========================================

    @Data
    @Schema(description = "Product detail")
    public static class ProductDetailDTO {

        @Schema(description = "Product ID")
        private String productId;

        @Schema(description = "Product name")
        private String name;

        @Schema(description = "Product description")
        private String description;

        @Schema(description = "Product price")
        private BigDecimal price;

        @Schema(description = "Category")
        private String category;

        @Schema(description = "Stock quantity")
        private Integer stockQuantity;

        @Schema(description = "Active status")
        private Boolean isActive;

        // Audit fields
        @Schema(description = "Created by")
        private String createdBy;

        @Schema(description = "Created at")
        private Instant createdAt;

        @Schema(description = "Updated by")
        private String updatedBy;

        @Schema(description = "Updated at")
        private Instant updatedAt;
    }

    // ==========================================
    // 일괄 처리 DTO
    // ==========================================

    @Data
    @Schema(description = "Batch delete request")
    public static class ProductBatchDeleteDTO {

        @Schema(description = "Product IDs to delete")
        @NotEmpty(message = "At least one product ID is required")
        private java.util.List<String> productIds;
    }
}
```

### DTO 설계 원칙

| DTO 타입 | 용도 | 특징 |
|----------|------|------|
| **SearchDTO** | 검색 조건 | 모든 필드 Optional |
| **CreateDTO** | 생성 요청 | 필수 필드 검증 |
| **UpdateDTO** | 수정 요청 | CreateDTO 상속 + ID 필드 |
| **ListDTO** | 목록 응답 | 필수 정보만 포함 |
| **DetailDTO** | 상세 응답 | 전체 정보 + audit 필드 |

---

## 4. Service 구현

### ProductService.java

```java
package com.example.myapp.service;

import com.example.myapp.domain.entity.Product;
import com.example.myapp.domain.repository.ProductRepository;
import com.example.myapp.web.dto.ProductDTOs.*;
import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import dev.simplecore.simplix.web.service.SimpliXBaseService;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductService extends SimpliXBaseService<Product, String> {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
        this.productRepository = repository;
    }

    // ==========================================
    // Create
    // ==========================================

    @Transactional
    public ProductDetailDTO create(ProductCreateDTO dto) {
        // 중복 검사
        if (productRepository.existsByName(dto.getName())) {
            throw new SimpliXGeneralException(
                ErrorCode.GEN_CONFLICT,
                "Product with this name already exists"
            );
        }

        Product entity = new Product();
        modelMapper.map(dto, entity);

        if (dto.getStockQuantity() == null) {
            entity.setStockQuantity(0);
        }
        entity.setIsActive(true);

        Product saved = saveAndFlush(entity);
        return modelMapper.map(saved, ProductDetailDTO.class);
    }

    // ==========================================
    // Read
    // ==========================================

    public ProductDetailDTO getById(String productId) {
        return findById(productId)
            .map(entity -> modelMapper.map(entity, ProductDetailDTO.class))
            .orElseThrow(() -> new SimpliXGeneralException(
                ErrorCode.GEN_NOT_FOUND,
                "Product not found"
            ));
    }

    public Page<ProductListDTO> search(ProductSearchDTO searchDto, Pageable pageable) {
        Specification<Product> spec = buildSpecification(searchDto);
        Page<Product> products = findAll(spec, pageable);
        return products.map(entity -> modelMapper.map(entity, ProductListDTO.class));
    }

    public List<ProductListDTO> findByCategory(String category) {
        return productRepository.findByCategory(category)
            .stream()
            .map(entity -> modelMapper.map(entity, ProductListDTO.class))
            .toList();
    }

    // ==========================================
    // Update
    // ==========================================

    @Transactional
    public ProductDetailDTO update(String productId, ProductUpdateDTO dto) {
        Product entity = findById(productId)
            .orElseThrow(() -> new SimpliXGeneralException(
                ErrorCode.GEN_NOT_FOUND,
                "Product not found"
            ));

        // ID 불일치 검사
        if (!productId.equals(dto.getProductId())) {
            throw new SimpliXGeneralException(
                ErrorCode.GEN_CONFLICT,
                "Product ID mismatch"
            );
        }

        modelMapper.map(dto, entity);
        Product saved = saveAndFlush(entity);
        return modelMapper.map(saved, ProductDetailDTO.class);
    }

    @Transactional
    public void updateStock(String productId, Integer quantity) {
        Product entity = findById(productId)
            .orElseThrow(() -> new SimpliXGeneralException(
                ErrorCode.GEN_NOT_FOUND,
                "Product not found"
            ));

        entity.setStockQuantity(quantity);
        save(entity);
    }

    // ==========================================
    // Delete
    // ==========================================

    @Transactional
    public void delete(String productId) {
        if (!existsById(productId)) {
            throw new SimpliXGeneralException(
                ErrorCode.GEN_NOT_FOUND,
                "Product not found"
            );
        }
        deleteById(productId);
    }

    @Transactional
    public void batchDelete(List<String> productIds) {
        deleteAllByIds(productIds);
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private Specification<Product> buildSpecification(ProductSearchDTO dto) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (StringUtils.hasText(dto.getName())) {
                predicates.add(cb.like(
                    cb.lower(root.get("name")),
                    "%" + dto.getName().toLowerCase() + "%"
                ));
            }

            if (StringUtils.hasText(dto.getCategory())) {
                predicates.add(cb.equal(root.get("category"), dto.getCategory()));
            }

            if (dto.getIsActive() != null) {
                predicates.add(cb.equal(root.get("isActive"), dto.getIsActive()));
            }

            if (dto.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), dto.getMinPrice()));
            }

            if (dto.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), dto.getMaxPrice()));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    public boolean hasOwnerPermission(String permission, String productId, Object dto) {
        // 필요시 소유자 권한 검사 구현
        return true;
    }
}
```

### SimpliXBaseService 제공 메서드

```java
// 기본 CRUD
save(entity)
saveAndFlush(entity)
saveAll(entities)
findById(id)
findById(id, projectionClass)  // Projection 지원
findAll()
findAll(pageable)
findAll(spec)
findAll(spec, pageable)
existsById(id)
deleteById(id)
delete(entity)
deleteAllByIds(ids)
count()

// ModelMapper 자동 주입
protected ModelMapper modelMapper;
```

---

## 5. Controller 구현

### ProductController.java

```java
package com.example.myapp.web.controller;

import com.example.myapp.domain.entity.Product;
import com.example.myapp.service.ProductService;
import com.example.myapp.web.dto.ProductDTOs.*;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.controller.SimpliXBaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product management APIs")
public class ProductController extends SimpliXBaseController<Product, String> {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        super(productService);
        this.productService = productService;
    }

    // ==========================================
    // Create
    // ==========================================

    @PostMapping
    @Operation(summary = "Create product", description = "Creates a new product")
    public SimpliXApiResponse<ProductDetailDTO> create(
            @RequestBody @Validated ProductCreateDTO dto) {
        return SimpliXApiResponse.success(productService.create(dto));
    }

    // ==========================================
    // Read
    // ==========================================

    @GetMapping("/{productId}")
    @Operation(summary = "Get product", description = "Retrieves a product by ID")
    public SimpliXApiResponse<ProductDetailDTO> get(
            @Parameter(description = "Product ID")
            @PathVariable String productId) {
        return SimpliXApiResponse.success(productService.getById(productId));
    }

    @GetMapping
    @Operation(summary = "Search products", description = "Search products with filters")
    public SimpliXApiResponse<Page<ProductListDTO>> search(
            @ModelAttribute ProductSearchDTO searchDto,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return SimpliXApiResponse.success(productService.search(searchDto, pageable));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "Get by category", description = "Get products by category")
    public SimpliXApiResponse<List<ProductListDTO>> getByCategory(
            @Parameter(description = "Category name")
            @PathVariable String category) {
        return SimpliXApiResponse.success(productService.findByCategory(category));
    }

    // ==========================================
    // Update
    // ==========================================

    @PutMapping("/{productId}")
    @Operation(summary = "Update product", description = "Updates an existing product")
    public SimpliXApiResponse<ProductDetailDTO> update(
            @Parameter(description = "Product ID")
            @PathVariable String productId,
            @RequestBody @Validated ProductUpdateDTO dto) {
        return SimpliXApiResponse.success(productService.update(productId, dto));
    }

    @PatchMapping("/{productId}/stock")
    @Operation(summary = "Update stock", description = "Updates product stock quantity")
    public SimpliXApiResponse<Void> updateStock(
            @Parameter(description = "Product ID")
            @PathVariable String productId,
            @Parameter(description = "New stock quantity")
            @RequestParam Integer quantity) {
        productService.updateStock(productId, quantity);
        return SimpliXApiResponse.success(null, "Stock updated successfully");
    }

    // ==========================================
    // Delete
    // ==========================================

    @DeleteMapping("/{productId}")
    @Operation(summary = "Delete product", description = "Deletes a product by ID")
    public SimpliXApiResponse<Void> delete(
            @Parameter(description = "Product ID")
            @PathVariable String productId) {
        productService.delete(productId);
        return SimpliXApiResponse.success(null, "Product deleted successfully");
    }

    @DeleteMapping("/batch")
    @Operation(summary = "Batch delete", description = "Deletes multiple products")
    public SimpliXApiResponse<Void> batchDelete(
            @RequestBody @Validated ProductBatchDeleteDTO dto) {
        productService.batchDelete(dto.getProductIds());
        return SimpliXApiResponse.success(null, "Products deleted successfully");
    }
}
```

### SimpliXApiResponse 사용법

```java
// 성공 응답 (데이터 포함)
SimpliXApiResponse.success(data)

// 성공 응답 (메시지 포함)
SimpliXApiResponse.success(data, "Operation successful")

// 실패 응답
SimpliXApiResponse.failure(data, "Operation failed")

// 에러 응답
SimpliXApiResponse.error("Error message")
```

응답 JSON 구조:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": { ... },
  "errorCode": null,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

## 6. API 테스트

### Swagger UI 사용

http://localhost:8080/swagger-ui.html 에서 API를 테스트할 수 있습니다.

### cURL 예시

**상품 생성:**

```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MacBook Pro 14",
    "description": "Apple M3 Pro chip",
    "price": 2499000,
    "category": "Electronics",
    "stockQuantity": 50
  }'
```

**상품 목록 조회:**

```bash
curl "http://localhost:8080/api/v1/products?category=Electronics&page=0&size=10"
```

**상품 수정:**

```bash
curl -X PUT http://localhost:8080/api/v1/products/{productId} \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "{productId}",
    "name": "MacBook Pro 14 (Updated)",
    "price": 2399000,
    "category": "Electronics",
    "stockQuantity": 45,
    "isActive": true
  }'
```

**상품 삭제:**

```bash
curl -X DELETE http://localhost:8080/api/v1/products/{productId}
```

---

## 다음 단계

- [Authentication Guide](/ko/auth/getting-started.md) - JWE 토큰 인증 추가
- [Exception Handler Guide](/ko/starter/exception-handler.md) - 예외 처리 커스터마이징
- [Tree Structure Guide](/ko/core/tree-structure.md) - 트리 구조 엔티티 구현