package com.payagent.realtime.web;

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

    public AuditEventController(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @GetMapping
    public ResponseEntity<List<AuditEventDTO>> getDealTimeline(
            @PathVariable("id") UUID dealId,
            @RequestParam(name = "afterSeq", defaultValue = "0") long afterSeq,
            Principal principal) {
        String callerEmail = principal != null ? principal.getName() : null;
        List<AuditEventDTO> events = auditEventService.getTimeline(dealId, afterSeq, callerEmail);
        return ResponseEntity.ok(events);
    }
}
