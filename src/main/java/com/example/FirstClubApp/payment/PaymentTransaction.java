package com.example.FirstClubApp.payment;

import com.example.FirstClubApp.common.AuditableEntity;
import com.example.FirstClubApp.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Stores an immutable audit record for one mock payment attempt.
 */
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private UserPaymentMethod paymentMethod;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 100)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "provider_reference", nullable = false, unique = true, length = 100)
    private String providerReference;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /**
     * Required by JPA when materializing a transaction from PostgreSQL.
     *
     * @return a transaction with fields left unset for JPA population
     * @implNote Used only by the JPA persistence provider.
     */
    protected PaymentTransaction() {
    }

    /**
     * Creates a final mock payment transaction record.
     *
     * @param user user charged by the transaction
     * @param paymentMethod tokenized method used for the charge
     * @param amount positive charge amount
     * @param currency three-letter currency code
     * @param purpose client-provided payment purpose
     * @param status final mock processor outcome
     * @param providerReference unique mock processor reference
     * @param failureReason optional failure explanation; defaults to {@code null} on success
     * @return a new transaction audit record
     * @implNote Used by {@link PaymentService} after mock processor execution.
     */
    public PaymentTransaction(User user,
                              UserPaymentMethod paymentMethod,
                              BigDecimal amount,
                              String currency,
                              String purpose,
                              PaymentStatus status,
                              String providerReference,
                              String failureReason) {
        this.user = user;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.currency = currency;
        this.purpose = purpose;
        this.status = status;
        this.providerReference = providerReference;
        this.failureReason = failureReason;
    }

    /**
     * Returns the charged user.
     *
     * @return associated user
     * @implNote Used by transaction response mapping.
     */
    public User getUser() {
        return user;
    }

    /**
     * Returns the tokenized method used for payment.
     *
     * @return associated payment method
     * @implNote Used by transaction response mapping.
     */
    public UserPaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    /**
     * Returns the attempted charge amount.
     *
     * @return transaction amount
     * @implNote Used by transaction response mapping.
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Returns the transaction currency.
     *
     * @return three-letter currency code
     * @implNote Used by transaction response mapping.
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Returns the payment purpose.
     *
     * @return purpose text
     * @implNote Used by transaction response mapping.
     */
    public String getPurpose() {
        return purpose;
    }

    /**
     * Returns the final payment outcome.
     *
     * @return succeeded or failed status
     * @implNote Used by transaction response mapping.
     */
    public PaymentStatus getStatus() {
        return status;
    }

    /**
     * Returns the mock provider reference.
     *
     * @return unique provider reference
     * @implNote Used by transaction response mapping and support diagnostics.
     */
    public String getProviderReference() {
        return providerReference;
    }

    /**
     * Returns the optional failure reason.
     *
     * @return failure explanation, or {@code null} for successful payments
     * @implNote Used by transaction response mapping.
     */
    public String getFailureReason() {
        return failureReason;
    }
}
