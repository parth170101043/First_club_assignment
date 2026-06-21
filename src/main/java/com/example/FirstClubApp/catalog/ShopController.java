package com.example.FirstClubApp.catalog;

import com.example.FirstClubApp.order.OrderDtos;
import com.example.FirstClubApp.order.OrderService;
import com.example.FirstClubApp.user.User;
import com.example.FirstClubApp.user.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Provides member product browsing, session cart management, and checkout.
 */
@Controller
@SessionAttributes("cart")
public class ShopController {

    private final ProductService productService;
    private final UserService userService;
    private final OrderService orderService;

    public ShopController(ProductService productService,
                          UserService userService,
                          OrderService orderService) {
        this.productService = productService;
        this.userService = userService;
        this.orderService = orderService;
    }

    @ModelAttribute("cart")
    public ShoppingCart cart() {
        return new ShoppingCart();
    }

    @GetMapping("/shop")
    public String shop(@ModelAttribute("cart") ShoppingCart cart, Model model) {
        List<ProductDtos.Response> products = productService.findActive();
        model.addAttribute("products", products);
        model.addAttribute("cartLines", cartLines(cart, products));
        model.addAttribute("cartTotal", cartLines(cart, products).stream()
            .map(CartView::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        return "shop";
    }

    @PostMapping("/cart/add")
    public String add(@RequestParam UUID productId,
                      @RequestParam(defaultValue = "1") int quantity,
                      @ModelAttribute("cart") ShoppingCart cart) {
        productService.requireActive(productId);
        cart.add(productId, quantity);
        return "redirect:/shop#cart";
    }

    @PostMapping("/cart/update")
    public String update(@RequestParam UUID productId,
                         @RequestParam int quantity,
                         @ModelAttribute("cart") ShoppingCart cart) {
        cart.update(productId, quantity);
        return "redirect:/shop#cart";
    }

    @PostMapping("/cart/checkout")
    public String checkout(@ModelAttribute("cart") ShoppingCart cart,
                           Authentication authentication,
                           RedirectAttributes flash) {
        try {
            User user = userService.requireUserByEmail(authentication.getName());
            OrderDtos.Response order = orderService.createFromCart(
                user.getId(),
                cart.getQuantities().entrySet().stream()
                    .map(entry -> new OrderService.CartLine(
                        entry.getKey(), entry.getValue()))
                    .toList());
            cart.clear();
            return "redirect:/orders/" + order.id() + "/confirmation";
        } catch (RuntimeException exception) {
            flash.addFlashAttribute("orderError", exception.getMessage());
            return "redirect:/shop#cart";
        }
    }

    @GetMapping("/orders/{orderId}/confirmation")
    public String confirmation(
        @org.springframework.web.bind.annotation.PathVariable UUID orderId,
        Authentication authentication,
        Model model
    ) {
        User user = userService.requireUserByEmail(authentication.getName());
        OrderDtos.Response order = orderService.findById(orderId);
        if (!order.userId().equals(user.getId())) {
            throw new com.example.FirstClubApp.common.ResourceNotFoundException(
                "Order not found: " + orderId);
        }
        model.addAttribute("order", order);
        return "order-confirmation";
    }

    private List<CartView> cartLines(
        ShoppingCart cart, List<ProductDtos.Response> products) {
        return cart.getQuantities().entrySet().stream()
            .map(entry -> products.stream()
                .filter(product -> product.id().equals(entry.getKey()))
                .findFirst()
                .map(product -> new CartView(
                    product, entry.getValue(),
                    product.price().multiply(BigDecimal.valueOf(entry.getValue()))))
                .orElse(null))
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    public record CartView(
        ProductDtos.Response product,
        int quantity,
        BigDecimal lineTotal
    ) {
    }
}
