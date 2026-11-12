package com.payagent.releasegates;
import com.payagent.auth.*;
import com.payagent.deals.*;
import com.payagent.agreements.Sha256Hasher;
import com.payagent.common.contracts.DealStatus;
import java.util.*;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
/** Positive controls using the actual JWT filter, database and controllers after identity integration. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CrossModuleIdentityReleaseGateIntegrationTest {
 @Autowired com.payagent.settlement.persistence.PaymentRepository payments;
 @Autowired MockMvc mvc; @Autowired UserRepository users; @Autowired DealRepository deals; @Autowired JwtTokenProvider tokens;
 private User user(){UUID id=UUID.randomUUID();return users.save(new User(id,id+"@example.test","test-only",OffsetDateTime.now()));}
 private String auth(User u){return "Bearer "+tokens.createToken(u.getId(),u.getEmail());}
 @Test void issue26_realJwtCreatesDeal() throws Exception {
  User buyer=user();mvc.perform(post("/api/v1/deals").header("Authorization",auth(buyer)).header("Idempotency-Key",UUID.randomUUID().toString()))
   .andExpect(status().isOk()).andExpect(jsonPath("$.dealId").exists());
 }
 @Test void issue27_wrongInvitePreservesExistingSeller() throws Exception {
  User buyer=user(),seller=user(),intruder=user();UUID id=UUID.randomUUID();Deal d=new Deal(id,buyer.getId(),seller.getId(),DealStatus.ACTIVE_NEGOTIATION);
  d.setInviteTokenHash(Sha256Hasher.hash("correct-invitation".getBytes(StandardCharsets.UTF_8)));deals.save(d);
  mvc.perform(post("/api/v1/deals/"+id+"/join").header("Authorization",auth(intruder)).header("Idempotency-Key",UUID.randomUUID().toString())
   .contentType("application/json").content("{\"inviteToken\":\"wrong-invitation\"}")).andExpect(status().isBadRequest());
  assertEquals(seller.getId(),deals.findById(id).orElseThrow().getSellerUserId());
 }
 @Test void authorizedBuyerCanStillReadOwnSettlementAfterIdentityFix() throws Exception {
  User buyer=user(),seller=user();UUID id=UUID.randomUUID();deals.save(new Deal(id,buyer.getId(),seller.getId(),DealStatus.IN_PROGRESS));
  payments.save(new com.payagent.settlement.persistence.PaymentEntity(UUID.randomUUID(), id, 1,
   com.payagent.common.contracts.PaymentLifecycleStatus.AUTHORIZED, "SYNTHETIC-ORDER", "SYNTHETIC-AUTH", null,
   java.time.Instant.now().plusSeconds(3600), 98000, "USD"));
  mvc.perform(get("/api/v1/deals/"+id+"/settlement").header("Authorization",auth(buyer))).andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("AUTHORIZED"));
 }
}
