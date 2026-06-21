package com.example.FirstClubApp.payment;

import com.example.FirstClubApp.common.ConflictException;
import com.example.FirstClubApp.common.ResourceNotFoundException;
import com.example.FirstClubApp.user.User;
import com.example.FirstClubApp.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Manages tokenized payment methods and synchronous mock charge transactions.
 */
@Service
public class PaymentService {

    private final UserRepository userRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final MockPaymentProcessor mockPaymentProcessor;

    /**
     * Creates the payment service.
     *
     * @param userRepository persistence gateway used to validate users
     * @param paymentMethodRepository persistence gateway for tokenized methods
     * @param paymentTransactionRepository persistence gateway for transaction history
     * @param mockPaymentProcessor deterministic mock provider
     * @return an initialized payment service
     * @implNote Used by Spring dependency injection when constructing payment components.
     */
    public PaymentService(UserRepository userRepository,
                          PaymentMethodRepository paymentMethodRepository,
                          PaymentTransactionRepository paymentTransactionRepository,
                          MockPaymentProcessor mockPaymentProcessor) {
        this.userRepository = userRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.mockPaymentProcessor = mockPaymentProcessor;
    }

    /**
     * Adds a tokenized payment method and maintains one default method per user.
     *
     * @param userId owner UUID with no default value
     * @param request validated token and display metadata
     * @return created safe payment method response
     * @implNote Used by {@link PaymentController#addMethod(UUID, PaymentDtos.AddMethodRequest)}.
     */
    @Transactional
    public PaymentDtos.MethodResponse addMethod(
        UUID userId,
        PaymentDtos.AddMethodRequest request) {
        User user = requireUser(userId);
        String providerToken = request.providerToken().trim();
        if (paymentMethodRepository.existsByProviderToken(providerToken)) {
            throw new ConflictException("This payment provider token is already registered.");
        }
        List<UserPaymentMethod> existing =
            paymentMethodRepository.findAllByUserIdAndActiveTrueOrderByDefaultMethodDescCreatedAtAsc(
                userId);
        boolean makeDefault = Boolean.TRUE.equals(request.defaultMethod()) || existing.isEmpty();
        if (makeDefault) {
            existing.forEach(UserPaymentMethod::removeDefault);
            paymentMethodRepository.flush();
        }
        UserPaymentMethod method = new UserPaymentMethod(
            user,
            request.type(),
            providerToken,
            request.displayName().trim(),
            trimToNull(request.brand()),
            trimToNull(request.lastFour()),
            makeDefault
        );
        return PaymentDtos.MethodResponse.from(paymentMethodRepository.save(method));
    }

    /**
     * Lists active tokenized payment methods for a user.
     *
     * @param userId owner UUID with no default value
     * @return safe methods ordered with the default first; defaults to an empty list
     * @implNote Used by {@link PaymentController#listMethods(UUID)}.
     */
    @Transactional(readOnly = true)
    public List<PaymentDtos.MethodResponse> listMethods(UUID userId) {
        requireUser(userId);
        return paymentMethodRepository
            .findAllByUserIdAndActiveTrueOrderByDefaultMethodDescCreatedAtAsc(userId)
            .stream()
            .map(PaymentDtos.MethodResponse::from)
            .toList();
    }

    /**
     * Deactivates a user-owned payment method and promotes another active method when required.
     *
     * @param userId owner UUID with no default value
     * @param methodId payment method UUID with no default value
     * @return no return value
     * @implNote Used by {@link PaymentController#deleteMethod(UUID, UUID)}.
     */
    @Transactional
    public void deleteMethod(UUID userId, UUID methodId) {
        UserPaymentMethod method = requireActiveMethod(userId, methodId);
        boolean wasDefault = method.isDefaultMethod();
        method.deactivate();
        if (wasDefault) {
            paymentMethodRepository
                .findAllByUserIdAndActiveTrueOrderByDefaultMethodDescCreatedAtAsc(userId)
                .stream()
                .filter(candidate -> !candidate.getId().equals(methodId))
                .findFirst()
                .ifPresent(UserPaymentMethod::makeDefault);
        }
    }

    /**
     * Charges a user-owned active method through the mock processor and stores the result.
     *
     * @param request validated mock charge request
     * @return persisted transaction response for success or failure
     * @implNote Used by {@link PaymentController#charge(PaymentDtos.ChargeRequest)}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentDtos.TransactionResponse charge(PaymentDtos.ChargeRequest request) {
        User user = requireUser(request.userId());
        UserPaymentMethod method =
            requireActiveMethod(request.userId(), request.paymentMethodId());
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);
        MockPaymentProcessor.ProcessorResult result = mockPaymentProcessor.charge(
            method.getProviderToken(), request.amount(), currency);
        PaymentTransaction transaction = new PaymentTransaction(
            user,
            method,
            request.amount(),
            currency,
            request.purpose().trim(),
            result.status(),
            result.providerReference(),
            result.failureReason()
        );
        return PaymentDtos.TransactionResponse.from(
            paymentTransactionRepository.save(transaction));
    }

    /**
     * Lists a user's successful and failed mock payment attempts.
     *
     * @param userId owner UUID with no default value
     * @return transactions ordered newest first; defaults to an empty list
     * @implNote Used by {@link PaymentController#history(UUID)}.
     */
    @Transactional(readOnly = true)
    public List<PaymentDtos.TransactionResponse> history(UUID userId) {
        requireUser(userId);
        return paymentTransactionRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(PaymentDtos.TransactionResponse::from)
            .toList();
    }

    /**
     * Loads a user or raises a domain-level not-found error.
     *
     * @param userId user UUID with no default value
     * @return matching persistent user
     * @implNote Used internally by all payment operations.
     */
    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    /**
     * Loads an active payment method and verifies ownership.
     *
     * @param userId expected owner UUID
     * @param methodId payment method UUID
     * @return matching active method
     * @implNote Used internally by method deletion and mock charges.
     */
    private UserPaymentMethod requireActiveMethod(UUID userId, UUID methodId) {
        return paymentMethodRepository.findByIdAndUserIdAndActiveTrue(methodId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Active payment method not found: " + methodId));
    }

    /**
     * Trims optional metadata and converts blank input to {@code null}.
     *
     * @param value optional source text; defaults to {@code null}
     * @return trimmed text, or {@code null} when absent or blank
     * @implNote Used internally while normalizing brand and masked suffix metadata.
     */
    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
