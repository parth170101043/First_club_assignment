package com.example.FirstClubApp.home;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the homepage controller's route-facing view name and model population.
 */
class HomePageControllerTest {

    /**
     * Verifies the controller adds homepage data and selects the Thymeleaf template.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect server-rendered homepage routing.
     */
    @Test
    void rendersUserHomeTemplate() {
        UUID userId = UUID.randomUUID();
        HomePageService homePageService = mock(HomePageService.class);
        HomePageView home = new HomePageView(
            userId, UUID.randomUUID(), "Member", "GOLD", "Gold", "Gold",
            "MONTHLY", "21 Jul 2026", false, true, null, List.of(), List.of());
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("member@example.com");
        when(homePageService.getHomePageForEmail("member@example.com")).thenReturn(home);
        HomePageController controller = new HomePageController(homePageService);
        Model model = new ConcurrentModel();

        String viewName = controller.show(authentication, model);

        assertThat(viewName).isEqualTo("user-home");
        assertThat(model.getAttribute("home")).isSameAs(home);
    }
}
