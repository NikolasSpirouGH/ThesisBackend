package com.cloud_ml_app_thesis.dto.request.custom_algorithm;

import com.cloud_ml_app_thesis.dto.custom_algorithm.AlgorithmParameterDTO;
import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CustomAlgorithmCreateRequest", description = "Form for creating a custom ML algorithm. Provide either dockerTarFile OR dockerHubUrl.")
public class CustomAlgorithmCreateRequest {

    @Schema(description = "Algorithm name", example = "MyAwesomeAlgorithm", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Algorithm name must not be blank")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Algorithm name must contain only letters, digits, underscores, dots or hyphens")
    @Size(max = 100)
    private String name;

    @Schema(description = "Optional description (max 500 characters)", example = "This algorithm solves X using Y")
    @Size(max = 500)
    private String description;

    @Schema(description = "Version string", example = "1.0.0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Version must not be blank")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Version must contain only letters, digits, underscores, dots or hyphens")
    private String version;

    @Schema(description = "PUBLIC , PRIVATE , SHARED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private AlgorithmAccessibiltyEnum accessibility;

    @Schema(description = "List of keywords for searching/classification", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty
    private List<@Size(max = 50) String> keywords;

    @Schema(description = "JSON file defining DEFAULTS parameters for the algorithm", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
    private MultipartFile parametersFile;

    @Schema(description = "TAR file containing Docker image with algorithm.py", type = "string", format = "binary")
    private MultipartFile dockerTarFile;

    @Schema(description = "Alternative Docker Hub URL if TAR is not provided", example = "docker.io/user/image:tag")
    private String dockerHubUrl;

}