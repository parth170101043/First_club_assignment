package com.example.FirstClubApp.order;

import com.example.FirstClubApp.common.ResourceNotFoundException;
import com.example.FirstClubApp.tier.TierEvaluationService;
import com.example.FirstClubApp.user.User;
import com.example.FirstClubApp.user.UserService;
import com.example.FirstClubApp.catalog.Product;
import com.example.FirstClubApp.catalog.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

/**
 * Coordinates order persistence, membership checkout benefits, and post-order tier reevaluation.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserService userService;
    private final OrderBenefitService orderBenefitService;
    private final TierEvaluationService tierEvaluationService;
    private final ProductService productService;

    /**
     * Creates the order service.
     *
     * @param orderRepository persistence gateway for completed orders
     * @param userService service that validates the ordering user
     * @param orderBenefitService service that calculates checkout membership benefits
     * @param tierEvaluationService post-order tier reevaluation extension point
     * @return an initialized order service
     * @implNote Used by Spring dependency injection when constructing order components.
     */
    @Autowired
    public OrderService(OrderRepository orderRepository,
                        UserService userService,
                        OrderBenefitService orderBenefitService,
                        TierEvaluationService tierEvaluationService,
                        ProductService productService) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.orderBenefitService = orderBenefitService;
        this.tierEvaluationService = tierEvaluationService;
        this.productService = productService;
    }

    /**
     * Retains the original constructor for focused unit tests.
     */
    public OrderService(OrderRepository orderRepository,
                        UserService userService,
                        OrderBenefitService orderBenefitService,
                        TierEvaluationService tierEvaluationService) {
        this(orderRepository, userService, orderBenefitService, tierEvaluationService, null);
    }

    /**
     * Creates a product-backed order from the current member cart.
     *
     * @param userId ordering member
     * @param lines product IDs and positive quantities
     * @return persisted order with item snapshots and membership benefits
     */
    @Transactional
    public OrderDtos.Response createFromCart(UUID userId, List<CartLine> lines) {
        User user = userService.requireUser(userId);
        List<ResolvedLine> resolved = lines.stream()
            .filter(line -> line.quantity() > 0)
            .map(line -> new ResolvedLine(
                productService.requireActive(line.productId()), line.quantity()))
            .toList();
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("Add at least one product to the cart.");
        }
        BigDecimal total = resolved.stream()
            .map(line -> line.product().getPrice()
                .multiply(BigDecimal.valueOf(line.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        OrderBenefitService.BenefitResult benefits =
            orderBenefitService.evaluate(userId, total, null);
        CustomerOrder order = new CustomerOrder(
            user, total, null, benefits.discountPercent(),
            benefits.discountAmount(), benefits.appliedDiscountPerkCode(),
            benefits.appliedDiscountPerkName(), benefits.deliveryFee(), benefits.finalAmount(),
            benefits.freeDelivery(), benefits.membershipTierCode());
        resolved.forEach(line -> order.addItem(line.product(), line.quantity()));
        CustomerOrder saved = orderRepository.save(order);
        tierEvaluationService.reevaluate(userId);
        return OrderDtos.Response.from(saved);
    }

    public record CartLine(UUID productId, int quantity) {
    }

    private record ResolvedLine(Product product, int quantity) {
    }

    /**
     * Creates an order, snapshots membership benefits, and triggers tier reevaluation.
     *
     * @param request validated order creation request
     * @return persisted order response containing applied checkout benefits
     * @implNote Used by {@link OrderController#create(OrderDtos.CreateRequest)}.
     */
    @Transactional
    public OrderDtos.Response create(OrderDtos.CreateRequest request) {
        User user = userService.requireUser(request.userId());
        String category = trimToNull(request.category());
        OrderBenefitService.BenefitResult benefits = orderBenefitService.evaluate(
            request.userId(), request.totalAmount(), category);
        CustomerOrder order = new CustomerOrder(
            user,
            request.totalAmount(),
            category,
            benefits.discountPercent(),
            benefits.discountAmount(),
            benefits.appliedDiscountPerkCode(),
            benefits.appliedDiscountPerkName(),
            benefits.deliveryFee(),
            benefits.finalAmount(),
            benefits.freeDelivery(),
            benefits.membershipTierCode()
        );
        CustomerOrder saved = orderRepository.save(order);
        tierEvaluationService.reevaluate(request.userId());
        return OrderDtos.Response.from(saved);
    }

    /**
     * Returns one persisted order by identifier.
     *
     * @param orderId order UUID with no default value
     * @return matching order response
     * @implNote Used by {@link OrderController#findById(UUID)}.
     */
    @Transactional(readOnly = true)
    public OrderDtos.Response findById(UUID orderId) {
        CustomerOrder order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Order not found: " + orderId));
        return OrderDtos.Response.from(order);
    }

    /**
     * Returns a user's completed order history.
     *
     * @param userId user UUID with no default value
     * @return orders ordered newest first; defaults to an empty list
     * @implNote Used by {@link OrderController#findForUser(UUID)}.
     */
    @Transactional(readOnly = true)
    public List<OrderDtos.Response> findForUser(UUID userId) {
        userService.requireUser(userId);
        return orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(OrderDtos.Response::from)
            .toList();
    }

    /**
     * Trims optional category text and converts blank input to {@code null}.
     *
     * @param value optional category; defaults to {@code null}
     * @return trimmed category, or {@code null} when absent or blank
     * @implNote Used internally while normalizing order creation input.
     */
    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
