package com.example.FirstClubApp.user;

import com.example.FirstClubApp.common.ConflictException;
import com.example.FirstClubApp.common.ResourceNotFoundException;
import com.example.FirstClubApp.order.OrderRepository;
import com.example.FirstClubApp.payment.PaymentTransactionRepository;
import com.example.FirstClubApp.subscription.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static com.example.FirstClubApp.testutil.TestEntityFactory.initialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies user normalization, uniqueness, retrieval, and protected deletion behavior.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    /**
     * Creates the service under test with mocked persistence dependencies.
     *
     * @return no return value
     * @implNote Used by JUnit before each user service test.
     */
    @BeforeEach
    void setUp() {
        userService = new UserService(
            userRepository, subscriptionRepository, orderRepository,
            paymentTransactionRepository, passwordEncoder);
    }

    /**
     * Verifies that user creation normalizes email and optional cohort values.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect user creation rules.
     */
    @Test
    void createsNormalizedUser() {
        UserDtos.CreateRequest request =
            new UserDtos.CreateRequest(
                " TEST@Example.COM ", " Test ", " User ", "  ", "password123");
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(false);
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
            .thenAnswer(invocation -> initialize(invocation.getArgument(0), UUID.randomUUID()));

        UserDtos.Response response = userService.create(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("test@example.com");
        assertThat(captor.getValue().getCohort()).isNull();
        assertThat(response.firstName()).isEqualTo("Test");
    }

    /**
     * Verifies that duplicate email creation is rejected before persistence.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect email uniqueness behavior.
     */
    @Test
    void rejectsDuplicateEmail() {
        UserDtos.CreateRequest request =
            new UserDtos.CreateRequest(
                "test@example.com", "Test", "User", null, "password123");
        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /**
     * Verifies that all persisted users are mapped into API responses.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect administrative user listing.
     */
    @Test
    void listsAllUsers() {
        User first = initialize(
            new User("first@example.com", "First", "User", null), UUID.randomUUID());
        User second = initialize(
            new User("second@example.com", "Second", "User", "LOYAL"), UUID.randomUUID());
        when(userRepository.findAll()).thenReturn(List.of(first, second));

        List<UserDtos.Response> responses = userService.findAll();

        assertThat(responses).extracting(UserDtos.Response::email)
            .containsExactly("first@example.com", "second@example.com");
    }

    /**
     * Verifies that a user without subscription history can be deleted.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect test-user cleanup behavior.
     */
    @Test
    void deletesUserWithoutSubscriptionHistory() {
        UUID userId = UUID.randomUUID();
        User user = initialize(new User("test@example.com", "Test", "User", null), userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.existsByUserId(userId)).thenReturn(false);
        when(orderRepository.existsByUserId(userId)).thenReturn(false);
        when(paymentTransactionRepository.existsByUserId(userId)).thenReturn(false);

        userService.delete(userId);

        verify(userRepository).delete(user);
        verify(userRepository).flush();
    }

    /**
     * Verifies that subscription history prevents destructive user deletion.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to preserve membership history.
     */
    @Test
    void rejectsDeletionWhenSubscriptionHistoryExists() {
        UUID userId = UUID.randomUUID();
        User user = initialize(new User("test@example.com", "Test", "User", null), userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.existsByUserId(userId)).thenReturn(true);

        assertThatThrownBy(() -> userService.delete(userId))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("subscription history");
        verify(userRepository, never()).delete(user);
    }

    /**
     * Verifies that order history prevents destructive user deletion.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to preserve checkout audit history.
     */
    @Test
    void rejectsDeletionWhenOrderHistoryExists() {
        UUID userId = UUID.randomUUID();
        User user = initialize(new User("test@example.com", "Test", "User", null), userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.existsByUserId(userId)).thenReturn(false);
        when(orderRepository.existsByUserId(userId)).thenReturn(true);

        assertThatThrownBy(() -> userService.delete(userId))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("order history");
        verify(userRepository, never()).delete(user);
    }

    /**
     * Verifies that payment history prevents destructive user deletion.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to preserve payment audit history.
     */
    @Test
    void rejectsDeletionWhenPaymentHistoryExists() {
        UUID userId = UUID.randomUUID();
        User user = initialize(new User("test@example.com", "Test", "User", null), userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.existsByUserId(userId)).thenReturn(false);
        when(orderRepository.existsByUserId(userId)).thenReturn(false);
        when(paymentTransactionRepository.existsByUserId(userId)).thenReturn(true);

        assertThatThrownBy(() -> userService.delete(userId))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("payment history");
        verify(userRepository, never()).delete(user);
    }

    /**
     * Verifies that requesting a missing user produces a domain not-found error.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect missing-user API behavior.
     */
    @Test
    void rejectsMissingUserLookup() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(userId.toString());
    }
}
