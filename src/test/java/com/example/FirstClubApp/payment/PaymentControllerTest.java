package com.example.FirstClubApp.payment;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies payment method and mock charge REST contracts and validation.
 */
@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;

    /**
     * Creates a standalone MVC environment for payment endpoints.
     *
     * @return no return value
     * @implNote Used by JUnit before each payment controller test.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new PaymentController(paymentService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    /**
     * Verifies users can add tokenized payment methods without exposing tokens in responses.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect payment method setup routing.
     */
    @Test
    void addsPaymentMethodThroughRestApi() throws Exception {
        UUID userId = UUID.randomUUID();
        when(paymentService.addMethod(
            eq(userId), any(PaymentDtos.AddMethodRequest.class)))
            .thenReturn(new PaymentDtos.MethodResponse(
                UUID.randomUUID(), userId, PaymentMethodType.CARD,
                "Personal card", "VISA", "4242", true, true, Instant.now()));

        mockMvc.perform(post("/api/v1/users/{userId}/payment-methods", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "CARD",
                      "providerToken": "tok_success",
                      "displayName": "Personal card",
                      "brand": "VISA",
                      "lastFour": "4242"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.lastFour").value("4242"))
            .andExpect(jsonPath("$.defaultMethod").value(true))
            .andExpect(jsonPath("$.providerToken").doesNotExist());
    }

    /**
     * Verifies mock charge creation returns a persisted transaction outcome.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect mock charge routing.
     */
    @Test
    void chargesPaymentMethodThroughRestApi() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();
        when(paymentService.charge(any(PaymentDtos.ChargeRequest.class)))
            .thenReturn(new PaymentDtos.TransactionResponse(
                UUID.randomUUID(), userId, methodId, new BigDecimal("299.00"),
                "INR", "Subscription", PaymentStatus.SUCCEEDED,
                "mock_pay_123", null, Instant.now()));

        mockMvc.perform(post("/api/v1/payments/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "%s",
                      "paymentMethodId": "%s",
                      "amount": 299.00,
                      "currency": "INR",
                      "purpose": "Subscription"
                    }
                    """.formatted(userId, methodId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.providerReference").value("mock_pay_123"));
    }

    /**
     * Verifies invalid masked card suffixes are rejected by request validation.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect payment method metadata quality.
     */
    @Test
    void rejectsInvalidLastFour() throws Exception {
        mockMvc.perform(post(
                "/api/v1/users/{userId}/payment-methods", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "CARD",
                      "providerToken": "tok_success",
                      "displayName": "Card",
                      "lastFour": "42"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.lastFour").exists());
    }
}
