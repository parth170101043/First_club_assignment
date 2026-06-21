package com.example.FirstClubApp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;

/**
 * Defines shared infrastructure and session-based browser authentication.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Supplies a UTC clock for subscription start and expiry calculations.
     *
     * @return a system UTC clock with no configurable default offset
     * @implNote Used by {@code SubscriptionService}; tests can replace it with a fixed clock.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Configures the HTTP security filter chain.
     *
     * @param http Spring Security's mutable HTTP configuration
     * @return the built security filter chain
     * @throws Exception when Spring Security cannot build the configuration
     * @implNote Used by Spring Security for every HTTP request.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures form login, session cookies, CSRF for browser forms, and role authorization.
     *
     * @param http Spring Security's mutable HTTP configuration
     * @return the built security filter chain
     * @throws Exception when Spring Security cannot build the configuration
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/signup", "/login", "/error").permitAll()
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/plans", "/api/v1/membership-options",
                    "/api/v1/tiers", "/api/v1/tiers/*").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/tiers").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/tiers/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/users", "/api/v1/users/*").hasRole("ADMIN")
                .requestMatchers("/api/v1/subscriptions/expire-due").hasRole("ADMIN")
                .requestMatchers("/api/v1/payments/charge").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler((request, response, authentication) -> {
                    boolean admin = authentication.getAuthorities().stream()
                        .anyMatch(authority ->
                            "ROLE_ADMIN".equals(authority.getAuthority()));
                    response.sendRedirect(admin ? "/admin" : "/shop");
                })
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.migrateSession())
                .maximumSessions(1))
            .build();
    }
}
