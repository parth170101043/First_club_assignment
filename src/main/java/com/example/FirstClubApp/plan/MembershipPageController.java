package com.example.FirstClubApp.plan;

import com.example.FirstClubApp.common.ConflictException;
import com.example.FirstClubApp.payment.PaymentDtos;
import com.example.FirstClubApp.payment.PaymentMethodType;
import com.example.FirstClubApp.payment.PaymentService;
import com.example.FirstClubApp.subscription.BillingCycle;
import com.example.FirstClubApp.subscription.SubscriptionDtos;
import com.example.FirstClubApp.subscription.SubscriptionService;
import com.example.FirstClubApp.user.User;
import com.example.FirstClubApp.user.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides the browser-facing membership selection and checkout experience.
 */
@Controller
public class MembershipPageController {

    private final MembershipPlanService planService;
    private final UserService userService;
    private final PaymentService paymentService;
    private final SubscriptionService subscriptionService;

    public MembershipPageController(MembershipPlanService planService,
                                    UserService userService,
                                    PaymentService paymentService,
                                    SubscriptionService subscriptionService) {
        this.planService = planService;
        this.userService = userService;
        this.paymentService = paymentService;
        this.subscriptionService = subscriptionService;
    }

    /**
     * Renders all purchasable tier and duration combinations as selection cards.
     */
    @GetMapping("/memberships")
    public String memberships(Authentication authentication, Model model) {
        User user = userService.requireUserByEmail(authentication.getName());
        Optional<SubscriptionDtos.Response> active =
            subscriptionService.findCurrentForUser(user.getId());
        List<PlanDtos.OptionResponse> options = planService.findOptions();
        if (active.isPresent()) {
            SubscriptionDtos.Response subscription = active.get();
            int currentPaidRank = options.stream()
                .filter(option -> option.tierId().equals(subscription.minTierId()))
                .mapToInt(PlanDtos.OptionResponse::tierRank)
                .findFirst()
                .orElseThrow(() -> new com.example.FirstClubApp.common.ResourceNotFoundException(
                    "Current membership pricing was not found."));
            options = options.stream()
                .filter(option -> option.billingCycle() == subscription.billingCycle())
                .filter(option -> option.tierRank() > currentPaidRank)
                .toList();
            model.addAttribute("cycles",
                new BillingCycle[]{subscription.billingCycle()});
            model.addAttribute("upgradeMode", true);
        } else {
            model.addAttribute("cycles", BillingCycle.values());
            model.addAttribute("upgradeMode", false);
        }
        model.addAttribute("options", options);
        return "membership-options";
    }

    /**
     * Renders checkout for one active tier and billing-cycle price.
     */
    @GetMapping("/membership/checkout")
    public String checkout(@RequestParam UUID tierId,
                           @RequestParam BillingCycle billingCycle,
                           Authentication authentication,
                           Model model) {
        User user = userService.requireUserByEmail(authentication.getName());
        Optional<SubscriptionDtos.Response> active =
            subscriptionService.findCurrentForUser(user.getId());
        PlanDtos.OptionResponse option;
        if (active.isPresent()) {
            SubscriptionDtos.Response subscription = active.get();
            if (billingCycle != subscription.billingCycle()) {
                throw new ConflictException(
                    "An upgrade must keep the current billing cycle until renewal.");
            }
            option = requireOption(tierId, subscription.billingCycle());
            SubscriptionDtos.UpgradeQuote quote =
                subscriptionService.quoteUpgrade(user.getId(), tierId);
            model.addAttribute("upgradeMode", true);
            model.addAttribute("upgradeQuote", quote);
        } else {
            option = requireOption(tierId, billingCycle);
            model.addAttribute("upgradeMode", false);
        }
        model.addAttribute("option", option);
        if (!model.containsAttribute("checkoutForm")) {
            model.addAttribute("checkoutForm", new MembershipCheckoutForm(
                tierId, billingCycle, "", "Visa", ""));
        }
        return "membership-checkout";
    }

    /**
     * Creates a mock tokenized payment method and purchases the selected subscription.
     */
    @PostMapping("/membership/checkout")
    public String purchase(
        @Valid @ModelAttribute("checkoutForm") MembershipCheckoutForm form,
        BindingResult bindingResult,
        Authentication authentication,
        Model model
    ) {
        PlanDtos.OptionResponse option = requireOption(form.tierId(), form.billingCycle());
        User user = userService.requireUserByEmail(authentication.getName());
        Optional<SubscriptionDtos.Response> active =
            subscriptionService.findCurrentForUser(user.getId());
        populateCheckoutMode(model, active, user.getId(), form.tierId());
        if (bindingResult.hasErrors()) {
            model.addAttribute("option", option);
            return "membership-checkout";
        }

        try {
            PaymentDtos.MethodResponse method = paymentService.addMethod(
                user.getId(),
                new PaymentDtos.AddMethodRequest(
                    PaymentMethodType.CARD,
                    "web_" + UUID.randomUUID(),
                    form.cardholderName(),
                    form.brand(),
                    form.lastFour(),
                    true
                )
            );
            if (active.isPresent()) {
                SubscriptionDtos.Response subscription = active.get();
                if (form.billingCycle() != subscription.billingCycle()) {
                    throw new ConflictException(
                        "An upgrade must keep the current billing cycle until renewal.");
                }
                subscriptionService.upgrade(
                    subscription.id(),
                    new SubscriptionDtos.UpgradeRequest(form.tierId(), method.id()));
                return "redirect:/home?upgraded";
            }
            subscriptionService.subscribe(new SubscriptionDtos.CreateRequest(
                user.getId(), form.tierId(), form.billingCycle(), method.id()));
            return "redirect:/home?subscribed";
        } catch (ConflictException exception) {
            model.addAttribute("option", option);
            model.addAttribute("purchaseError", exception.getMessage());
            return "membership-checkout";
        }
    }

    private void populateCheckoutMode(
        Model model,
        Optional<SubscriptionDtos.Response> active,
        UUID userId,
        UUID tierId
    ) {
        model.addAttribute("upgradeMode", active.isPresent());
        if (active.isPresent()) {
            try {
                model.addAttribute(
                    "upgradeQuote", subscriptionService.quoteUpgrade(userId, tierId));
            } catch (ConflictException ignored) {
                // The controller displays the validation or lifecycle error instead.
            }
        }
    }

    private PlanDtos.OptionResponse requireOption(UUID tierId, BillingCycle billingCycle) {
        return planService.findOptions().stream()
            .filter(option -> option.tierId().equals(tierId))
            .filter(option -> option.billingCycle() == billingCycle)
            .findFirst()
            .orElseThrow(() -> new com.example.FirstClubApp.common.ResourceNotFoundException(
                "Membership option not found."));
    }
}
