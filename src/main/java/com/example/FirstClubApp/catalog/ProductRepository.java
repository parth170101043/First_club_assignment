package com.example.FirstClubApp.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Provides product catalogue persistence and uniqueness checks.
 */
public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsByNameIgnoreCase(String name);

    List<Product> findAllByActiveTrueOrderByNameAsc();

    List<Product> findAllByOrderByNameAsc();
}
