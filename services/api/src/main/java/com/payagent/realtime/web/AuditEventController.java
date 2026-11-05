package com.payagent.realtime.web;

import com.payagent.auth.UserRepository;
import com.payagent.realtime.model.AuditEventDTO;
import com.payagent.realtime.service.AuditEventService;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deals/{id}/events")
public class AuditEventController {

    private final AuditEventService auditEventService;
    private final UserRepository userRepository;

    public AuditEventController(AuditEventService auditEventService, UserRepository userRepository) {
        this.auditEventService = auditEventService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<AuditEventDTO>> getDealTimeline(
            @PathVariable("id") UUID dealId,
            @RequestParam(name = "afterSeq", defaultValue = "0") long afterSeq,
            Principal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("Unauthenticated");
        }
        String name = principal.getName();
        UUID userId;
        try {
            userId = UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid principal: expected UUID");
        }
        String callerEmail = userRepository.findById(userId)
                .map(u -> u.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<AuditEventDTO> events = auditEventService.getTimeline(dealId, afterSeq, callerEmail);
        return ResponseEntity.ok(events);
    }
}
