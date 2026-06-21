package com.example.FirstClubApp.catalog;

import com.example.FirstClubApp.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Stores a purchasable catalogue item without inventory tracking.
 */
@Entity
@Table(name = "products")
public class Product extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    @Column(nullable = false, unique = true, length = 160)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 100)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active = true;

    protected Product() {
    }

    public Product(String sku, String name, String description,
                   String category, BigDecimal price) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }
}
