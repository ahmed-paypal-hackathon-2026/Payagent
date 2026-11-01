package com.payagent.evidence.service;

import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.DeliverableEvidenceDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.evidence.persistence.EvidenceEntity;
import com.payagent.evidence.persistence.EvidenceRepository;
import com.payagent.settlement.exception.SettlementConflictException;
import com.payagent.settlement.exception.SettlementForbiddenException;
import com.payagent.settlement.exception.SettlementNotFoundException;
import com.payagent.settlement.port.AuditEventPort;
import com.payagent.settlement.port.DealPort;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EvidenceStorageService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceStorageService.class);

    public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/zip",
            "application/x-zip-compressed",
            "application/pdf",
            "image/png"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".zip",
            ".pdf",
            ".png"
    );

    private final EvidenceRepository evidenceRepository;
    private final DealPort dealPort;
    private final AuditEventPort auditEventPort;
    private final EvidenceSignatureService signatureService;
    private final Path rootStoragePath;

    public EvidenceStorageService(
            EvidenceRepository evidenceRepository,
            DealPort dealPort,
            AuditEventPort auditEventPort,
            EvidenceSignatureService signatureService,
            @Value("${payagent.evidence.storage-path:./data/evidence}") String storagePathStr) {
        this.evidenceRepository = evidenceRepository;
        this.dealPort = dealPort;
        this.auditEventPort = auditEventPort;
        this.signatureService = signatureService;
        this.rootStoragePath = Paths.get(storagePathStr).toAbsolutePath().normalize();
    }

    @Transactional
    public DeliverableEvidenceDTO storeEvidence(UUID dealId, MultipartFile file, String callerEmail) {
        dealPort.assertParty(dealId, callerEmail);
        assertSellerRole(dealId, callerEmail);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Evidence file must not be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds 10MB limit: " + file.getSize() + " bytes");
        }

        String rawFilename = file.getOriginalFilename();
        String safeFilename = sanitizeFilename(rawFilename);
        validateMimeAndExtension(file.getContentType(), safeFilename);

        UUID evidenceId = UUID.randomUUID();
        Path targetDir = rootStoragePath.resolve(dealId.toString()).normalize();
        if (!targetDir.startsWith(rootStoragePath)) {
            throw new SecurityException("Directory traversal attack detected");
        }

        try {
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(evidenceId + "_" + safeFilename).normalize();
            if (!targetFile.startsWith(targetDir)) {
                throw new SecurityException("File path traversal attack detected");
            }

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream();
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                Files.copy(dis, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }

            String sha256 = HexFormat.of().formatHex(md.digest());
            int nextVersion = evidenceRepository.findMaxVersionByDealId(dealId) + 1;
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

            EvidenceEntity entity = new EvidenceEntity(
                    evidenceId,
                    dealId,
                    PartyRole.SELLER,
                    safeFilename,
                    contentType,
                    file.getSize(),
                    sha256,
                    nextVersion,
                    targetFile.toString()
            );
            evidenceRepository.save(entity);

            // Update deal status to DELIVERABLES_SUBMITTED
            dealPort.transitionDealStatus(dealId, DealStatus.DELIVERABLES_SUBMITTED);

            // Record audit event
            auditEventPort.recordEvent(dealId, "DELIVERABLE_UPLOADED", Map.of(
                    "evidenceId", evidenceId.toString(),
                    "filename", safeFilename,
                    "mimeType", contentType,
                    "sizeBytes", file.getSize(),
                    "sha256Hash", sha256,
                    "version", nextVersion
            ));

            String downloadUrl = signatureService.generateDownloadUrl(dealId, evidenceId);
            return toDTO(entity, downloadUrl);

        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("Failed to store evidence file for deal {}: {}", dealId, e.getMessage(), e);
            throw new IllegalStateException("Failed to store evidence file: " + e.getMessage(), e);
        }
    }

    public List<DeliverableEvidenceDTO> listEvidenceForDeal(UUID dealId, String callerEmail) {
        dealPort.assertParty(dealId, callerEmail);
        List<EvidenceEntity> entities = evidenceRepository.findByDealIdOrderByVersionAsc(dealId);
        return entities.stream()
                .map(e -> toDTO(e, signatureService.generateDownloadUrl(dealId, e.getId())))
                .toList();
    }

    public Resource loadEvidenceResource(UUID dealId, UUID evidenceId, long expires, String signature, String callerEmail) {
        dealPort.assertParty(dealId, callerEmail);

        if (!signatureService.verifySignature(dealId, evidenceId, expires, signature)) {
            throw new SettlementForbiddenException("Invalid or expired evidence download signature");
        }

        EvidenceEntity entity = evidenceRepository.findByIdAndDealId(evidenceId, dealId)
                .orElseThrow(() -> new SettlementNotFoundException("Evidence not found: " + evidenceId));

        Path filePath = Paths.get(entity.getStorageKey()).normalize();
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new SettlementNotFoundException("Evidence file not found on disk: " + evidenceId);
        }

        try {
            return new UrlResource(filePath.toUri());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load file resource: " + e.getMessage(), e);
        }
    }

    public EvidenceEntity getEvidenceMetadata(UUID dealId, UUID evidenceId) {
        return evidenceRepository.findByIdAndDealId(evidenceId, dealId)
                .orElseThrow(() -> new SettlementNotFoundException("Evidence not found: " + evidenceId));
    }

    private void assertSellerRole(UUID dealId, String callerEmail) {
        String sellerEmail = dealPort.getSellerEmail(dealId);
        if (sellerEmail == null || callerEmail == null || !callerEmail.trim().equalsIgnoreCase(sellerEmail.trim())) {
            throw new SettlementForbiddenException("Only the authorized SELLER can upload deliverable evidence");
        }
    }

    private void validateMimeAndExtension(String mimeType, String filename) {
        String lowerFilename = filename.toLowerCase();
        boolean validExt = ALLOWED_EXTENSIONS.stream().anyMatch(lowerFilename::endsWith);
        if (!validExt) {
            throw new IllegalArgumentException("Invalid file extension. Allowed: .zip, .pdf, .png");
        }

        if (mimeType != null && !mimeType.isBlank()) {
            String cleanMime = mimeType.toLowerCase().split(";")[0].trim();
            if (!ALLOWED_MIME_TYPES.contains(cleanMime)) {
                throw new IllegalArgumentException("Invalid MIME type: " + mimeType + ". Allowed: " + ALLOWED_MIME_TYPES);
            }
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "deliverable.bin";
        }
        // Take only basename (remove ../ and path separators)
        String name = Paths.get(filename).getFileName().toString();
        // Remove special or dangerous characters
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.isBlank() || name.equals(".")) {
            return "deliverable.bin";
        }
        return name;
    }

    private DeliverableEvidenceDTO toDTO(EvidenceEntity entity, String downloadUrl) {
        return new DeliverableEvidenceDTO(
                entity.getId().toString(),
                entity.getDealId().toString(),
                entity.getUploaderRole(),
                entity.getFilename(),
                entity.getMimeType(),
                entity.getSizeBytes(),
                entity.getSha256Hash(),
                entity.getVersion(),
                downloadUrl,
                entity.getUploadedAt().toString()
        );
    }
}
