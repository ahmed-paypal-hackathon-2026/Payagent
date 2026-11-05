package com.payagent.releasegates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.auth.*;
import com.payagent.common.contracts.DealStatus;
import com.payagent.deals.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Executes when E's real module is on the classpath; absent module is explicitly skipped on F-only branch. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@EnabledIf("evidenceAvailable")
class EvidenceModuleReleaseGateIntegrationTest {
    private static final String SECRET = "evidence-release-test-secret-never-for-deployment";
    private static final byte[] CONTENT = "%PDF-1.4\nDisposable release test\n%%EOF".getBytes(StandardCharsets.UTF_8);
    private static final Path STORAGE;
    static {
        try { STORAGE = Files.createTempDirectory("payagent-evidence-release-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static boolean evidenceAvailable() {
        try { Class.forName("com.payagent.evidence.service.EvidenceStorageService"); return true; }
        catch (ClassNotFoundException e) { return false; }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("payagent.evidence.storage-path", STORAGE::toString);
        registry.add("payagent.evidence.signing-secret", () -> SECRET);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired DealRepository deals;
    @Autowired JwtTokenProvider tokens;
    private User buyer, seller, outsider;
    private UUID deal;

    @BeforeEach
    void setUp() {
        buyer = user(); seller = user(); outsider = user();
        deal = UUID.randomUUID();
        deals.save(new Deal(deal, buyer.getId(), seller.getId(), DealStatus.IN_PROGRESS));
    }
    private User user() {
        UUID id=UUID.randomUUID();
        return users.save(new User(id,id+"@example.test","unused-test-password-hash",OffsetDateTime.now()));
    }
    private String auth(User user) { return "Bearer "+tokens.createToken(user.getId(),user.getEmail()); }
    private String route() { return "/api/v1/deals/"+deal+"/evidence"; }
    private MockMultipartFile file() { return new MockMultipartFile("file","release.pdf","application/pdf",CONTENT); }
    private JsonNode upload(String key) throws Exception {
        var response=mvc.perform(multipart(route()).file(file()).header("Authorization",auth(seller))
                .header("Idempotency-Key",key)).andExpect(status().isOk()).andReturn().getResponse();
        return mapper.readTree(response.getContentAsString());
    }

    @Test
    void tst12_realSellerUploadBuyerDownloadHasExactSha256() throws Exception {
        var evidence=upload(UUID.randomUUID().toString());
        String expected=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CONTENT));
        assertEquals(expected,evidence.get("sha256Hash").asText());
        mvc.perform(get(evidence.get("downloadSignedUrl").asText()).header("Authorization",auth(buyer)))
                .andExpect(status().isOk()).andExpect(content().bytes(CONTENT));
        assertEquals(DealStatus.DELIVERABLES_SUBMITTED,deals.findById(deal).orElseThrow().getStatus());
    }

    @Test
    void tst10_12_outsiderCannotListOrDownloadEvenWithValidSignature() throws Exception {
        var evidence=upload(UUID.randomUUID().toString());
        mvc.perform(get(route()).header("Authorization",auth(outsider))).andExpect(status().isForbidden());
        mvc.perform(get(evidence.get("downloadSignedUrl").asText()).header("Authorization",auth(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void tst12_buyerCannotUploadSellerEvidence() throws Exception {
        mvc.perform(multipart(route()).file(file()).header("Authorization",auth(buyer))
                .header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isForbidden());
    }

    @Test
    void tst13_correctlySignedExpiredLinkRejectsAuthorizedBuyer() throws Exception {
        var evidence=upload(UUID.randomUUID().toString());
        mvc.perform(get(evidence.get("downloadSignedUrl").asText()).header("Authorization",auth(buyer)))
                .andExpect(status().isOk());
        long expires=Instant.now().minusSeconds(1).getEpochSecond();
        String payload=evidence.get("evidenceId").asText()+":"+deal+":"+expires;
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signature=HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        mvc.perform(get(route()+"/"+evidence.get("evidenceId").asText()+"/download?expires="+expires+"&sig="+signature)
                .header("Authorization",auth(buyer))).andExpect(status().isForbidden());
    }

    @Test
    void tst09_duplicateUploadKeyMustReplayOriginalEvidence() throws Exception {
        String key=UUID.randomUUID().toString();
        var first=upload(key);var second=upload(key);
        assertEquals(first.get("evidenceId"),second.get("evidenceId"),"Same idempotency key must not create another evidence row/file/version");
        assertEquals(first.get("version"),second.get("version"));
    }

    @AfterAll
    static void removeDisposableFiles() throws Exception {
        try(var paths=Files.walk(STORAGE)) {
            for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
