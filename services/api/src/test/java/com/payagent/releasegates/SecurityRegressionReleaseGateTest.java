package com.payagent.releasegates;

import com.payagent.ai.intake.IntakeExtractionService;
import com.payagent.ai.negotiation.StructuredTermsValidator;
import com.payagent.auth.*;
import com.payagent.common.contracts.*;
import com.payagent.deals.*;
import com.payagent.settlement.IdempotencyService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** These gates deliberately remain red until Member A fixes the underlying production behavior. */
class SecurityRegressionReleaseGateTest {
    @Test
    void tst10_validJwtPrincipalMustIdentifyUserToDealController() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmail("buyer@example.test")).thenReturn(java.util.Optional.of(
                new User(buyerId, "buyer@example.test", "test-hash", java.time.OffsetDateTime.now())));
        JwtTokenProvider tokens = new JwtTokenProvider("release-test-only-secret-at-least-32-characters", 60);
        JwtAuthFilter filter = new JwtAuthFilter(tokens, new UserDetailsServiceImpl(users));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + tokens.createToken(buyerId, "buyer@example.test"));
        SecurityContextHolder.clearContext();
        try {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            DealService deals = mock(DealService.class);
            when(deals.createDeal(buyerId, null)).thenReturn(new Deal(UUID.randomUUID(), buyerId, null, DealStatus.DRAFT));
            IdempotencyService idempotency = mock(IdempotencyService.class);
            when(idempotency.execute(anyString(), anyString(), any(), any(), any(), any()))
                    .thenAnswer(i -> ((java.util.function.Supplier<?>) i.getArgument(5)).get());
            DealController controller = new DealController(deals, idempotency, mock(IntakeExtractionService.class),
                    new StructuredTermsValidator(), mock(DealRepository.class));
            var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new DealExceptionHandler()).build();
            mvc.perform(post("/api/v1/deals").header("Idempotency-Key", "jwt-deal-create")
                    .principal(SecurityContextHolder.getContext().getAuthentication())
                    .requestAttr("userId", request.getAttribute("userId"))
                    .requestAttr("userEmail", request.getAttribute("userEmail")))
                    .andExpect(status().isOk());
            verify(deals).createDeal(buyerId, null);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void tst10_wrongInvitationMustNotReplaceExistingSeller() {
        DealRepository repository = mock(DealRepository.class);
        DealService deals = new DealService(repository, new DealStateMachine(repository));
        IdempotencyService idempotency = mock(IdempotencyService.class);
        UUID dealId = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        Deal deal = new Deal(dealId, UUID.randomUUID(), seller, DealStatus.ACTIVE_NEGOTIATION);
        deal.setInviteTokenHash(com.payagent.agreements.Sha256Hasher.hash("correct-invitation".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(repository.findById(dealId)).thenReturn(java.util.Optional.of(deal));
        when(repository.save(any(Deal.class))).thenAnswer(i -> i.getArgument(0));
        when(idempotency.execute(anyString(), anyString(), any(), any(), any(), any()))
                .thenAnswer(i -> ((java.util.function.Supplier<ResponseEntity>) i.getArgument(5)).get());
        DealController controller = new DealController(deals, idempotency, mock(IntakeExtractionService.class),
                new StructuredTermsValidator(), repository);
        try {
            controller.joinDeal(dealId, "intruder-key", new DealController.JoinRequest("wrong-invitation"), () -> intruder.toString());
        } catch (SecurityException | IllegalArgumentException rejected) {
            // Rejection is acceptable only if the authorized seller was preserved.
        }
        assertEquals(seller, deal.getSellerUserId(), "Invalid invitation must not replace the seller");
    }

    @Test
    void tst10_nonMemberCannotPassRealMembershipCheck() {
        DealRepository repository = mock(DealRepository.class);
        UUID dealId = UUID.randomUUID();
        when(repository.findById(dealId)).thenReturn(java.util.Optional.of(
                new Deal(dealId, UUID.randomUUID(), UUID.randomUUID(), DealStatus.ACTIVE_NEGOTIATION)));
        DealService deals = new DealService(repository, new DealStateMachine(repository));
        assertThrows(SecurityException.class, () -> deals.requireMembership(dealId, UUID.randomUUID()));
    }
    @Test
    void tst10_nonMemberMandateRequestReturns403AtControllerBoundary() throws Exception {
        DealRepository repository = mock(DealRepository.class);
        UUID dealId = UUID.randomUUID();
        when(repository.findById(dealId)).thenReturn(java.util.Optional.of(
                new Deal(dealId, UUID.randomUUID(), UUID.randomUUID(), DealStatus.ACTIVE_NEGOTIATION)));
        DealService deals = new DealService(repository, new DealStateMachine(repository));
        com.payagent.mandates.MandateRepository mandates = mock(com.payagent.mandates.MandateRepository.class);
        var service = new com.payagent.mandates.MandateService(mandates, deals, new com.fasterxml.jackson.databind.ObjectMapper());
        var controller = new com.payagent.mandates.MandateController(service, mock(IdempotencyService.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.payagent.mandates.MandateExceptionHandler()).build();
        mvc.perform(get("/api/v1/deals/" + dealId + "/mandates").principal(() -> UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mandates);
    }

}
