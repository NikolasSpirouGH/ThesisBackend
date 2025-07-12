package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.dto.request.model.ModelShareRequest;
import com.cloud_ml_app_thesis.service.ModelShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/models/sharing")
@RequiredArgsConstructor
public class ModelShareController {

    private final ModelShareService modelShareService;

    @PostMapping("/{modelId}/share")
    public ResponseEntity<Void> shareModel(
            @PathVariable Integer modelId,
            @RequestBody ModelShareRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        modelShareService.shareModelWithUsers(
                modelId,
                request.getUsernames(),
                userDetails.getUsername(),
                request.getComment()
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{modelId}/revoke")
    public ResponseEntity<Void> revokeModelShares(
            @PathVariable Integer modelId,
            @RequestBody ModelShareRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        modelShareService.revokeModelShares(
                userDetails,
                modelId,
                request.getUsernames(), // null or empty means revoke all
                request.getComment()
        );
        return ResponseEntity.ok().build();
    }
}
