package com.example.FirstClubApp.admin;

import com.example.FirstClubApp.common.ConflictException;
import com.example.FirstClubApp.perk.PerkDtos;
import com.example.FirstClubApp.perk.PerkService;
import com.example.FirstClubApp.perk.PerkType;
import com.example.FirstClubApp.subscription.BillingCycle;
import com.example.FirstClubApp.subscription.SubscriptionService;
import com.example.FirstClubApp.tier.TierService;
import com.example.FirstClubApp.tier.BehavioralTierSettingsService;
import com.example.FirstClubApp.tier.TierEvaluationService;
import com.example.FirstClubApp.user.UserService;
import com.example.FirstClubApp.catalog.ProductDtos;
import com.example.FirstClubApp.catalog.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Provides browser forms for day-to-day FirstClub administration.
 */
@Controller
public class AdminConsoleController {

    private final UserService userService;
    private final TierService tierService;
    private final PerkService perkService;
    private final SubscriptionService subscriptionService;
    private final ProductService productService;
    private final BehavioralTierSettingsService behavioralSettingsService;
    private final TierEvaluationService tierEvaluationService;

    public AdminConsoleController(UserService userService,
                                  TierService tierService,
                                  PerkService perkService,
                                  SubscriptionService subscriptionService,
                                  ProductService productService,
                                  BehavioralTierSettingsService behavioralSettingsService,
                                  TierEvaluationService tierEvaluationService) {
        this.userService = userService;
        this.tierService = tierService;
        this.perkService = perkService;
        this.subscriptionService = subscriptionService;
        this.productService = productService;
        this.behavioralSettingsService = behavioralSettingsService;
        this.tierEvaluationService = tierEvaluationService;
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("tiers", tierService.findActive());
        model.addAttribute("perks", perkService.findAll());
        model.addAttribute("subscriptions", subscriptionService.findAllForAdmin());
        model.addAttribute("perkTypes", PerkType.values());
        model.addAttribute("billingCycles", BillingCycle.values());
        model.addAttribute("products", productService.findAll());
        model.addAttribute("behavioralRules", behavioralSettingsService.current());
        model.addAttribute("assignments", tierService.findActive().stream()
            .flatMap(tier -> perkService.findForTier(tier.id()).stream()
                .map(assignment -> new AdminConsoleForms.AssignmentView(
                    tier.name(), assignment)))
            .toList());
        return "admin-console";
    }

    @PostMapping("/admin/behavioral-rules")
    public String updateBehavioralRules(
        @ModelAttribute AdminConsoleForms.BehavioralRulesForm form,
        RedirectAttributes flash
    ) {
        try {
            behavioralSettingsService.update(
                new BehavioralTierSettingsService.UpdateRequest(
                    form.goldOrderCount(),
                    form.platinumOrderCount(),
                    form.goldMonthlySpend(),
                    form.platinumMonthlySpend(),
                    form.goldCohort(),
                    form.platinumCohort()
                )
            );
            int reevaluated = tierEvaluationService.reevaluateAllActive();
            flash.addFlashAttribute("success",
                "Behavioral rules updated. Reevaluated "
                    + reevaluated + " active subscriptions.");
        } catch (RuntimeException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin#behavioral-rules";
    }

    @PostMapping("/admin/products")
    public String createProduct(@ModelAttribute ProductDtos.CreateRequest form,
                                RedirectAttributes flash) {
        try {
            productService.create(form);
            flash.addFlashAttribute("success", "Product added to the catalogue.");
        } catch (RuntimeException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin#products";
    }

    @PostMapping("/admin/products/{productId}/remove")
    public String removeProduct(@PathVariable UUID productId,
                                RedirectAttributes flash) {
        productService.remove(productId);
        flash.addFlashAttribute("success", "Product removed from the member catalogue.");
        return "redirect:/admin#products";
    }

    @PostMapping("/admin/perks")
    public String createPerk(@ModelAttribute AdminConsoleForms.PerkForm form,
                             RedirectAttributes flash) {
        try {
            perkService.create(new PerkDtos.CreateRequest(
                form.code(), form.name(), form.description(), form.type(),
                discountConfiguration(form.discountPercent(), form.maximumDiscount())));
            flash.addFlashAttribute("success", "Perk created.");
        } catch (RuntimeException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin#perks";
    }

    @PostMapping("/admin/perks/{perkId}/delete")
    public String deletePerk(@PathVariable UUID perkId, RedirectAttributes flash) {
        try {
            perkService.deactivate(perkId);
            flash.addFlashAttribute("success", "Perk deleted.");
        } catch (ConflictException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin#perks";
    }

    @PostMapping("/admin/assignments")
    public String assignPerk(@ModelAttribute AdminConsoleForms.AssignmentForm form,
                             RedirectAttributes flash) {
        try {
            perkService.assign(form.tierId(), form.perkId(),
                new PerkDtos.AssignmentRequest());
            flash.addFlashAttribute("success", "Perk assigned to membership tier.");
        } catch (RuntimeException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin#assignments";
    }

    @PostMapping("/admin/tiers/{tierId}/perks/{perkId}/remove")
    public String unassignPerk(@PathVariable UUID tierId,
                               @PathVariable UUID perkId,
                               RedirectAttributes flash) {
        perkService.unassign(tierId, perkId);
        flash.addFlashAttribute("success", "Perk removed from membership tier.");
        return "redirect:/admin#assignments";
    }

    @PostMapping("/admin/subscriptions")
    public String createSubscription(
        @ModelAttribute AdminConsoleForms.SubscriptionForm form,
        RedirectAttributes flash) {
        try {
            subscriptionService.createForAdmin(
                form.userId(), form.tierId(), form.billingCycle());
            flash.addFlashAttribute("success", "Subscription created.");
        } catch (RuntimeException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin#subscriptions";
    }

    @PostMapping("/admin/subscriptions/{subscriptionId}/end")
    public String endSubscription(@PathVariable UUID subscriptionId,
                                  RedirectAttributes flash) {
        subscriptionService.endForAdmin(subscriptionId);
        flash.addFlashAttribute("success", "Subscription ended.");
        return "redirect:/admin#subscriptions";
    }

    private Map<String, Object> discountConfiguration(
        BigDecimal discountPercent, BigDecimal maximumDiscount) {
        if (discountPercent == null && maximumDiscount == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        if (discountPercent != null) {
            values.put("discountPercent", discountPercent);
        }
        if (maximumDiscount != null) {
            values.put("maximumDiscount", maximumDiscount);
        }
        return values;
    }
}
