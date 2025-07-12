package com.cloud_ml_app_thesis.dto.request.dataset;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class DatasetUpdateRequest {

    MultipartFile file;
}
