package com.payagent.evidence.web;

import com.payagent.common.contracts.DeliverableEvidenceDTO;
import com.payagent.evidence.persistence.EvidenceEntity;
import com.payagent.evidence.service.EvidenceStorageService;
import com.payagent.settlement.IdempotencyService;
import com.payagent.settlement.exception.IdempotencyConflictException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/deals/{id}/evidence")
public class EvidenceController {

    private final EvidenceStorageService evidenceStorageService;
    private final IdempotencyService idempotencyService;

    public EvidenceController(EvidenceStorageService evidenceStorageService, IdempotencyService idempotencyService) {
        this.evidenceStorageService = evidenceStorageService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeliverableEvidenceDTO> uploadEvidence(
            @PathVariable("id") UUID dealId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        String callerEmail = principal != null ? principal.getName() : null;
        String route = "/api/v1/deals/" + dealId + "/evidence";
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("dealId", dealId.toString());
        payload.put("callerEmail", callerEmail == null ? "" : callerEmail);
        payload.put("filename", file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        payload.put("size", file.getSize());
        payload.put("sha256", sha256Hex(file));
        return idempotencyService.execute(
                idempotencyKey,
                route,
                dealId,
                payload,
                DeliverableEvidenceDTO.class,
                () -> ResponseEntity.ok(evidenceStorageService.storeEvidence(dealId, file, callerEmail))
        );
    }

    private String sha256Hex(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(file.getBytes()));
        } catch (NoSuchAlgorithmException | java.io.IOException e) {
            throw new IllegalArgumentException("Unable to compute evidence file hash", e);
        }
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, String>> handleIdempotencyConflict(IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "IDEMPOTENCY_CONFLICT", "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "BAD_REQUEST", "message", ex.getMessage()));
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
