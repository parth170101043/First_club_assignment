package com.example.FirstClubApp.home;

import com.example.FirstClubApp.common.ResourceNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Renders the simple server-side HTML homepage for a FirstClub member.
 */
@Controller
public class HomePageController {

    private final HomePageService homePageService;

    /**
     * Creates the user homepage controller.
     *
     * @param homePageService service that aggregates homepage data
     * @return an initialized controller
     * @implNote Used by Spring MVC during application startup.
     */
    public HomePageController(HomePageService homePageService) {
        this.homePageService = homePageService;
    }

    /**
     * Renders the membership homepage for a user with an active subscription.
     *
     * @param authentication authenticated member session
     * @param model Spring MVC model receiving the homepage data
     * @return Thymeleaf view name {@code user-home}
     * @implNote Used by browsers opening {@code /home}; identity comes from the session.
     */
    @GetMapping("/home")
    public String show(Authentication authentication, Model model) {
        try {
            model.addAttribute(
                "home", homePageService.getHomePageForEmail(authentication.getName()));
            return "user-home";
        } catch (ResourceNotFoundException exception) {
            model.addAttribute("email", authentication.getName());
            return "no-subscription";
        }
    }

    @PostMapping("/membership/downgrade")
    public String downgrade(@RequestParam UUID tierId,
                            Authentication authentication,
                            RedirectAttributes flash) {
        try {
            homePageService.scheduleDowngrade(authentication.getName(), tierId);
            flash.addFlashAttribute("success",
                "Your downgrade is scheduled for the next renewal.");
        } catch (RuntimeException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/home";
    }

    @PostMapping("/membership/cancel")
    public String cancel(Authentication authentication,
                         RedirectAttributes flash) {
        try {
            homePageService.scheduleCancellation(authentication.getName());
            flash.addFlashAttribute("success",
                "Your subscription will expire at the end of the current period.");
        } catch (RuntimeException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/home";
    }

    @PostMapping("/membership/reactivate")
    public String reactivate(Authentication authentication,
                             RedirectAttributes flash) {
        try {
            homePageService.resumeAutoRenewal(authentication.getName());
            flash.addFlashAttribute("success", "Automatic renewal is active again.");
        } catch (RuntimeException exception) {
            flash.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/home";
    }
}
