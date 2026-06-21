package com.example.FirstClubApp.catalog;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores a lightweight product/quantity cart in the authenticated browser session.
 */
public class ShoppingCart implements Serializable {

    private final Map<UUID, Integer> quantities = new LinkedHashMap<>();

    public void add(UUID productId, int quantity) {
        quantities.merge(productId, Math.max(quantity, 1), Integer::sum);
    }

    public void update(UUID productId, int quantity) {
        if (quantity <= 0) {
            quantities.remove(productId);
        } else {
            quantities.put(productId, quantity);
        }
    }

    public void clear() {
        quantities.clear();
    }

    public Map<UUID, Integer> getQuantities() {
        return Map.copyOf(quantities);
    }
}
