package com.cloud_ml_app_thesis.validation.validator;

import com.cloud_ml_app_thesis.validation.validation.ValidTarball;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class TarballContentValidator implements ConstraintValidator<ValidTarball, MultipartFile> {

    private static final Set<String> REQUIRED_FILES = Set.of(
            "Dockerfile", "train.py", "predict.py", "requirements.txt"
    );

    @Override
    public boolean isValid(MultipartFile tarFile, ConstraintValidatorContext context) {
        if (tarFile == null || tarFile.isEmpty()) return false;

        try (TarArchiveInputStream tarInput = new TarArchiveInputStream(tarFile.getInputStream())) {
            TarArchiveEntry entry;
            while ((entry = tarInput.getNextTarEntry()) != null) {
                if ("manifest.json".equals(entry.getName())) {
                    // It’s a docker image tarball, skip validation
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }

        return false;
    }

}

