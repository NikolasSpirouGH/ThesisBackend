package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.dto.pipeline.PipelineCopyResponse;
import com.cloud_ml_app_thesis.entity.pipeline.PipelineCopy;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

public interface PipelineCopyService {

    /**
     * Copy a complete pipeline (training + all related entities) to a target user.
     * Returns PipelineCopy with all mappings (originalId -> newId).
     *
     * @param trainingId     the source training ID
     * @param targetUsername the username to copy the pipeline to (null = copy to self)
     * @param userDetails    the authenticated user performing the copy
     * @return the response with copy details and provenance mappings
     */
    PipelineCopyResponse copyPipeline(Integer trainingId, String targetUsername, UserDetails userDetails);

    /**
     * Copy a pipeline to all members of a group.
     *
     * @param trainingId  the source training ID
     * @param groupId     the group ID
     * @param userDetails the authenticated user performing the copy
     * @return list of responses for each group member
     */
    List<PipelineCopyResponse> copyPipelineToGroup(Integer trainingId, Integer groupId, UserDetails userDetails);

    /**
     * Get a pipeline copy with all its mappings.
     *
     * @param pipelineCopyId the pipeline copy ID
     * @return the pipeline copy with mappings
     */
    PipelineCopy getPipelineCopyWithMappings(Integer pipelineCopyId);

    /**
     * Get all pipeline copies initiated by the user.
     *
     * @param userDetails the authenticated user
     * @return list of pipeline copies
     */
    List<PipelineCopy> getCopiesInitiatedByUser(UserDetails userDetails);

    /**
     * Get all pipeline copies received by the user.
     *
     * @param userDetails the authenticated user
     * @return list of pipeline copies
     */
    List<PipelineCopy> getCopiesReceivedByUser(UserDetails userDetails);
}
