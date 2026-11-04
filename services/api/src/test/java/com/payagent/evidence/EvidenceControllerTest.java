package com.payagent.evidence;

import com.payagent.common.contracts.DeliverableEvidenceDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.evidence.persistence.EvidenceEntity;
import com.payagent.evidence.service.EvidenceStorageService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvidenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EvidenceStorageService evidenceStorageService;

    @Test
    @WithMockUser(username = "seller@test.local")
    void uploadEvidence_validFile_returnsDTO() throws Exception {
        UUID dealId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();

        DeliverableEvidenceDTO dto = new DeliverableEvidenceDTO(
                evidenceId.toString(),
                dealId.toString(),
                PartyRole.SELLER,
                "deliverable.zip",
                "application/zip",
                1024L,
                "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
                1,
                "/api/v1/deals/" + dealId + "/evidence/" + evidenceId + "/download?expires=123&sig=abc",
                "2026-10-22T14:00:00Z"
        );

        when(evidenceStorageService.storeEvidence(eq(dealId), any(), eq("seller@test.local")))
                .thenReturn(dto);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "deliverable.zip",
                "application/zip",
                "test-content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/deals/{id}/evidence", dealId)
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceId").value(evidenceId.toString()))
                .andExpect(jsonPath("$.filename").value("deliverable.zip"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @WithMockUser(username = "buyer@test.local")
    void listEvidence_returnsList() throws Exception {
        UUID dealId = UUID.randomUUID();
        DeliverableEvidenceDTO dto = new DeliverableEvidenceDTO(
                UUID.randomUUID().toString(),
                dealId.toString(),
                PartyRole.SELLER,
                "doc.pdf",
                "application/pdf",
                500L,
                "hash",
                1,
                "http://localhost/download",
                "2026-10-22T14:00:00Z"
        );

        when(evidenceStorageService.listEvidenceForDeal(eq(dealId), eq("buyer@test.local")))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/deals/{id}/evidence", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("doc.pdf"));
    }

    @Test
    @WithMockUser(username = "buyer@test.local")
    void downloadEvidence_withSignature_returnsFileAttachment() throws Exception {
        UUID dealId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();

        ByteArrayResource resource = new ByteArrayResource("file-binary-data".getBytes());
        EvidenceEntity metadata = new EvidenceEntity(
                evidenceId,
                dealId,
                PartyRole.SELLER,
                "result.pdf",
                "application/pdf",
                16L,
                "hash",
                1,
                "/path/to/file"
        );

        when(evidenceStorageService.loadEvidenceResource(eq(dealId), eq(evidenceId), anyLong(), eq("valid-sig"), eq("buyer@test.local")))
                .thenReturn(resource);
        when(evidenceStorageService.getEvidenceMetadata(eq(dealId), eq(evidenceId)))
                .thenReturn(metadata);

        mockMvc.perform(get("/api/v1/deals/{id}/evidence/{evidenceId}/download", dealId, evidenceId)
                        .param("expires", "9999999999")
                        .param("sig", "valid-sig"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"result.pdf\""))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes("file-binary-data".getBytes()));
    }
}
