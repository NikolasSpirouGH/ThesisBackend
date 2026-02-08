package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.pipeline.PipelineCopyResponse;
import com.cloud_ml_app_thesis.dto.request.pipeline.PipelineCopyRequest;
import com.cloud_ml_app_thesis.entity.pipeline.PipelineCopy;
import com.cloud_ml_app_thesis.enumeration.status.PipelineCopyStatusEnum;
import com.cloud_ml_app_thesis.service.PipelineCopyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineCopyController {

    private final PipelineCopyService pipelineCopyService;

    @PostMapping("/{trainingId}/copy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> copyPipeline(
            @PathVariable Integer trainingId,
            @Valid @RequestBody PipelineCopyRequest request,
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        if (request.isGroupCopy()) {
            List<PipelineCopyResponse> responses = pipelineCopyService.copyPipelineToGroup(
                    trainingId, request.getTargetGroupId(), accountDetails
            );
            return ResponseEntity.ok(responses);
        } else {
            PipelineCopyResponse response = pipelineCopyService.copyPipeline(
                    trainingId, request.getTargetUsername(), accountDetails
            );
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/{trainingId}/copy-to-group/{groupId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PipelineCopyResponse>> copyPipelineToGroup(
            @PathVariable Integer trainingId,
            @PathVariable Integer groupId,
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        List<PipelineCopyResponse> responses = pipelineCopyService.copyPipelineToGroup(
                trainingId, groupId, accountDetails
        );
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/copy/{pipelineCopyId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PipelineCopyResponse> getPipelineCopy(
            @PathVariable Integer pipelineCopyId
    ) {
        PipelineCopy copy = pipelineCopyService.getPipelineCopyWithMappings(pipelineCopyId);
        if (copy == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mapToResponse(copy));
    }

    @GetMapping("/copies/sent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PipelineCopyResponse>> getMySentCopies(
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        List<PipelineCopy> copies = pipelineCopyService.getCopiesInitiatedByUser(accountDetails);
        return ResponseEntity.ok(
                copies.stream().map(this::mapToResponse).collect(Collectors.toList())
        );
    }

    @GetMapping("/copies/received")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PipelineCopyResponse>> getMyReceivedCopies(
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        List<PipelineCopy> copies = pipelineCopyService.getCopiesReceivedByUser(accountDetails);
        return ResponseEntity.ok(
                copies.stream().map(this::mapToResponse).collect(Collectors.toList())
        );
    }

    private PipelineCopyResponse mapToResponse(PipelineCopy copy) {
        Map<String, PipelineCopyResponse.EntityMapping> mappings = copy.getMappings().stream()
                .collect(Collectors.toMap(
                        m -> m.getEntityType().name(),
                        m -> PipelineCopyResponse.EntityMapping.builder()
                                .sourceId(m.getSourceEntityId())
                                .targetId(m.getTargetEntityId())
                                .minioSourceKey(m.getMinioSourceKey())
                                .minioTargetKey(m.getMinioTargetKey())
                                .status(m.getStatus().name())
                                .build()
                ));

        return PipelineCopyResponse.builder()
                .pipelineCopyId(copy.getId())
                .sourceTrainingId(copy.getSourceTraining().getId())
                .targetTrainingId(copy.getTargetTraining() != null ? copy.getTargetTraining().getId() : null)
                .copiedByUsername(copy.getCopiedByUser().getUsername())
                .copyForUsername(copy.getCopyForUser().getUsername())
                .copyDate(copy.getCopyDate())
                .status(copy.getStatus())
                .errorMessage(copy.getErrorMessage())
                .mappings(mappings)
                .build();
    }
}
