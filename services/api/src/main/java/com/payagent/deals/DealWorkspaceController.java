package com.payagent.deals;

import com.payagent.common.contracts.DealWorkspaceDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deals/{id}/workspace")
public class DealWorkspaceController {

    private final DealWorkspaceService workspaceService;

    public DealWorkspaceController(DealWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ResponseEntity<DealWorkspaceDTO> getWorkspace(
            @PathVariable("id") UUID dealId,
            @RequestAttribute(value = "userId", required = false) UUID userIdAttr,
            Principal principal) {
        UUID userId = resolveUserId(userIdAttr, principal);
        DealWorkspaceDTO workspace = workspaceService.getWorkspace(dealId, userId);
        return ResponseEntity.ok(workspace);
    }

    private UUID resolveUserId(UUID userIdAttr, Principal principal) {
        if (userIdAttr != null) {
            return userIdAttr;
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid principal");
        }
    }
}
