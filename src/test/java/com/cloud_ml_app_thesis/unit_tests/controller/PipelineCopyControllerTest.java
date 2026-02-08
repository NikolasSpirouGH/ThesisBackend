package com.cloud_ml_app_thesis.unit_tests.controller;

import com.cloud_ml_app_thesis.controller.PipelineCopyController;
import com.cloud_ml_app_thesis.dto.pipeline.PipelineCopyResponse;
import com.cloud_ml_app_thesis.dto.request.pipeline.PipelineCopyRequest;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.pipeline.PipelineCopy;
import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.enumeration.status.PipelineCopyStatusEnum;
import com.cloud_ml_app_thesis.service.PipelineCopyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.ZonedDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PipelineCopyControllerTest {

    @Mock
    private PipelineCopyService pipelineCopyService;

    @InjectMocks
    private PipelineCopyController pipelineCopyController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private User testUser;
    private User targetUser;
    private Training sourceTraining;
    private Training targetTraining;
    private PipelineCopy pipelineCopy;
    private PipelineCopyResponse copyResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(pipelineCopyController).build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("owner");
        testUser.setEmail("owner@example.com");

        targetUser = new User();
        targetUser.setId(UUID.randomUUID());
        targetUser.setUsername("targetUser");
        targetUser.setEmail("target@example.com");

        sourceTraining = new Training();
        sourceTraining.setId(1);
        sourceTraining.setUser(testUser);

        targetTraining = new Training();
        targetTraining.setId(2);
        targetTraining.setUser(targetUser);

        pipelineCopy = new PipelineCopy();
        pipelineCopy.setId(1);
        pipelineCopy.setSourceTraining(sourceTraining);
        pipelineCopy.setTargetTraining(targetTraining);
        pipelineCopy.setCopiedByUser(testUser);
        pipelineCopy.setCopyForUser(targetUser);
        pipelineCopy.setCopyDate(ZonedDateTime.now());
        pipelineCopy.setStatus(PipelineCopyStatusEnum.COMPLETED);

        copyResponse = PipelineCopyResponse.builder()
                .pipelineCopyId(1)
                .sourceTrainingId(1)
                .targetTrainingId(2)
                .copiedByUsername("owner")
                .copyForUsername("targetUser")
                .copyDate(ZonedDateTime.now())
                .status(PipelineCopyStatusEnum.COMPLETED)
                .mappings(new HashMap<>())
                .build();
    }

    @Nested
    @DisplayName("Copy Pipeline Tests")
    class CopyPipelineTests {

        @Test
        @DisplayName("Should copy pipeline to user successfully")
        void copyPipelineToUser_Success() throws Exception {
            // Given
            PipelineCopyRequest request = new PipelineCopyRequest();
            request.setTargetUsername("targetUser");

            when(pipelineCopyService.copyPipeline(eq(1), eq("targetUser"), any(UserDetails.class)))
                    .thenReturn(copyResponse);

            // When/Then
            mockMvc.perform(post("/api/pipeline/1/copy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .principal(() -> "owner"))
                    .andExpect(status().isOk());

            verify(pipelineCopyService).copyPipeline(eq(1), eq("targetUser"), any(UserDetails.class));
        }
    }

    @Nested
    @DisplayName("Copy Pipeline to Group Tests")
    class CopyPipelineToGroupTests {

        @Test
        @DisplayName("Should copy pipeline to group successfully")
        void copyPipelineToGroup_Success() throws Exception {
            // Given
            List<PipelineCopyResponse> responses = List.of(copyResponse);
            when(pipelineCopyService.copyPipelineToGroup(eq(1), eq(1), any(UserDetails.class)))
                    .thenReturn(responses);

            // When/Then
            mockMvc.perform(post("/api/pipeline/1/copy-to-group/1")
                            .principal(() -> "owner"))
                    .andExpect(status().isOk());

            verify(pipelineCopyService).copyPipelineToGroup(eq(1), eq(1), any(UserDetails.class));
        }
    }

    @Nested
    @DisplayName("Get Pipeline Copy Tests")
    class GetPipelineCopyTests {

        @Test
        @DisplayName("Should get pipeline copy with mappings")
        void getPipelineCopyWithMappings_Success() throws Exception {
            // Given
            when(pipelineCopyService.getPipelineCopyWithMappings(eq(1)))
                    .thenReturn(pipelineCopy);

            // When/Then
            mockMvc.perform(get("/api/pipeline/copy/1")
                            .principal(() -> "owner"))
                    .andExpect(status().isOk());

            verify(pipelineCopyService).getPipelineCopyWithMappings(eq(1));
        }
    }

    @Nested
    @DisplayName("Get Copies Sent/Received Tests")
    class GetCopiesTests {

        @Test
        @DisplayName("Should get copies initiated by user")
        void getCopiesInitiated_Success() throws Exception {
            // Given
            when(pipelineCopyService.getCopiesInitiatedByUser(any(UserDetails.class)))
                    .thenReturn(List.of(pipelineCopy));

            // When/Then
            mockMvc.perform(get("/api/pipeline/copies/sent")
                            .principal(() -> "owner"))
                    .andExpect(status().isOk());

            verify(pipelineCopyService).getCopiesInitiatedByUser(any(UserDetails.class));
        }

        @Test
        @DisplayName("Should get copies received by user")
        void getCopiesReceived_Success() throws Exception {
            // Given
            when(pipelineCopyService.getCopiesReceivedByUser(any(UserDetails.class)))
                    .thenReturn(List.of(pipelineCopy));

            // When/Then
            mockMvc.perform(get("/api/pipeline/copies/received")
                            .principal(() -> "owner"))
                    .andExpect(status().isOk());

            verify(pipelineCopyService).getCopiesReceivedByUser(any(UserDetails.class));
        }
    }
}
