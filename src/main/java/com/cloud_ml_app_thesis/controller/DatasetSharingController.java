package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.dto.request.dataset.DatasetRemoveSharedUsersRequest;
import com.cloud_ml_app_thesis.dto.request.dataset.DatasetShareRequest;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.service.DatasetShareService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dataset/share")
@RequiredArgsConstructor
public class DatasetSharingController {

    private final DatasetShareService datasetShareService;

    @PostMapping("/{datasetId}")
    public ResponseEntity<Void> shareDataset(
            @PathVariable Integer datasetId,
            @RequestBody DatasetShareRequest request,
            Authentication authentication
    ) {
        String sharedByUsername = authentication.getName();
        datasetShareService.shareDatasetWithUsers(datasetId, request.getUsernames(), sharedByUsername, request.getComment());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{datasetId}/share")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeSharedUsers(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer datasetId,
            @RequestBody DatasetRemoveSharedUsersRequest request
    ) {
        datasetShareService.removeUsersFromSharedDataset(userDetails, datasetId, request.getUsernames(), request.getComments());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{datasetId}/copy")
    public ResponseEntity<Dataset> copyDataset(
            @PathVariable Integer datasetId,
            @RequestParam(required = false) String targetUsername,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();

        Dataset copied = datasetShareService.copySharedDataset(
                datasetId,
                currentUser,
                targetUsername
        );

        return ResponseEntity.ok(copied);
    }

    @PostMapping("/{datasetId}/decline")
    @PreAuthorize("hasAnyAuthority('USER', 'DATASET_MANAGER', 'ADMIN')")
    public ResponseEntity<Void> declineDatasetShare(
            @PathVariable Integer datasetId,
            @RequestParam(required = false) @Size(max = 50) String targetUsername,
            @RequestParam(required = false) @Size(max = 100) String comments,
            @AuthenticationPrincipal UserDetails userDetails
    ) {

        datasetShareService.declineDatasetShare(datasetId, targetUsername, comments, userDetails);
        return ResponseEntity.ok().build();
    }
}
