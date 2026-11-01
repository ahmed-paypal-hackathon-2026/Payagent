package com.payagent.evidence;

import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.DeliverableEvidenceDTO;
import com.payagent.evidence.persistence.EvidenceEntity;
import com.payagent.evidence.persistence.EvidenceRepository;
import com.payagent.evidence.service.EvidenceSignatureService;
import com.payagent.evidence.service.EvidenceStorageService;
import com.payagent.settlement.exception.SettlementForbiddenException;
import com.payagent.settlement.port.AuditEventPort;
import com.payagent.settlement.port.DealPort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EvidenceStorageServiceTest {

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private EvidenceSignatureService signatureService;

    @MockBean
    private DealPort dealPort;

    @MockBean
    private com.payagent.realtime.service.AuditEventService auditEventService;

    @TempDir
    Path tempStorageDir;

    private EvidenceStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new EvidenceStorageService(
                evidenceRepository,
                dealPort,
                auditEventService,
                signatureService,
                tempStorageDir.toString()
        );
    }

    @Test
    void storeEvidence_validZipFile_storesAndUpdatesDealStatus() throws Exception {
        UUID dealId = UUID.randomUUID();
        String sellerEmail = "seller@test.local";
        when(dealPort.getSellerEmail(dealId)).thenReturn(sellerEmail);

        byte[] content = "synthetic-zip-content-for-testing".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "deliverable_v1.zip",
                "application/zip",
                content
        );

        DeliverableEvidenceDTO dto = storageService.storeEvidence(dealId, file, sellerEmail);

        assertThat(dto).isNotNull();
        assertThat(dto.filename()).isEqualTo("deliverable_v1.zip");
        assertThat(dto.version()).isEqualTo(1);
        assertThat(dto.sizeBytes()).isEqualTo(content.length);

        // Verify SHA-256 calculation
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        assertThat(dto.sha256Hash()).isEqualTo(expectedHash);

        // Verify Deal status transition to DELIVERABLES_SUBMITTED
        verify(dealPort).transitionDealStatus(dealId, DealStatus.DELIVERABLES_SUBMITTED);

        // Verify Audit event recorded
        verify(auditEventService).recordEvent(eq(dealId), eq("DELIVERABLE_UPLOADED"), any());

        // Verify download URL signed with HMAC
        assertThat(dto.downloadSignedUrl()).contains("/api/v1/deals/" + dealId + "/evidence/");
        assertThat(dto.downloadSignedUrl()).contains("sig=");
    }

    @Test
    void storeEvidence_secondUpload_incrementsVersion() {
        UUID dealId = UUID.randomUUID();
        String sellerEmail = "seller@test.local";
        when(dealPort.getSellerEmail(dealId)).thenReturn(sellerEmail);

        MockMultipartFile file1 = new MockMultipartFile("file", "v1.pdf", "application/pdf", "data1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "v2.pdf", "application/pdf", "data2".getBytes());

        DeliverableEvidenceDTO dto1 = storageService.storeEvidence(dealId, file1, sellerEmail);
        DeliverableEvidenceDTO dto2 = storageService.storeEvidence(dealId, file2, sellerEmail);

        assertThat(dto1.version()).isEqualTo(1);
        assertThat(dto2.version()).isEqualTo(2);

        List<DeliverableEvidenceDTO> list = storageService.listEvidenceForDeal(dealId, sellerEmail);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).version()).isEqualTo(1);
        assertThat(list.get(1).version()).isEqualTo(2);
    }

    @Test
    void storeEvidence_nonSellerCaller_rejectedWithForbidden() {
        UUID dealId = UUID.randomUUID();
        String sellerEmail = "seller@test.local";
        String buyerEmail = "buyer@test.local";
        when(dealPort.getSellerEmail(dealId)).thenReturn(sellerEmail);

        MockMultipartFile file = new MockMultipartFile("file", "v1.zip", "application/zip", "zip".getBytes());

        assertThatThrownBy(() -> storageService.storeEvidence(dealId, file, buyerEmail))
                .isInstanceOf(SettlementForbiddenException.class)
                .hasMessageContaining("Only the authorized SELLER");
    }

    @Test
    void storeEvidence_disallowedExtensionOrMime_rejectedWithBadRequest() {
        UUID dealId = UUID.randomUUID();
        String sellerEmail = "seller@test.local";
        when(dealPort.getSellerEmail(dealId)).thenReturn(sellerEmail);

        // Malicious executable
        MockMultipartFile file1 = new MockMultipartFile("file", "payload.exe", "application/x-msdownload", "bad".getBytes());
        assertThatThrownBy(() -> storageService.storeEvidence(dealId, file1, sellerEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid file extension");

        // Disallowed script
        MockMultipartFile file2 = new MockMultipartFile("file", "hack.sh", "text/plain", "#!/bin/sh".getBytes());
        assertThatThrownBy(() -> storageService.storeEvidence(dealId, file2, sellerEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid file extension");
    }

    @Test
    void storeEvidence_exceeding10MB_rejected() {
        UUID dealId = UUID.randomUUID();
        String sellerEmail = "seller@test.local";
        when(dealPort.getSellerEmail(dealId)).thenReturn(sellerEmail);

        byte[] hugeContent = new byte[(int) (EvidenceStorageService.MAX_FILE_SIZE_BYTES + 10)];
        MockMultipartFile hugeFile = new MockMultipartFile("file", "huge.zip", "application/zip", hugeContent);

        assertThatThrownBy(() -> storageService.storeEvidence(dealId, hugeFile, sellerEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds 10MB");
    }

    @Test
    void storeEvidence_pathTraversalFilename_sanitizedSafely() {
        UUID dealId = UUID.randomUUID();
        String sellerEmail = "seller@test.local";
        when(dealPort.getSellerEmail(dealId)).thenReturn(sellerEmail);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../../etc/passwd.zip",
                "application/zip",
                "content".getBytes()
        );

        DeliverableEvidenceDTO dto = storageService.storeEvidence(dealId, file, sellerEmail);
        assertThat(dto.filename()).doesNotContain("..");
        assertThat(dto.filename()).doesNotContain("/");
        assertThat(dto.filename()).doesNotContain("\\");
    }

    @Test
    void loadEvidenceResource_withValidSignature_succeeds() throws Exception {
        UUID dealId = UUID.randomUUID();
        String sellerEmail = "seller@test.local";
        String buyerEmail = "buyer@test.local";
        when(dealPort.getSellerEmail(dealId)).thenReturn(sellerEmail);

        MockMultipartFile file = new MockMultipartFile("file", "deliverable.pdf", "application/pdf", "test-content".getBytes());
        DeliverableEvidenceDTO dto = storageService.storeEvidence(dealId, file, sellerEmail);

        UUID evidenceId = UUID.fromString(dto.evidenceId());
        String url = dto.downloadSignedUrl();
        long expires = Long.parseLong(url.substring(url.indexOf("expires=") + 8, url.indexOf("&sig=")));
        String signature = url.substring(url.indexOf("sig=") + 4);

        // Buyer downloads evidence
        Resource resource = storageService.loadEvidenceResource(dealId, evidenceId, expires, signature, buyerEmail);
        assertThat(resource.exists()).isTrue();
        assertThat(resource.contentLength()).isEqualTo("test-content".length());
    }

    @Test
    void loadEvidenceResource_withExpiredSignature_rejected() {
        UUID dealId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        long expiredTimestamp = Instant.now().getEpochSecond() - 100; // in the past

        assertThatThrownBy(() -> storageService.loadEvidenceResource(dealId, evidenceId, expiredTimestamp, "fake-sig", "buyer@test.local"))
                .isInstanceOf(SettlementForbiddenException.class)
                .hasMessageContaining("Invalid or expired evidence download signature");
    }
}
