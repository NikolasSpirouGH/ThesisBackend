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
        // 👇 Αν δεν υπάρχει tarFile, απλά δεν τον ελέγχεις
        if (tarFile == null || tarFile.isEmpty()) {
            return true;  // Skip validation – θα το ελέγξει το @ValidImageSource
        }

        try (TarArchiveInputStream tarInput = new TarArchiveInputStream(tarFile.getInputStream())) {
            TarArchiveEntry entry;
            Set<String> foundFiles = new HashSet<>();

            while ((entry = tarInput.getNextTarEntry()) != null) {
                if (!entry.isDirectory()) {
                    foundFiles.add(entry.getName());
                }

                // 👉 Bonus: Αν είναι docker image tarball (.tar from `docker save`), skip check
                if ("manifest.json".equals(entry.getName())) {
                    return true;
                }
            }

            return foundFiles.containsAll(REQUIRED_FILES);

        } catch (IOException e) {
            return false;
        }
    }
}
