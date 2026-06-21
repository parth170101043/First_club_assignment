package com.example.FirstClubApp.auth;

import com.example.FirstClubApp.user.User;
import com.example.FirstClubApp.user.UserRepository;
import com.example.FirstClubApp.user.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Optionally creates the first administrator from environment-backed configuration.
 */
@Component
public class AdminAccountBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminAccountBootstrap(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Value("${firstclub.admin.email:}") String email,
        @Value("${firstclub.admin.password:}") String password
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            return;
        }
        userRepository.save(new User(
            normalizedEmail, "FirstClub", "Admin", null,
            passwordEncoder.encode(password), UserRole.ADMIN));
    }
}
