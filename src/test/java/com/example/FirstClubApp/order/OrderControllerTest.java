package com.example.FirstClubApp.order;

import com.example.FirstClubApp.common.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies order REST creation, benefit response fields, and request validation.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    /**
     * Creates a standalone MVC environment for the order controller.
     *
     * @return no return value
     * @implNote Used by JUnit before each order controller test.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new OrderController(orderService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    /**
     * Verifies order creation returns the persisted membership benefit snapshot.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect checkout REST integration.
     */
    @Test
    void createsOrderThroughRestApi() throws Exception {
        UUID userId = UUID.randomUUID();
        when(orderService.create(any(OrderDtos.CreateRequest.class))).thenReturn(
            new OrderDtos.Response(
                UUID.randomUUID(), userId, new BigDecimal("1600.00"), "GROCERY",
                new BigDecimal("20.00"), new BigDecimal("320.00"),
                new BigDecimal("1280.00"), true, "GOLD", Instant.now()));

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "%s",
                      "totalAmount": 1600.00,
                      "category": "GROCERY"
                    }
                    """.formatted(userId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.discountPercent").value(20.00))
            .andExpect(jsonPath("$.finalAmount").value(1280.00))
            .andExpect(jsonPath("$.freeDelivery").value(true))
            .andExpect(jsonPath("$.membershipTierCode").value("GOLD"));
    }

    /**
     * Verifies negative order amounts are rejected before service execution.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect order amount validation.
     */
    @Test
    void rejectsNegativeOrderAmount() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "%s",
                      "totalAmount": -1.00
                    }
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.totalAmount").exists());
    }
}
