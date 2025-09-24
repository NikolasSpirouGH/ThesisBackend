package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.github.dockerjava.okhttp.OkDockerHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

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
                        new Bind(hostDataDir.toAbsolutePath().toString(), dataVolume),
                        new Bind(hostModelDir.toAbsolutePath().toString(), modelVolume)
                );

        log.info("🔧 Creating container with host config...");
        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withHostConfig(hostConfig)
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
                new Bind(dataDir.toString(), new Volume("/data")),
                new Bind(modelDir.toString(), new Volume("/model"))
        );

        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withHostConfig(hostConfig)
                .withCmd( "predict.py")
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



    private String getLatestImageId() {
        return dockerClient.listImagesCmd()
                .withShowAll(true)
                .exec()
                .stream()
                .max(Comparator.comparing(Image::getCreated))
                .orElseThrow(() -> new RuntimeException("❌ No image found after loading TAR"))
                .getId();
    }

    private void tagImage(String imageId, String dockerImageTag) {
        String[] parts = dockerImageTag.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid image tag: " + dockerImageTag);
        }

        dockerClient.tagImageCmd(imageId, parts[0], parts[1]).exec();
        log.info("🏷️ Tagged image {} as {}", imageId, dockerImageTag);
    }

    public void loadDockerImageFromTar(Path tarPath, String dockerImageTag) {
        log.info("🐳 Loading Docker image from TAR: {}", tarPath);
        try (InputStream tarInput = Files.newInputStream(tarPath)) {
            dockerClient.loadImageCmd(tarInput).exec();
            log.info("✅ Docker image loaded from TAR");
        } catch (IOException e) {
            throw new FileProcessingException("❌ Failed to load Docker TAR", e);
        }

        try {
            InspectImageResponse inspect = dockerClient.inspectImageCmd(dockerImageTag).exec();
            log.info("🔎 Loaded image ID: {}", inspect.getId());
        } catch (Exception e) {
            log.warn("⚠️ Image not found via tag '{}': {}", dockerImageTag, e.getMessage());
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
