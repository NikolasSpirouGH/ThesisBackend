package com.cloud_ml_app_thesis.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("dockerRunner")
public class DockerContainerRunner implements ContainerRunner {

    private final DockerClient dockerClient;
    private volatile String cachedSharedVolumeName;
    private volatile Path cachedSharedRoot;


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

    // No longer needed - app runs in Docker container, always uses unix socket at /var/run/docker.sock
    private static String resolveDockerHost() {
        String env = System.getenv("DOCKER_HOST");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return "unix:///var/run/docker.sock";
    }

    @Override
    public void runTrainingContainer(String imageName, Path containerDataDir, Path containerModelDir) {
        log.info("🐳 Starting training container using image='{}'", imageName);

        Path expectedDataset = containerDataDir.resolve("dataset.csv");
        if (!Files.exists(expectedDataset)) {
            throw new IllegalStateException("❌ Missing dataset.csv at: " + expectedDataset);
        }

        if (!Files.exists(containerModelDir)) {
            throw new IllegalStateException("❌ Output directory (modelDir) does not exist: " + containerModelDir);
        }

        String sharedVolumeName = resolveSharedVolumeName();
        Path sharedRoot = resolveSharedRoot();

        Path relativeData = relativizeWithinShared(sharedRoot, containerDataDir);
        Path relativeModel = relativizeWithinShared(sharedRoot, containerModelDir);

        String dataPath = "/shared/" + relativeData.toString().replace('\\', '/');
        String modelPath = "/shared/" + relativeModel.toString().replace('\\', '/');

        log.info("📂 Mounting shared volume '{}' at /shared", sharedVolumeName);
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(new Bind(sharedVolumeName, new Volume("/shared")));

        log.info("🔧 Creating container with host config...");

        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withHostConfig(hostConfig)
                .withEnv(
                        "DATA_DIR=" + dataPath,
                        "MODEL_DIR=" + modelPath
                )
                .withCmd("sh", "-c", "python " + dataPath + "/train.py")
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


    @Override
    public void runPredictionContainer(String imageName, Path containerDataDir, Path containerModelDir) {
        log.info("🚀 Starting prediction container with image={}", imageName);
        String sharedVolumeName = resolveSharedVolumeName();
        Path sharedRoot = resolveSharedRoot();

        Path relativeData = relativizeWithinShared(sharedRoot, containerDataDir);
        Path relativeModel = relativizeWithinShared(sharedRoot, containerModelDir);

        String dataPath = "/shared/" + relativeData.toString().replace('\\', '/');
        String modelPath = "/shared/" + relativeModel.toString().replace('\\', '/');

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(new Bind(sharedVolumeName, new Volume("/shared")));

        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withHostConfig(hostConfig)
                .withEnv("DATA_DIR=" + dataPath, "MODEL_DIR=" + modelPath)
                .withCmd("sh", "-c", "python " + dataPath + "/predict.py")
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


    @Override
    public void loadImageFromTar(Path tarPath, String expectedTag) {
        loadDockerImageFromTar(tarPath, expectedTag);
    }

    public void loadDockerImageFromTar(Path tarPath, String expectedTag) {
        log.info("🐳 Loading Docker image from TAR (simple approach): {}", tarPath);

        LoadedImageMetadata imageMeta = extractImageMetadataFromTar(tarPath);

        try (InputStream tarInput = Files.newInputStream(tarPath)) {
            dockerClient.loadImageCmd(tarInput).exec();
            log.info("✅ Docker image loaded from TAR");

            String[] expectedParts = expectedTag.split(":", 2);
            if (expectedParts.length != 2) {
                throw new IllegalArgumentException("Invalid expected tag format: " + expectedTag);
            }

            List<Image> allImages = dockerClient.listImagesCmd().exec();

            if (imageMeta.configDigest() != null) {
                String targetImageId = "sha256:" + imageMeta.configDigest();
                for (Image image : allImages) {
                    if (targetImageId.equals(image.getId())) {
                        dockerClient.tagImageCmd(image.getId(), expectedParts[0], expectedParts[1]).exec();
                        log.info("🏷️ Tagged image {} using manifest digest {}", image.getId().substring(0, 12), imageMeta.configDigest());
                        dockerClient.inspectImageCmd(expectedTag).exec();
                        log.info("✅ Verified expected tag: {}", expectedTag);
                        return;
                    }
                }
                log.warn("⚠️ Image with manifest digest {} not found after load", imageMeta.configDigest());
            }

            if (!imageMeta.repoTags().isEmpty()) {
                for (String repoTag : imageMeta.repoTags()) {
                    for (Image image : allImages) {
                        if (image.getRepoTags() == null) {
                            continue;
                        }
                        for (String existing : image.getRepoTags()) {
                            if (repoTag.equals(existing)) {
                                dockerClient.tagImageCmd(image.getId(), expectedParts[0], expectedParts[1]).exec();
                                log.info("🏷️ Retagged image {} from {} to {}", image.getId().substring(0, 12), repoTag, expectedTag);
                                dockerClient.inspectImageCmd(expectedTag).exec();
                                log.info("✅ Verified expected tag: {}", expectedTag);
                                return;
                            }
                        }
                    }
                }
                log.warn("⚠️ Loaded image repo tags {} were not found after load; falling back to heuristic tagging", imageMeta.repoTags());
            }

            for (Image image : allImages) {
                if (image.getRepoTags() != null && image.getRepoTags().length > 0) {
                    String firstTag = image.getRepoTags()[0];
                    if (!firstTag.startsWith("docker.io/") && !firstTag.startsWith("registry")) {
                        try {
                            dockerClient.tagImageCmd(image.getId(), expectedParts[0], expectedParts[1]).exec();
                            log.info("🏷️ Successfully tagged image {} as {}", image.getId().substring(0, 12), expectedTag);
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

    private LoadedImageMetadata extractImageMetadataFromTar(Path tarPath) {
        List<String> repoTags = new ArrayList<>();
        String configDigest = null;
        try (InputStream in = Files.newInputStream(tarPath);
             org.apache.commons.compress.archivers.tar.TarArchiveInputStream tarStream =
                     new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(in)) {

            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
            while ((entry = tarStream.getNextTarEntry()) != null) {
                if (!entry.isFile()) {
                    continue;
                }
                if ("manifest.json".equals(entry.getName())) {
                    ObjectMapper mapper = new ObjectMapper();
                    List<java.util.Map<String, Object>> manifest = mapper.readValue(tarStream, new TypeReference<>() {});
                    for (java.util.Map<String, Object> item : manifest) {
                        Object configObj = item.get("Config");
                        if (configObj instanceof String configStr && configStr.startsWith("blobs/sha256/")) {
                            configDigest = configStr.substring("blobs/sha256/".length());
                        }
                        Object tagsObj = item.get("RepoTags");
                        if (tagsObj instanceof List<?>) {
                            for (Object tag : (List<?>) tagsObj) {
                                if (tag instanceof String tagStr && !tagStr.isBlank()) {
                                    repoTags.add(tagStr);
                                }
                            }
                        }
                    }
                    break;
                }
            }
        } catch (IOException e) {
            log.warn("⚠️ Failed to read manifest.json from {}: {}", tarPath, e.getMessage());
        }
        return new LoadedImageMetadata(repoTags, configDigest);
    }

    private record LoadedImageMetadata(List<String> repoTags, String configDigest) {}

    private String resolveSharedVolumeName() {
        String cached = cachedSharedVolumeName;
        if (cached != null) {
            return cached;
        }

        String nameOverride = System.getenv("SHARED_VOLUME_NAME");
        if (nameOverride != null && !nameOverride.isBlank()) {
            cachedSharedVolumeName = nameOverride;
            return nameOverride;
        }

        String containerId = System.getenv("HOSTNAME");
        Path sharedRoot = resolveSharedRoot();
        if (containerId != null) {
            try {
                var inspect = dockerClient.inspectContainerCmd(containerId).exec();
                if (inspect.getMounts() != null) {
                    for (var mount : inspect.getMounts()) {
                        String destinationPath = mount.getDestination() != null ? mount.getDestination().getPath() : null;
                        log.info("🔍 Inspect mount: name={}, source={}, destination={}", mount.getName(), mount.getSource(), destinationPath);
                        if (destinationPath != null && sharedRoot.toString().equals(destinationPath)) {
                            if (mount.getName() != null && !mount.getName().isBlank()) {
                                cachedSharedVolumeName = mount.getName();
                                log.info("🔗 Resolved shared volume name via inspect: {}", mount.getName());
                                return mount.getName();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ Unable to resolve shared volume name via inspect: {}", e.getMessage());
            }
        }

        log.warn("⚠️ Falling back to default shared volume name 'shared_volume'");
        cachedSharedVolumeName = "shared_volume";
        return cachedSharedVolumeName;
    }

    private Path resolveSharedRoot() {
        Path cached = cachedSharedRoot;
        if (cached != null) {
            return cached;
        }
        String sharedEnv = System.getenv("SHARED_VOLUME");
        if (sharedEnv == null || sharedEnv.isBlank()) {
            sharedEnv = "/app/shared";
        }
        Path root = Paths.get(sharedEnv).toAbsolutePath().normalize();
        log.debug("🔍 Resolved shared root: {}", root);
        cachedSharedRoot = root;
        return root;
    }

    private Path relativizeWithinShared(Path sharedRoot, Path targetPath) {
        Path absoluteTarget = targetPath.toAbsolutePath().normalize();
        if (!absoluteTarget.startsWith(sharedRoot)) {
            throw new IllegalArgumentException("Path " + absoluteTarget + " is outside shared root " + sharedRoot);
        }
        Path relative = sharedRoot.relativize(absoluteTarget);
        log.debug("📁 Relative path inside shared volume: {}", relative);
        return relative;
    }

    /**
     * Copy a file from a Docker image to the host filesystem
     */
    @Override
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

    @Override
    public void pullImage(String imageName) {
        pullDockerImage(imageName);
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
