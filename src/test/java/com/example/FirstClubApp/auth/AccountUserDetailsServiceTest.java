package com.example.FirstClubApp.auth;

import com.example.FirstClubApp.user.User;
import com.example.FirstClubApp.user.UserRepository;
import com.example.FirstClubApp.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies database-backed Spring Security account loading.
 */
class AccountUserDetailsServiceTest {

    @Test
    void loadsPasswordRoleAndEnabledStateByEmail() {
        UserRepository repository = mock(UserRepository.class);
        User account = new User(
            "admin@example.com", "Admin", "User", null,
            "$2a$10$encoded", UserRole.ADMIN);
        when(repository.findByEmailIgnoreCase("admin@example.com"))
            .thenReturn(Optional.of(account));

        UserDetails details = new AccountUserDetailsService(repository)
            .loadUserByUsername(" admin@example.com ");

        assertThat(details.getUsername()).isEqualTo("admin@example.com");
        assertThat(details.getPassword()).isEqualTo("$2a$10$encoded");
        assertThat(details.getAuthorities())
            .extracting("authority")
            .containsExactly("ROLE_ADMIN");
        assertThat(details.isEnabled()).isTrue();
    }
}
