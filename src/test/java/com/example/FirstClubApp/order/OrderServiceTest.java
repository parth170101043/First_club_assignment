package com.example.FirstClubApp.order;

import com.example.FirstClubApp.tier.TierEvaluationService;
import com.example.FirstClubApp.user.User;
import com.example.FirstClubApp.user.UserService;
import com.example.FirstClubApp.catalog.Product;
import com.example.FirstClubApp.catalog.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

import static com.example.FirstClubApp.testutil.TestEntityFactory.initialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies order persistence, benefit snapshots, input normalization, and tier reevaluation.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserService userService;

    @Mock
    private OrderBenefitService orderBenefitService;

    @Mock
    private TierEvaluationService tierEvaluationService;

    @Mock
    private ProductService productService;

    private OrderService orderService;

    /**
     * Creates the order service with mocked collaborators.
     *
     * @return no return value
     * @implNote Used by JUnit before each order service test.
     */
    @BeforeEach
    void setUp() {
        orderService = new OrderService(
            orderRepository, userService, orderBenefitService,
            tierEvaluationService, productService);
    }

    /**
     * Verifies order creation persists applied benefits and triggers tier reevaluation.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect the complete checkout workflow.
     */
    @Test
    void createsOrderAndTriggersTierReevaluation() {
        UUID userId = UUID.randomUUID();
        User user = initialize(
            new User("member@example.com", "Member", "User", null), userId);
        OrderDtos.CreateRequest request = new OrderDtos.CreateRequest(
            userId, new BigDecimal("1600.00"), " GROCERY ");
        OrderBenefitService.BenefitResult benefits =
            new OrderBenefitService.BenefitResult(
                new BigDecimal("20.00"), new BigDecimal("320.00"),
                "GOLD_SAVER", "Gold saver", BigDecimal.ZERO,
                new BigDecimal("1280.00"), true, "GOLD");
        when(userService.requireUser(userId)).thenReturn(user);
        when(orderBenefitService.evaluate(
            userId, new BigDecimal("1600.00"), "GROCERY")).thenReturn(benefits);
        when(orderRepository.save(org.mockito.ArgumentMatchers.any(CustomerOrder.class)))
            .thenAnswer(invocation ->
                initialize(invocation.getArgument(0), UUID.randomUUID()));

        OrderDtos.Response response = orderService.create(request);

        assertThat(response.category()).isEqualTo("GROCERY");
        assertThat(response.discountPercent()).isEqualByComparingTo("20.00");
        assertThat(response.deliveryFee()).isZero();
        assertThat(response.finalAmount()).isEqualByComparingTo("1280.00");
        assertThat(response.appliedDiscountPerkCode()).isEqualTo("GOLD_SAVER");
        assertThat(response.appliedDiscountPerkName()).isEqualTo("Gold saver");
        assertThat(response.freeDelivery()).isTrue();
        assertThat(response.membershipTierCode()).isEqualTo("GOLD");
        verify(tierEvaluationService).reevaluate(userId);
    }

    /**
     * Verifies cart checkout uses current catalogue prices and snapshots every line item.
     */
    @Test
    void createsMultiItemOrderFromCart() {
        UUID userId = UUID.randomUUID();
        UUID coffeeId = UUID.randomUUID();
        UUID teaId = UUID.randomUUID();
        User user = initialize(
            new User("member@example.com", "Member", "User", null), userId);
        Product coffee = initialize(new Product(
            "COFFEE", "Coffee", null, "Grocery", new BigDecimal("100.00")), coffeeId);
        Product tea = initialize(new Product(
            "TEA", "Tea", null, "Grocery", new BigDecimal("50.00")), teaId);
        when(userService.requireUser(userId)).thenReturn(user);
        when(productService.requireActive(coffeeId)).thenReturn(coffee);
        when(productService.requireActive(teaId)).thenReturn(tea);
        when(orderBenefitService.evaluate(
            userId, new BigDecimal("250.00"), null))
            .thenReturn(new OrderBenefitService.BenefitResult(
                new BigDecimal("10.00"), new BigDecimal("25.00"),
                new BigDecimal("50.00"), new BigDecimal("275.00"), false, "GOLD"));
        when(orderRepository.save(org.mockito.ArgumentMatchers.any(CustomerOrder.class)))
            .thenAnswer(invocation ->
                initialize(invocation.getArgument(0), UUID.randomUUID()));

        OrderDtos.Response response = orderService.createFromCart(
            userId, List.of(
                new OrderService.CartLine(coffeeId, 2),
                new OrderService.CartLine(teaId, 1)));

        assertThat(response.totalAmount()).isEqualByComparingTo("250.00");
        assertThat(response.deliveryFee()).isEqualByComparingTo("50.00");
        assertThat(response.finalAmount()).isEqualByComparingTo("275.00");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items())
            .extracting(OrderDtos.ItemResponse::name)
            .containsExactly("Coffee", "Tea");
        verify(tierEvaluationService).reevaluate(userId);
    }
}
