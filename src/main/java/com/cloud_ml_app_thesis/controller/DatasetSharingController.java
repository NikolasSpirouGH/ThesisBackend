package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.dataset.DatasetRemoveSharedUsersRequest;
import com.cloud_ml_app_thesis.dto.request.dataset.DatasetShareRequest;
import com.cloud_ml_app_thesis.dto.request.share.GroupShareRequest;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.service.DatasetShareService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        String sharedByUsername = accountDetails.getUsername();
        datasetShareService.shareDatasetWithUsers(datasetId, request.getUsernames(), sharedByUsername, request.getComment());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{datasetId}/share")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeSharedUsers(
            @AuthenticationPrincipal AccountDetails accountDetails,
            @PathVariable Integer datasetId,
            @RequestBody DatasetRemoveSharedUsersRequest request
    ) {
        datasetShareService.removeUsersFromSharedDataset(accountDetails, datasetId, request.getUsernames(), request.getComments());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{datasetId}/copy")
    public ResponseEntity<Dataset> copyDataset(
            @PathVariable Integer datasetId,
            @RequestParam(required = false) String targetUsername,
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        User currentUser = accountDetails.getUser();

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
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {

        datasetShareService.declineDatasetShare(datasetId, targetUsername, comments, accountDetails);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{datasetId}/share-group")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> shareDatasetWithGroup(
            @PathVariable Integer datasetId,
            @Valid @RequestBody GroupShareRequest request,
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        String sharedByUsername = accountDetails.getUsername();
        datasetShareService.shareDatasetWithGroup(datasetId, request.getGroupId(), sharedByUsername, request.getComment());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{datasetId}/share-group/{groupId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unshareDatasetFromGroup(
            @PathVariable Integer datasetId,
            @PathVariable Integer groupId,
            @RequestParam(required = false) String comment,
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        datasetShareService.removeGroupFromSharedDataset(accountDetails, datasetId, groupId, comment);
        return ResponseEntity.ok().build();
    }
}
