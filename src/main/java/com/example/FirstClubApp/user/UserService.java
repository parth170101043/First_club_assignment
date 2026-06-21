package com.example.FirstClubApp.user;

import com.example.FirstClubApp.common.ConflictException;
import com.example.FirstClubApp.common.ResourceNotFoundException;
import com.example.FirstClubApp.auth.SignupForm;
import com.example.FirstClubApp.order.OrderRepository;
import com.example.FirstClubApp.payment.PaymentTransactionRepository;
import com.example.FirstClubApp.subscription.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Applies user creation, retrieval, and safe deletion rules between REST and persistence.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates the user service.
     *
     * @param userRepository persistence gateway for users
     * @param subscriptionRepository persistence gateway used to protect membership history
     * @param orderRepository persistence gateway used to protect order history
     * @param paymentTransactionRepository persistence gateway used to protect payment history
     * @return an initialized user service
     * @implNote Used by Spring dependency injection when constructing user components.
     */
    public UserService(UserRepository userRepository,
                       SubscriptionRepository subscriptionRepository,
                       OrderRepository orderRepository,
                       PaymentTransactionRepository paymentTransactionRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.orderRepository = orderRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Validates, normalizes, and stores a new user.
     *
     * @param request validated user creation request
     * @return created user response
     * @implNote Used by {@link UserController#create(UserDtos.CreateRequest)}.
     */
    @Transactional
    public UserDtos.Response create(UserDtos.CreateRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("A user with this email already exists.");
        }
        User user = new User(normalizedEmail, request.firstName().trim(),
            request.lastName().trim(), trimToNull(request.cohort()),
            passwordEncoder.encode(request.password()), UserRole.MEMBER);
        return UserDtos.Response.from(userRepository.save(user));
    }

    /**
     * Registers a member from the browser signup form.
     *
     * @param form validated signup fields
     * @return created public account response
     */
    @Transactional
    public UserDtos.Response register(SignupForm form) {
        return create(new UserDtos.CreateRequest(
            form.email(), form.firstName(), form.lastName(), form.cohort(), form.password()));
    }

    /**
     * Returns all registered users.
     *
     * @return user responses; defaults to an empty list when no users exist
     * @implNote Used by {@link UserController#findAll()}.
     */
    @Transactional(readOnly = true)
    public List<UserDtos.Response> findAll() {
        return userRepository.findAll().stream().map(UserDtos.Response::from).toList();
    }

    /**
     * Returns a user by identifier.
     *
     * @param id user UUID with no default value
     * @return matching user response
     * @implNote Used by {@link UserController#findById(UUID)}.
     */
    @Transactional(readOnly = true)
    public UserDtos.Response findById(UUID id) {
        return UserDtos.Response.from(requireUser(id));
    }

    /**
     * Deletes a user who has no current or historical subscriptions.
     *
     * @param id user UUID with no default value
     * @return no return value
     * @implNote Used by {@link UserController#delete(UUID)} for test-data cleanup.
     */
    @Transactional
    public void delete(UUID id) {
        User user = requireUser(id);
        if (subscriptionRepository.existsByUserId(id)) {
            throw new ConflictException(
                "Users with subscription history cannot be deleted.");
        }
        if (orderRepository.existsByUserId(id)) {
            throw new ConflictException(
                "Users with order history cannot be deleted.");
        }
        if (paymentTransactionRepository.existsByUserId(id)) {
            throw new ConflictException(
                "Users with payment history cannot be deleted.");
        }
        userRepository.delete(user);
        userRepository.flush();
    }

    /**
     * Loads a user entity or raises a domain-level not-found error.
     *
     * @param id user UUID with no default value
     * @return matching persistent user
     * @implNote Used by user queries and {@code SubscriptionService} validation.
     */
    public User requireUser(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /**
     * Loads an account by authenticated login email.
     *
     * @param email authenticated email
     * @return matching account
     */
    public User requireUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    /**
     * Trims optional text and converts blank input to {@code null}.
     *
     * @param value optional source text; defaults to {@code null}
     * @return trimmed text, or {@code null} when absent or blank
     * @implNote Used internally while normalizing optional user fields.
     */
    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
