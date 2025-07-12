package com.cloud_ml_app_thesis.validation.validator;

import com.cloud_ml_app_thesis.validation.validation.ValidFileExtension;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

public class FileExtensionValidator implements ConstraintValidator<ValidFileExtension, MultipartFile> {
    private String[] allowedExtensions;

    @Override
    public void initialize(ValidFileExtension constraintAnnotation){
        this.allowedExtensions = constraintAnnotation.extensions();
    }

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context){
        if(file == null || file.isEmpty()){
            return false;
        }

        String fileName = file.getOriginalFilename();
        if(fileName == null) return false;

        String fileExtention = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

        return  Arrays.asList(allowedExtensions).contains(fileExtention);
    }
}
