package com.example.FirstClubApp.catalog;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Groups catalogue forms and responses.
 */
public final class ProductDtos {

    private ProductDtos() {
    }

    public record CreateRequest(
        String sku,
        String name,
        String description,
        String category,
        BigDecimal price
    ) {
    }

    public record Response(
        UUID id,
        String sku,
        String name,
        String description,
        String category,
        BigDecimal price,
        boolean active
    ) {
        public static Response from(Product product) {
            return new Response(
                product.getId(), product.getSku(), product.getName(),
                product.getDescription(), product.getCategory(),
                product.getPrice(), product.isActive());
        }
    }
}
