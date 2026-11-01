package com.payagent.evidence.persistence;

import com.payagent.common.contracts.PartyRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evidence")
public class EvidenceEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "deal_id", nullable = false)
    private UUID dealId;

    @Enumerated(EnumType.STRING)
    @Column(name = "uploader_role", length = 16, nullable = false)
    private PartyRole uploaderRole;

    @Column(name = "filename", length = 512, nullable = false)
    private String filename;

    @Column(name = "mime_type", length = 128, nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256_hash", length = 64, nullable = false)
    private String sha256Hash;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "storage_key", length = 1024, nullable = false)
    private String storageKey;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    public EvidenceEntity() {}

    public EvidenceEntity(
            UUID id,
            UUID dealId,
            PartyRole uploaderRole,
            String filename,
            String mimeType,
            long sizeBytes,
            String sha256Hash,
            int version,
            String storageKey) {
        this.id = id;
        this.dealId = dealId;
        this.uploaderRole = uploaderRole;
        this.filename = filename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256Hash = sha256Hash;
        this.version = version;
        this.storageKey = storageKey;
        this.uploadedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDealId() {
        return dealId;
    }

    public void setDealId(UUID dealId) {
        this.dealId = dealId;
    }

    public PartyRole getUploaderRole() {
        return uploaderRole;
    }

    public void setUploaderRole(PartyRole uploaderRole) {
        this.uploaderRole = uploaderRole;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
