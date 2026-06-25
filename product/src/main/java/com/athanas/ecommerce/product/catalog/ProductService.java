package com.athanas.ecommerce.product.catalog;

import com.athanas.ecommerce.product.catalog.dto.CreateProductRequest;
import com.athanas.ecommerce.product.catalog.dto.ProductDetail;
import com.athanas.ecommerce.product.catalog.dto.ProductSummary;
import com.athanas.ecommerce.product.catalog.dto.UpdateProductRequest;
import com.athanas.ecommerce.product.catalog.error.CategoryNotFoundException;
import com.athanas.ecommerce.product.catalog.error.ProductAccessDeniedException;
import com.athanas.ecommerce.product.catalog.error.ProductNotFoundException;
import com.athanas.ecommerce.product.catalog.error.SkuAlreadyExistsException;
import com.athanas.ecommerce.product.category.Category;
import com.athanas.ecommerce.product.category.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    public Page<ProductSummary> listPublic(UUID categoryId, Pageable pageable) {
        Page<Product> page = (categoryId != null)
                ? productRepository.findByStatusAndCategoryId(ProductStatus.ACTIVE, categoryId, pageable)
                : productRepository.findByStatus(ProductStatus.ACTIVE, pageable);

        List<UUID> productIds = page.getContent().stream().map(Product::getId).toList();
        if (productIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> categoryIds = page.getContent().stream()
                .map(Product::getCategoryId).distinct().toList();

        Map<UUID, String> categoryNames = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        Map<UUID, String> primaryImages = productImageRepository
                .findPrimaryByProductIds(productIds).stream()
                .collect(Collectors.toMap(img -> img.getProduct().getId(), ProductImage::getUrl));

        return page.map(p -> productMapper.toSummary(
                p,
                categoryNames.get(p.getCategoryId()),
                primaryImages.get(p.getId())
        ));
    }

    public ProductDetail getPublicById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ProductNotFoundException(id);
        }
        Category category = categoryRepository.findById(product.getCategoryId())
                .orElseThrow(() -> new CategoryNotFoundException(product.getCategoryId()));
        return productMapper.toDetail(product, category);
    }

    public ProductDetail getForOwnerOrAdmin(UUID id, UUID callerId, Set<String> callerRoles) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        checkOwnership(product, callerId, callerRoles);
        Category category = categoryRepository.findById(product.getCategoryId())
                .orElseThrow(() -> new CategoryNotFoundException(product.getCategoryId()));
        return productMapper.toDetail(product, category);
    }

    @Transactional
    public ProductDetail create(CreateProductRequest req, UUID callerId, Set<String> callerRoles) {
        if (!categoryRepository.existsById(req.categoryId())) {
            throw new CategoryNotFoundException(req.categoryId());
        }
        if (productRepository.existsBySku(req.sku())) {
            throw new SkuAlreadyExistsException(req.sku());
        }
        UUID sellerId = (callerRoles.contains("ADMIN") && req.sellerId() != null)
                ? req.sellerId() : callerId;

        Product product = productMapper.toEntity(req, sellerId);
        product = productRepository.save(product);

        Category category = categoryRepository.findById(product.getCategoryId()).orElseThrow();
        return productMapper.toDetail(product, category);
    }

    @Transactional
    public ProductDetail update(UUID id, UpdateProductRequest req, UUID callerId, Set<String> callerRoles) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        checkOwnership(product, callerId, callerRoles);

        if (req.categoryId() != null && !req.categoryId().equals(product.getCategoryId())) {
            if (!categoryRepository.existsById(req.categoryId())) {
                throw new CategoryNotFoundException(req.categoryId());
            }
            product.setCategoryId(req.categoryId());
        }
        if (req.name() != null) product.setName(req.name());
        if (req.description() != null) product.setDescription(req.description());
        if (req.basePrice() != null) product.setBasePrice(req.basePrice());
        if (req.status() != null) product.setStatus(req.status());

        productRepository.save(product);
        Category category = categoryRepository.findById(product.getCategoryId()).orElseThrow();
        return productMapper.toDetail(product, category);
    }

    @Transactional
    public void softDelete(UUID id, UUID callerId, Set<String> callerRoles) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        checkOwnership(product, callerId, callerRoles);
        product.setDeletedAt(Instant.now());
        productRepository.save(product);
    }

    private void checkOwnership(Product product, UUID callerId, Set<String> callerRoles) {
        if (callerRoles.contains("ADMIN")) return;
        if (!product.getSellerId().equals(callerId)) {
            throw new ProductAccessDeniedException(product.getId(), callerId);
        }
    }
}
