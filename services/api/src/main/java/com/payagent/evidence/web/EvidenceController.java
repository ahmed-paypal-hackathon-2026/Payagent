package com.payagent.evidence.web;

import com.payagent.common.contracts.DeliverableEvidenceDTO;
import com.payagent.evidence.persistence.EvidenceEntity;
import com.payagent.evidence.service.EvidenceStorageService;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/deals/{id}/evidence")
public class EvidenceController {

    private final EvidenceStorageService evidenceStorageService;

    public EvidenceController(EvidenceStorageService evidenceStorageService) {
        this.evidenceStorageService = evidenceStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeliverableEvidenceDTO> uploadEvidence(
            @PathVariable("id") UUID dealId,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        String callerEmail = principal != null ? principal.getName() : null;
        DeliverableEvidenceDTO dto = evidenceStorageService.storeEvidence(dealId, file, callerEmail);
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    public ResponseEntity<List<DeliverableEvidenceDTO>> listEvidence(
            @PathVariable("id") UUID dealId,
            Principal principal) {
        String callerEmail = principal != null ? principal.getName() : null;
        List<DeliverableEvidenceDTO> list = evidenceStorageService.listEvidenceForDeal(dealId, callerEmail);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{evidenceId}/download")
    public ResponseEntity<Resource> downloadEvidence(
            @PathVariable("id") UUID dealId,
            @PathVariable("evidenceId") UUID evidenceId,
            @RequestParam("expires") long expires,
            @RequestParam("sig") String signature,
            Principal principal) {
        String callerEmail = principal != null ? principal.getName() : null;
        Resource resource = evidenceStorageService.loadEvidenceResource(dealId, evidenceId, expires, signature, callerEmail);
        EvidenceEntity metadata = evidenceStorageService.getEvidenceMetadata(dealId, evidenceId);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(metadata.getMimeType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.getFilename() + "\"")
                .body(resource);
    }
}
