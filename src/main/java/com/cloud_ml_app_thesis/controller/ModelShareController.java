package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.model.ModelShareRequest;
import com.cloud_ml_app_thesis.dto.request.share.GroupShareRequest;
import com.cloud_ml_app_thesis.service.ModelShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/models/sharing")
@RequiredArgsConstructor
public class ModelShareController {

    private final ModelShareService modelShareService;

    @PostMapping("/{modelId}/share")
    public ResponseEntity<Void> shareModel(
            @PathVariable Integer modelId,
            @RequestBody ModelShareRequest request,
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        modelShareService.shareModelWithUsers(
                modelId,
                request.getUsernames(),
                accountDetails.getUsername(),
                request.getComment()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{modelId}/revoke")
    public ResponseEntity<Void> revokeModelShares(
            @PathVariable Integer modelId,
            @RequestBody ModelShareRequest request,
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        modelShareService.revokeModelShares(
                accountDetails,
                modelId,
                request.getUsernames(), // null or empty means revoke all
                request.getComment()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{modelId}/share-group")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> shareModelWithGroup(
            @PathVariable Integer modelId,
            @Valid @RequestBody GroupShareRequest request,
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        modelShareService.shareModelWithGroup(
                modelId,
                request.getGroupId(),
                accountDetails.getUsername(),
                request.getComment()
        );
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{modelId}/share-group/{groupId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unshareModelFromGroup(
            @PathVariable Integer modelId,
            @PathVariable Integer groupId,
            @RequestParam(required = false) String comment,
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        modelShareService.revokeModelGroupShare(accountDetails, modelId, groupId, comment);
        return ResponseEntity.ok().build();
    }
}
