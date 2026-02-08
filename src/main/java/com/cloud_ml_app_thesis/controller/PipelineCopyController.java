package com.cloud_ml_app_thesis.controller;

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
import org.springframework.security.core.userdetails.UserDetails;
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
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (request.isGroupCopy()) {
            List<PipelineCopyResponse> responses = pipelineCopyService.copyPipelineToGroup(
                    trainingId, request.getTargetGroupId(), userDetails
            );
            return ResponseEntity.ok(responses);
        } else {
            PipelineCopyResponse response = pipelineCopyService.copyPipeline(
                    trainingId, request.getTargetUsername(), userDetails
            );
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/{trainingId}/copy-to-group/{groupId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PipelineCopyResponse>> copyPipelineToGroup(
            @PathVariable Integer trainingId,
            @PathVariable Integer groupId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<PipelineCopyResponse> responses = pipelineCopyService.copyPipelineToGroup(
                trainingId, groupId, userDetails
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
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<PipelineCopy> copies = pipelineCopyService.getCopiesInitiatedByUser(userDetails);
        return ResponseEntity.ok(
                copies.stream().map(this::mapToResponse).collect(Collectors.toList())
        );
    }

    @GetMapping("/copies/received")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PipelineCopyResponse>> getMyReceivedCopies(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<PipelineCopy> copies = pipelineCopyService.getCopiesReceivedByUser(userDetails);
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
