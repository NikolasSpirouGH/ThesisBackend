package com.cloud_ml_app_thesis.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.okhttp.OkDockerHttpClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DockerContainerRunner {

    private final DockerClient dockerClient;

    public DockerContainerRunner() {
        String effectiveHost = resolveDockerHost();
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(effectiveHost)
                .withDockerTlsVerify(false)
                .build();

        log.info("🐳 Docker host in use: {}", config.getDockerHost());
        OkDockerHttpClient httpClient = new OkDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectTimeout(30_000)   // 30 sec
                .readTimeout(120_000)
                .build();
        this.dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();
    }

    private static String resolveDockerHost() {
        // 1) Respect explicit env override if provided
        String env = System.getenv("DOCKER_HOST");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }

        // 2) If Linux/WSL and socket exists, use Unix socket
        if (!isWindows()) {
            if (Files.exists(Paths.get("/var/run/docker.sock"))) {
                return "unix:///var/run/docker.sock";
            }
        }

        // 3) On native Windows, prefer named pipe
        if (isWindows()) {
            return "npipe:////./pipe/docker_engine";
        }

        // 4) Fallback to Unix socket
        return "unix:///var/run/docker.sock";
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    private static String normalizePathForDocker(Path path) {
        String pathStr = path.toAbsolutePath().toString();

        // For WSL2 with Docker Desktop, convert absolute paths to avoid complex bind mount issues
        if (pathStr.startsWith("/tmp/")) {
            return pathStr;
        }

        // If running in Docker or path contains complex WSL mounts, use a simpler temp path
        if (pathStr.contains("/run/desktop/mnt/host/wsl/docker-desktop-bind-mounts") ||
            pathStr.contains("/ThesisApp/shared/")) {
            // Use /tmp which Docker Desktop can handle properly in WSL2
            return pathStr.replace("/ThesisApp/shared/", "/tmp/shared/")
                          .replaceAll("/run/desktop/mnt/host/wsl/docker-desktop-bind-mounts/[^/]+/[^/]+", "/tmp");
        }

        return pathStr;
    }

    public void runTrainingContainer(String imageName, Path hostDataDir, Path hostModelDir) {
        log.info("🐳 Starting training container using image='{}'", imageName);
        log.info("📂 Mounting volumes:");
        log.info("    • Host data dir  → /data  → {}", hostDataDir.toAbsolutePath());
        log.info("    • Host model dir → /model → {}", hostModelDir.toAbsolutePath());

        Volume dataVolume = new Volume("/data");
        Volume modelVolume = new Volume("/model");

        Path expectedDataset = hostDataDir.resolve("dataset.csv");
        if (!Files.exists(expectedDataset)) {
            throw new IllegalStateException("❌ Missing dataset.csv at: " + expectedDataset);
        }

        if (!Files.exists(hostModelDir)) {
            throw new IllegalStateException("❌ Output directory (modelDir) does not exist: " + hostModelDir);
        }
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(
                        new Bind(normalizePathForDocker(hostDataDir), dataVolume),
                        new Bind(normalizePathForDocker(hostModelDir), modelVolume)
                );

        log.info("🔧 Creating container with host config...");
        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withHostConfig(hostConfig)
                .withCmd("python", "/data/train.py")  // Force execution of our injected template
                .exec();

        String containerId = container.getId();
        log.info("✅ Container created: ID={}", containerId);

        dockerClient.startContainerCmd(containerId).exec();
        log.info("🚀 Container started: ID={}", containerId);

        try {
            log.info("📡 Attaching to container logs...");
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(new LogContainerResultCallback() {
                        @Override
                        public void onNext(Frame frame) {
                            String logLine = new String(frame.getPayload()).trim();
                            if (!logLine.isBlank()) {
                                log.info("[CONTAINER LOG] {}", logLine);
                            }
                            super.onNext(frame);
                        }
                    }).awaitCompletion(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            log.error("⛔ Interrupted while waiting for container logs", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for container logs", e);
        }

        log.info("⌛ Waiting for container to finish...");
        Integer exitCode = dockerClient.waitContainerCmd(containerId).start().awaitStatusCode();

        if (exitCode != 0) {
            log.error("❌ Container exited with non-zero code: {}", exitCode);
            throw new RuntimeException("Training container failed (exitCode=" + exitCode + ")");
        }

        log.info("✅ Container finished successfully (exitCode=0)");
    }


    public void runPredictionContainer(String imageName, Path dataDir, Path modelDir) {
        log.info("🚀 Starting prediction container with image={}", imageName);
        log.info("🔗 Mount /data: {}", dataDir);
        log.info("🔗 Mount /model: {}", modelDir);

        HostConfig hostConfig = HostConfig.newHostConfig().withBinds(
                new Bind(normalizePathForDocker(dataDir), new Volume("/data")),
                new Bind(normalizePathForDocker(modelDir), new Volume("/model"))
        );

        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withHostConfig(hostConfig)
                .withCmd("python", "/data/predict.py")  // Force execution of our injected template
                .exec();

        String containerId = container.getId();
        dockerClient.startContainerCmd(containerId).exec();

        log.info("🟢 Prediction container started (id={})", containerId);

        try {
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true).withStdErr(true).withFollowStream(true)
                    .exec(new LogContainerResultCallback() {
                        @Override
                        public void onNext(Frame frame) {
                            log.info("[CONTAINER LOG] {}", new String(frame.getPayload()).trim());
                            super.onNext(frame);
                        }
                    }).awaitCompletion(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for prediction logs", e);
        }

        int exitCode = dockerClient.waitContainerCmd(containerId).start().awaitStatusCode();
        if (exitCode != 0) {
            log.error("❌ Prediction container exited with error code {}", exitCode);
            throw new RuntimeException("Prediction container failed (exitCode=" + exitCode + ")");
        }

        log.info("✅ Prediction container finished successfully");
    }


    public void loadDockerImageFromTar(Path tarPath, String expectedTag) {
        log.info("🐳 Loading Docker image from TAR (simple approach): {}", tarPath);

        try (InputStream tarInput = Files.newInputStream(tarPath)) {
            // Load the TAR file
            dockerClient.loadImageCmd(tarInput).exec();
            log.info("✅ Docker image loaded from TAR");

            // Get all images and find one with <none> tag or use the expected approach
            List<Image> allImages = dockerClient.listImagesCmd().exec();

            // Parse expected tag
            String[] expectedParts = expectedTag.split(":", 2);
            if (expectedParts.length != 2) {
                throw new IllegalArgumentException("Invalid expected tag format: " + expectedTag);
            }

            // Try to find an image that looks like it came from the TAR
            for (Image image : allImages) {
                if (image.getRepoTags() != null && image.getRepoTags().length > 0) {
                    String firstTag = image.getRepoTags()[0];
                    // Skip system images
                    if (!firstTag.startsWith("docker.io/") && !firstTag.startsWith("registry")) {
                        try {
                            dockerClient.tagImageCmd(image.getId(), expectedParts[0], expectedParts[1]).exec();
                            log.info("🏷️ Successfully tagged image {} as {}", image.getId().substring(0, 12), expectedTag);

                            // Verify the tag worked
                            dockerClient.inspectImageCmd(expectedTag).exec();
                            log.info("✅ Verified expected tag: {}", expectedTag);
                            return;
                        } catch (Exception e) {
                            log.debug("Could not tag image {}: {}", image.getId(), e.getMessage());
                        }
                    }
                }
            }

            throw new RuntimeException("Could not find suitable image to tag from TAR");
        } catch (IOException e) {
            throw new FileProcessingException("❌ Failed to load Docker TAR", e);
        }
    }

    /**
     * Copy a file from a Docker image to the host filesystem
     */
    public void copyFileFromImage(String imageName, String sourcePathInImage, Path destPathOnHost) {
        log.info("📋 Copying file from Docker image: {} -> {}", sourcePathInImage, destPathOnHost);

        // Create a temporary container from the image
        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withCmd("sleep", "1") // Dummy command
                .exec();

        String containerId = container.getId();

        try {
            // Copy file from container to host
            try (InputStream fileStream = dockerClient.copyArchiveFromContainerCmd(containerId, sourcePathInImage).exec();
                 java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {

                // Read the tar stream
                fileStream.transferTo(baos);
                byte[] tarData = baos.toByteArray();

                // Extract the file from tar archive
                try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(tarData);
                     org.apache.commons.compress.archivers.tar.TarArchiveInputStream tarStream =
                         new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(bais)) {

                    org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
                    while ((entry = tarStream.getNextTarEntry()) != null) {
                        if (entry.isFile()) {
                            // Write file content to destination
                            Files.copy(tarStream, destPathOnHost, StandardCopyOption.REPLACE_EXISTING);
                            log.info("✅ Successfully copied {} from image to {}", sourcePathInImage, destPathOnHost);
                            return;
                        }
                    }
                }

                throw new RuntimeException("File not found in archive: " + sourcePathInImage);

            } catch (IOException e) {
                throw new RuntimeException("Failed to copy file from image: " + e.getMessage(), e);
            }

        } finally {
            // Clean up temporary container
            try {
                dockerClient.removeContainerCmd(containerId).exec();
                log.info("🗑️ Cleaned up temporary container: {}", containerId);
            } catch (Exception e) {
                log.warn("⚠️ Failed to cleanup temporary container {}: {}", containerId, e.getMessage());
            }
        }
    }

    public void pullDockerImage(String imageName) {
        log.info("📥 Pulling Docker image: {}", imageName);
        try {
            dockerClient.pullImageCmd(imageName)
                    .start()
                    .awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Image pull was interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to pull Docker image: " + imageName, e);
        }
    }


}
