package com.example.FirstClubApp.order;

import com.example.FirstClubApp.common.AuditableEntity;
import com.example.FirstClubApp.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores an immutable checkout snapshot of an order and the membership benefits applied to it.
 */
@Entity
@Table(name = "customer_orders")
public class CustomerOrder extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 100)
    private String category;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "applied_discount_perk_code", length = 80)
    private String appliedDiscountPerkCode;

    @Column(name = "applied_discount_perk_name", length = 120)
    private String appliedDiscountPerkName;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "final_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "free_delivery", nullable = false)
    private boolean freeDelivery;

    @Column(name = "membership_tier_code", length = 50)
    private String membershipTierCode;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    /**
     * Required by JPA when materializing an order from PostgreSQL.
     *
     * @return an order with fields left unset for JPA population
     * @implNote Used only by the JPA persistence provider.
     */
    protected CustomerOrder() {
    }

    /**
     * Creates a completed order with a snapshot of checkout benefits.
     *
     * @param user user who placed the order
     * @param totalAmount original order amount before membership discount
     * @param category optional category; defaults to {@code null}
     * @param discountPercent applied percentage; defaults to zero without an eligible benefit
     * @param discountAmount monetary discount applied to the order
     * @param deliveryFee delivery charge after applying any free-delivery perk
     * @param finalAmount amount payable after discount and delivery charge
     * @param freeDelivery whether a free-delivery perk was active
     * @param membershipTierCode tier used for benefits, or {@code null} without an active subscription
     * @return a new completed order snapshot
     * @implNote Used by {@link OrderService} after membership benefit evaluation.
     */
    public CustomerOrder(User user,
                         BigDecimal totalAmount,
                         String category,
                         BigDecimal discountPercent,
                         BigDecimal discountAmount,
                         String appliedDiscountPerkCode,
                         String appliedDiscountPerkName,
                         BigDecimal deliveryFee,
                         BigDecimal finalAmount,
                         boolean freeDelivery,
                         String membershipTierCode) {
        this.user = user;
        this.totalAmount = totalAmount;
        this.category = category;
        this.discountPercent = discountPercent;
        this.discountAmount = discountAmount;
        this.appliedDiscountPerkCode = appliedDiscountPerkCode;
        this.appliedDiscountPerkName = appliedDiscountPerkName;
        this.deliveryFee = deliveryFee;
        this.finalAmount = finalAmount;
        this.freeDelivery = freeDelivery;
        this.membershipTierCode = membershipTierCode;
    }

    /**
     * Returns the user who placed the order.
     *
     * @return associated user
     * @implNote Used by order response mapping.
     */
    public User getUser() {
        return user;
    }

    /**
     * Returns the original order amount.
     *
     * @return amount before membership discount
     * @implNote Used by order response mapping and future behavioral spend evaluation.
     */
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * Returns the optional order category.
     *
     * @return category, or {@code null} when omitted
     * @implNote Used by order responses and future category-specific benefit rules.
     */
    public String getCategory() {
        return category;
    }

    /**
     * Returns the applied discount percentage.
     *
     * @return discount percentage snapshot
     * @implNote Used by order response mapping.
     */
    public BigDecimal getDiscountPercent() {
        return discountPercent;
    }

    /**
     * Returns the applied monetary discount.
     *
     * @return discount amount snapshot
     * @implNote Used by order response mapping.
     */
    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public String getAppliedDiscountPerkCode() {
        return appliedDiscountPerkCode;
    }

    public String getAppliedDiscountPerkName() {
        return appliedDiscountPerkName;
    }

    /**
     * Returns the delivery charge applied at checkout.
     *
     * @return zero for free delivery, otherwise the standard delivery fee
     */
    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    /**
     * Returns the final payable amount.
     *
     * @return final amount after discount
     * @implNote Used by order response mapping.
     */
    public BigDecimal getFinalAmount() {
        return finalAmount;
    }

    /**
     * Reports whether free delivery was applied.
     *
     * @return {@code true} when the active tier had a free-delivery perk
     * @implNote Used by order response mapping and checkout fulfillment.
     */
    public boolean isFreeDelivery() {
        return freeDelivery;
    }

    /**
     * Returns the membership tier used at checkout.
     *
     * @return tier code, or {@code null} when the user had no active subscription
     * @implNote Used by order response mapping and audit diagnostics.
     */
    public String getMembershipTierCode() {
        return membershipTierCode;
    }

    public void addItem(com.example.FirstClubApp.catalog.Product product, int quantity) {
        items.add(new OrderItem(this, product, quantity));
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }
}
