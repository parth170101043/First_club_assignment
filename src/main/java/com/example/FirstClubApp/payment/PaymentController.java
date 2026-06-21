package com.example.FirstClubApp.payment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Exposes user payment method and mock charge REST operations.
 */
@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Creates the payment REST controller.
     *
     * @param paymentService service that owns method and transaction rules
     * @return an initialized controller
     * @implNote Used by Spring MVC during application startup.
     */
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Adds a tokenized payment method for a user.
     *
     * @param userId owner UUID supplied in the URL path
     * @param request validated tokenized method metadata
     * @return created safe payment method response with HTTP 201
     * @implNote Used by payment setup clients.
     */
    @PostMapping("/users/{userId}/payment-methods")
    @PreAuthorize("@accountAuthorization.canAccess(#userId, authentication)")
    @ResponseStatus(HttpStatus.CREATED)
    PaymentDtos.MethodResponse addMethod(
        @PathVariable UUID userId,
        @Valid @RequestBody PaymentDtos.AddMethodRequest request) {
        return paymentService.addMethod(userId, request);
    }

    /**
     * Lists active payment methods for a user.
     *
     * @param userId owner UUID supplied in the URL path
     * @return safe active payment methods; defaults to an empty list
     * @implNote Used by checkout and payment settings clients.
     */
    @GetMapping("/users/{userId}/payment-methods")
    @PreAuthorize("@accountAuthorization.canAccess(#userId, authentication)")
    List<PaymentDtos.MethodResponse> listMethods(@PathVariable UUID userId) {
        return paymentService.listMethods(userId);
    }

    /**
     * Deactivates a user-owned payment method.
     *
     * @param userId owner UUID supplied in the URL path
     * @param methodId payment method UUID supplied in the URL path
     * @return no response body with HTTP 204
     * @implNote Used by payment settings clients.
     */
    @DeleteMapping("/users/{userId}/payment-methods/{methodId}")
    @PreAuthorize("@accountAuthorization.canAccess(#userId, authentication)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteMethod(@PathVariable UUID userId, @PathVariable UUID methodId) {
        paymentService.deleteMethod(userId, methodId);
    }

    /**
     * Creates a synchronous mock payment transaction.
     *
     * @param request validated charge details
     * @return persisted successful or failed transaction response with HTTP 201
     * @implNote Used by subscription and order payment demonstrations.
     */
    @PostMapping("/payments/charge")
    @ResponseStatus(HttpStatus.CREATED)
    PaymentDtos.TransactionResponse charge(
        @Valid @RequestBody PaymentDtos.ChargeRequest request) {
        return paymentService.charge(request);
    }

    /**
     * Lists a user's mock payment transaction history.
     *
     * @param userId user UUID supplied in the URL path
     * @return transactions ordered newest first; defaults to an empty list
     * @implNote Used by payment history and support clients.
     */
    @GetMapping("/users/{userId}/payments")
    @PreAuthorize("@accountAuthorization.canAccess(#userId, authentication)")
    List<PaymentDtos.TransactionResponse> history(@PathVariable UUID userId) {
        return paymentService.history(userId);
    }
}
