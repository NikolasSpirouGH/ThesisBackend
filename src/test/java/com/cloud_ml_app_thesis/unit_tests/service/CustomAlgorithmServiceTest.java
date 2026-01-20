package com.cloud_ml_app_thesis.unit_tests.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.custom_algorithm.AlgorithmParameterDTO;
import com.cloud_ml_app_thesis.dto.request.custom_algorithm.CustomAlgorithmCreateRequest;
import com.cloud_ml_app_thesis.entity.AlgorithmParameter;
import com.cloud_ml_app_thesis.entity.CustomAlgorithm;
import com.cloud_ml_app_thesis.entity.CustomAlgorithmImage;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import com.cloud_ml_app_thesis.repository.AlgorithmImageRepository;
import com.cloud_ml_app_thesis.repository.AlgorithmRepository;
import com.cloud_ml_app_thesis.repository.CustomAlgorithmRepository;
import com.cloud_ml_app_thesis.repository.accessibility.AlgorithmAccessibilityRepository;
import com.cloud_ml_app_thesis.service.AlgorithmService;
import com.cloud_ml_app_thesis.service.CustomAlgorithmService;
import com.cloud_ml_app_thesis.service.MinioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.glassfish.jersey.internal.inject.Custom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.modelmapper.ModelMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class CustomAlgorithmServiceTest {

    @InjectMocks
    private CustomAlgorithmService customAlgorithmService;

    @Mock
    private CustomAlgorithmRepository customAlgorithmRepository;
    @Mock
    private BucketResolver bucketResolver;
    @Mock
    private ModelMapper modelMapper;
    @Mock
    private MinioService minioService;
    @Mock
    private AlgorithmImageRepository imageRepository;
    @Mock
    private AlgorithmAccessibilityRepository algorithmAccessibilityRepository;


    private User testUser;

    @BeforeEach
    void setup(){
        testUser = new User();
        testUser.setUsername("testUser");
    }


    private MockMultipartFile validParameters() {
        return new MockMultipartFile("params.json", "params.json", "application/json",
                """
        [
        {
        "name": "learningRate",
        "type": "float",
        "defaultValue": "0.01",
        "range": "[0.001, 1.0]"
        }
        ]
        """.getBytes());
    }

    @Test
    void create_validRequest_shouldReturnIdAndUploadTar() throws IOException {
        CustomAlgorithmCreateRequest request = CustomAlgorithmCreateRequest.builder()
                .name("test-algo")
                .description("test-desc")
                .version("test-version")
                .accessibility(AlgorithmAccessibiltyEnum.PUBLIC)
                .keywords(List.of("keyword1", "keyword2"))
                .parametersFile(validParameters())
                .dockerTarFile(new MockMultipartFile("dockerTarFile", "algo.tar", "application/x-tar", "dummy".getBytes()))
                .build();

        AlgorithmParameter param = new AlgorithmParameter();
        param.setName("learningRate");
        param.setType("float");

        when(bucketResolver.resolve(BucketTypeEnum.CUSTOM_ALGORITHM)).thenReturn("ml-algorithms");

        when(modelMapper.map(any(AlgorithmParameterDTO.class), eq(AlgorithmParameter.class))).thenReturn(param);

        when(customAlgorithmRepository.save(any())).thenAnswer(invocation -> {
            CustomAlgorithm algorithm = invocation.getArgument(0);
            algorithm.setId(3);
            return algorithm;
        });

        Integer result = customAlgorithmService.create(request, testUser);

        assertThat(result).isEqualTo(3);

        verify(minioService).uploadObjectToBucket(any(), eq("ml-algorithms"), contains("algo.tar"));
        verify(customAlgorithmRepository).save(any());
        verify(imageRepository).save(any());

        // 👉 ΜΕΤΑ το create call: capture values
        ArgumentCaptor<CustomAlgorithm> algoCaptor = ArgumentCaptor.forClass(CustomAlgorithm.class);
        verify(customAlgorithmRepository).save(algoCaptor.capture());
        CustomAlgorithm saved = algoCaptor.getValue();
        assertThat(saved.getName()).isEqualTo("test-algo");
        assertThat(saved.getOwner()).isEqualTo(testUser);
        assertThat(saved.getParameters()).hasSize(1);

        ArgumentCaptor<CustomAlgorithmImage> imageCaptor = ArgumentCaptor.forClass(CustomAlgorithmImage.class);
        verify(imageRepository).save(imageCaptor.capture());
        CustomAlgorithmImage img = imageCaptor.getValue();
        assertThat(img.getCustomAlgorithm().getName()).isEqualTo("test-algo");
        assertThat(img.isActive()).isTrue();
        assertThat(img.getDockerTarKey()).contains("algo.tar");
    }

    @Test
    void create_withDockerHubOnly_shouldSaveImageWithUrl() throws IOException {
        // given
        CustomAlgorithmCreateRequest request = CustomAlgorithmCreateRequest.builder()
                .name("algo-from-url")
                .description("desc")
                .version("1.0")
                .accessibility(AlgorithmAccessibiltyEnum.PUBLIC)
                .keywords(List.of("cnn"))
                .dockerHubUrl("docker.io/user/image:latest")
                .parametersFile(validParameters())
                .build();

        AlgorithmParameter param = new AlgorithmParameter();
        param.setName("learningRate");
        param.setType("float");

        when(modelMapper.map(any(AlgorithmParameterDTO.class), eq(AlgorithmParameter.class))).thenReturn(param);

        when(customAlgorithmRepository.save(any())).thenAnswer(invocation -> {
            CustomAlgorithm algo = invocation.getArgument(0);
            algo.setId(99);
            return algo;
        });

        // when
        Integer result = customAlgorithmService.create(request, testUser);

        // then
        assertThat(result).isEqualTo(99);

        // 🔍 Verify save of algorithm
        verify(customAlgorithmRepository).save(any());

        // 🔍 Verify image saved with correct URL
        ArgumentCaptor<CustomAlgorithmImage> imageCaptor = ArgumentCaptor.forClass(CustomAlgorithmImage.class);
        verify(imageRepository).save(imageCaptor.capture());
        CustomAlgorithmImage savedImage = imageCaptor.getValue();

        assertThat(savedImage.getDockerHubUrl()).isEqualTo("docker.io/user/image:latest");
        assertThat(savedImage.isActive()).isTrue();
        assertThat(savedImage.getCustomAlgorithm().getName()).isEqualTo("algo-from-url");

        // ❌ Δεν πρέπει να γίνει upload σε MinIO
        verify(minioService, org.mockito.Mockito.never())
                .uploadObjectToBucket(any(), anyString(), anyString());
    }

}

