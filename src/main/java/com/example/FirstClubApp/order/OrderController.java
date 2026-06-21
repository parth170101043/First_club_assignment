package com.example.FirstClubApp.order;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

/**
 * Exposes REST operations for order creation, lookup, and user order history.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    /**
     * Creates the order REST controller.
     *
     * @param orderService service that owns checkout and order persistence
     * @return an initialized controller
     * @implNote Used by Spring MVC during application startup.
     */
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates a completed order and applies current membership benefits.
     *
     * @param request validated user, amount, and optional category
     * @return persisted order response with HTTP 201
     * @implNote Used by shopping and checkout clients.
     */
    @PostMapping
    @PreAuthorize("@accountAuthorization.canAccess(#request.userId(), authentication)")
    @ResponseStatus(HttpStatus.CREATED)
    OrderDtos.Response create(@Valid @RequestBody OrderDtos.CreateRequest request) {
        return orderService.create(request);
    }

    /**
     * Retrieves one order by UUID.
     *
     * @param orderId order UUID supplied in the URL path
     * @return matching order response
     * @implNote Used by checkout confirmation and support clients.
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("@accountAuthorization.canAccessOrder(#orderId, authentication)")
    OrderDtos.Response findById(@PathVariable UUID orderId) {
        return orderService.findById(orderId);
    }

    /**
     * Retrieves completed orders for one user.
     *
     * @param userId user UUID supplied in the URL path
     * @return orders ordered newest first; defaults to an empty list
     * @implNote Used by order history clients and future behavioral tier diagnostics.
     */
    @GetMapping("/users/{userId}")
    @PreAuthorize("@accountAuthorization.canAccess(#userId, authentication)")
    List<OrderDtos.Response> findForUser(@PathVariable UUID userId) {
        return orderService.findForUser(userId);
    }
}
