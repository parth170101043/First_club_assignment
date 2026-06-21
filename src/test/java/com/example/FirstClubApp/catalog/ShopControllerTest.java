package com.example.FirstClubApp.catalog;

import com.example.FirstClubApp.order.OrderDtos;
import com.example.FirstClubApp.order.OrderService;
import com.example.FirstClubApp.user.User;
import com.example.FirstClubApp.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.example.FirstClubApp.testutil.TestEntityFactory.initialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Verifies browser checkout navigation and session-cart cleanup.
 */
@ExtendWith(MockitoExtension.class)
class ShopControllerTest {

    @Mock
    private ProductService productService;

    @Mock
    private UserService userService;

    @Mock
    private OrderService orderService;

    @Mock
    private Authentication authentication;

    @Test
    void checkoutClearsCartAndRedirectsToConfirmation() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User user = initialize(
            new User("member@example.com", "Member", "User", null), userId);
        ShoppingCart cart = new ShoppingCart();
        cart.add(productId, 2);
        when(authentication.getName()).thenReturn("member@example.com");
        when(userService.requireUserByEmail("member@example.com")).thenReturn(user);
        when(orderService.createFromCart(org.mockito.ArgumentMatchers.eq(userId), anyList()))
            .thenReturn(new OrderDtos.Response(
                orderId, userId, new BigDecimal("200.00"), null,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("250.00"),
                false, null, Instant.parse("2026-06-21T15:00:00Z")));

        ShopController controller = new ShopController(
            productService, userService, orderService);
        String view = controller.checkout(
            cart, authentication, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo(
            "redirect:/orders/" + orderId + "/confirmation");
        assertThat(cart.getQuantities()).isEmpty();
    }
}
