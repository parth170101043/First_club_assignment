package com.example.FirstClubApp.catalog;

import com.example.FirstClubApp.common.ConflictException;
import com.example.FirstClubApp.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Manages the active product catalogue; inventory is intentionally out of scope.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductDtos.Response create(ProductDtos.CreateRequest request) {
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        String name = request.name().trim();
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw new ConflictException("A product with this SKU already exists.");
        }
        if (productRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A product with this name already exists.");
        }
        if (request.price() == null || request.price().signum() < 0) {
            throw new ConflictException("Product price cannot be negative.");
        }
        Product product = new Product(
            sku, name, trim(request.description()), trim(request.category()), request.price());
        return ProductDtos.Response.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductDtos.Response> findActive() {
        return productRepository.findAllByActiveTrueOrderByNameAsc().stream()
            .map(ProductDtos.Response::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductDtos.Response> findAll() {
        return productRepository.findAllByOrderByNameAsc().stream()
            .map(ProductDtos.Response::from)
            .toList();
    }

    public Product requireActive(UUID productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Product not found: " + productId));
        if (!product.isActive()) {
            throw new ConflictException("This product is no longer available.");
        }
        return product;
    }

    @Transactional
    public void remove(UUID productId) {
        requireActive(productId).deactivate();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
