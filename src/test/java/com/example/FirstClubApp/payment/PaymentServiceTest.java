package com.example.FirstClubApp.payment;

import com.example.FirstClubApp.common.ConflictException;
import com.example.FirstClubApp.common.ResourceNotFoundException;
import com.example.FirstClubApp.user.User;
import com.example.FirstClubApp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.FirstClubApp.testutil.TestEntityFactory.initialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies payment method ownership, default handling, removal, mock charges, and history.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private MockPaymentProcessor mockPaymentProcessor;

    private PaymentService paymentService;

    /**
     * Creates the payment service with mocked persistence and provider dependencies.
     *
     * @return no return value
     * @implNote Used by JUnit before each payment service test.
     */
    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
            userRepository,
            paymentMethodRepository,
            paymentTransactionRepository,
            mockPaymentProcessor
        );
    }

    /**
     * Verifies the first payment method automatically becomes the user's default.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect convenient first-method setup.
     */
    @Test
    void makesFirstPaymentMethodDefault() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(paymentMethodRepository.existsByProviderToken("tok_card")).thenReturn(false);
        when(paymentMethodRepository
            .findAllByUserIdAndActiveTrueOrderByDefaultMethodDescCreatedAtAsc(USER_ID))
            .thenReturn(List.of());
        when(paymentMethodRepository.save(
            org.mockito.ArgumentMatchers.any(UserPaymentMethod.class)))
            .thenAnswer(invocation ->
                initialize(invocation.getArgument(0), UUID.randomUUID()));

        PaymentDtos.MethodResponse response = paymentService.addMethod(
            USER_ID,
            new PaymentDtos.AddMethodRequest(
                PaymentMethodType.CARD, "tok_card", "Personal card",
                "VISA", "4242", false)
        );

        assertThat(response.defaultMethod()).isTrue();
        assertThat(response.lastFour()).isEqualTo("4242");
    }

    /**
     * Verifies duplicate provider tokens are rejected before persistence.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect token uniqueness.
     */
    @Test
    void rejectsDuplicateProviderToken() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(paymentMethodRepository.existsByProviderToken("tok_card")).thenReturn(true);

        assertThatThrownBy(() -> paymentService.addMethod(
            USER_ID,
            new PaymentDtos.AddMethodRequest(
                PaymentMethodType.CARD, "tok_card", "Card", "VISA", "4242", false)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already registered");
        verify(paymentMethodRepository, never())
            .save(org.mockito.ArgumentMatchers.any());
    }

    /**
     * Verifies deleting the default method promotes another active method.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect default-method continuity.
     */
    @Test
    void promotesAnotherMethodWhenDefaultIsDeleted() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UserPaymentMethod first = method(firstId, "tok_first", true);
        UserPaymentMethod second = method(secondId, "tok_second", false);
        when(paymentMethodRepository.findByIdAndUserIdAndActiveTrue(firstId, USER_ID))
            .thenReturn(Optional.of(first));
        when(paymentMethodRepository
            .findAllByUserIdAndActiveTrueOrderByDefaultMethodDescCreatedAtAsc(USER_ID))
            .thenReturn(List.of(first, second));

        paymentService.deleteMethod(USER_ID, firstId);

        assertThat(first.isActive()).isFalse();
        assertThat(second.isDefaultMethod()).isTrue();
    }

    /**
     * Verifies successful mock charges are persisted with normalized currency and purpose.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect payment transaction creation.
     */
    @Test
    void chargesActiveUserPaymentMethod() {
        UUID methodId = UUID.randomUUID();
        User user = user();
        UserPaymentMethod method = method(methodId, "tok_success", true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(paymentMethodRepository.findByIdAndUserIdAndActiveTrue(methodId, USER_ID))
            .thenReturn(Optional.of(method));
        when(mockPaymentProcessor.charge(
            "tok_success", new BigDecimal("299.00"), "INR"))
            .thenReturn(new MockPaymentProcessor.ProcessorResult(
                PaymentStatus.SUCCEEDED, "mock_pay_123", null));
        when(paymentTransactionRepository.save(
            org.mockito.ArgumentMatchers.any(PaymentTransaction.class)))
            .thenAnswer(invocation ->
                initialize(invocation.getArgument(0), UUID.randomUUID()));

        PaymentDtos.TransactionResponse response = paymentService.charge(
            new PaymentDtos.ChargeRequest(
                USER_ID, methodId, new BigDecimal("299.00"),
                "inr", " Gold subscription ")
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.purpose()).isEqualTo("Gold subscription");
    }

    /**
     * Verifies methods belonging to another user cannot be charged.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect payment method ownership.
     */
    @Test
    void rejectsPaymentMethodNotOwnedByUser() {
        UUID methodId = UUID.randomUUID();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(paymentMethodRepository.findByIdAndUserIdAndActiveTrue(methodId, USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.charge(
            new PaymentDtos.ChargeRequest(
                USER_ID, methodId, new BigDecimal("299.00"), "INR", "Subscription")))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("payment method");
    }

    /**
     * Creates an initialized user fixture.
     *
     * @return persistent-style user fixture
     * @implNote Used internally by payment service tests.
     */
    private User user() {
        return initialize(
            new User("member@example.com", "Member", "User", null), USER_ID);
    }

    /**
     * Creates an initialized card payment method fixture.
     *
     * @param methodId payment method UUID
     * @param token mock provider token
     * @param defaultMethod whether the method begins as default
     * @return active payment method fixture
     * @implNote Used internally by method removal and charge tests.
     */
    private UserPaymentMethod method(
        UUID methodId,
        String token,
        boolean defaultMethod) {
        return initialize(new UserPaymentMethod(
            user(), PaymentMethodType.CARD, token, "Card",
            "VISA", "4242", defaultMethod), methodId);
    }
}
