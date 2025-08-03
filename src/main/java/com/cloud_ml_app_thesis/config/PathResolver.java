package com.cloud_ml_app_thesis.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
        if (runningInDocker) {
            return Paths.get(sharedVolume).toAbsolutePath();
        } else {
            return Paths.get("shared").toAbsolutePath();
        }
    }

    public Path getTmpDir() {
        String tmp = System.getenv("TMPDIR"); // Optional: ή @Value again
        return tmp != null ? Paths.get(tmp).toAbsolutePath() : Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath();
    }
}
