package com.example.FirstClubApp.auth;

import com.example.FirstClubApp.common.ConflictException;
import com.example.FirstClubApp.user.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Renders browser signup and login pages.
 */
@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/signup")
    public String signupForm(Model model) {
        if (!model.containsAttribute("signupForm")) {
            model.addAttribute("signupForm",
                new SignupForm("", "", "", "", "", ""));
        }
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute SignupForm signupForm,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (!signupForm.password().equals(signupForm.confirmPassword())) {
            bindingResult.rejectValue(
                "confirmPassword", "password.mismatch", "Passwords do not match.");
        }
        if (bindingResult.hasErrors()) {
            return "signup";
        }
        try {
            userService.register(signupForm);
        } catch (ConflictException exception) {
            bindingResult.rejectValue("email", "email.exists", exception.getMessage());
            return "signup";
        }
        redirectAttributes.addFlashAttribute(
            "signupSuccess", "Account created. You can now sign in.");
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
