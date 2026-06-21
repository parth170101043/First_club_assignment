package com.example.FirstClubApp.catalog;

import com.example.FirstClubApp.common.ConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.example.FirstClubApp.testutil.TestEntityFactory.initialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies catalogue uniqueness and soft removal without inventory behavior.
 */
class ProductServiceTest {

    @Test
    void createsNormalizedUniqueProduct() {
        ProductRepository repository = mock(ProductRepository.class);
        ProductService service = new ProductService(repository);
        when(repository.save(any(Product.class))).thenAnswer(invocation ->
            initialize(invocation.getArgument(0), UUID.randomUUID()));

        ProductDtos.Response response = service.create(new ProductDtos.CreateRequest(
            " coffee_1 ", " Coffee Hamper ", "A hamper", "Grocery",
            new BigDecimal("499.00")));

        assertThat(response.sku()).isEqualTo("COFFEE_1");
        assertThat(response.name()).isEqualTo("Coffee Hamper");
        verify(repository).existsBySkuIgnoreCase("COFFEE_1");
        verify(repository).existsByNameIgnoreCase("Coffee Hamper");
    }

    @Test
    void rejectsDuplicateProductName() {
        ProductRepository repository = mock(ProductRepository.class);
        ProductService service = new ProductService(repository);
        when(repository.existsByNameIgnoreCase("Coffee Hamper")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new ProductDtos.CreateRequest(
            "COFFEE_2", "Coffee Hamper", null, null, BigDecimal.ONE)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("name");
    }

    @Test
    void removalHidesProductWithoutDeletingHistory() {
        UUID id = UUID.randomUUID();
        Product product = initialize(new Product(
            "COFFEE_1", "Coffee", null, null, BigDecimal.TEN), id);
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(product));

        new ProductService(repository).remove(id);

        assertThat(product.isActive()).isFalse();
    }
}
