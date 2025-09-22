package com.cloud_ml_app_thesis.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@RequiredArgsConstructor
public class PathResolver {

    @Value("${SHARED_VOLUME:shared}")
    private String sharedVolume;

    @Value("${RUNNING_IN_DOCKER:false}")
    private boolean runningInDocker;

    public Path getSharedPathRoot() {
        Path configured = (sharedVolume != null && !sharedVolume.isBlank())
                ? Paths.get(sharedVolume)
                : Paths.get("shared");

        Path resolved = configured.isAbsolute()
                ? configured
                : configured.toAbsolutePath();

        if (runningInDocker && !resolved.isAbsolute()) {
            resolved = resolved.toAbsolutePath();
        }

        try {
            Files.createDirectories(resolved);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create shared directory at " + resolved, e);
        }

        return resolved;
    }

    public Path getTmpDir() {
        String tmp = System.getenv("TMPDIR"); // Optional: ή @Value again
        return tmp != null ? Paths.get(tmp).toAbsolutePath() : Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath();
    }
}
