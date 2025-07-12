package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class DockerContainerRunner {

    private final DockerClient dockerClient;

    public DockerContainerRunner() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://localhost:2375") // Windows Docker Desktop daemon must expose this
                .build();

        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofMinutes(2))
                .build();

        this.dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();
    }

    public void runTrainingContainer(String imageName, Path hostDataDir, Path hostModelDir){
        log.info("Running training container with image={} ", imageName);

        Volume dataVolume = new Volume("/data");
        Volume modelVolume = new Volume("/model");

       HostConfig hostConfig = HostConfig.newHostConfig().
               withBinds(
                       new Bind(hostDataDir.toString(), new Volume("/data")),
                       new Bind(hostModelDir.toString(), new Volume("/model")));

        log.info("📂 Mounting volumes: hostDataDir={} → /data, hostModelDir={} → /model", hostDataDir, hostModelDir);

       CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
               .withHostConfig(hostConfig)
               .exec();

       String containerId = container.getId();
       log.info("Created container ID={}", containerId);

       dockerClient.startContainerCmd(containerId).exec();
       log.info("Started container ID={}", containerId);

        try {
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(new LogContainerResultCallback(){
                         @Override
                        public void onNext(Frame frame){
                             log.info("[CONTAINER LOG] {}", new String(frame.getPayload()).trim());
                             super.onNext(frame );
                         }
                    }).awaitCompletion(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Integer exitCode = dockerClient.waitContainerCmd(containerId).start().awaitStatusCode();

        if(exitCode != 0){
            log.error("❌ Container exited with non-zero code: {}", exitCode);
            throw new RuntimeException("Training container failed (exitCode=" + exitCode + ")");
        }

        log.info("Container finished successfully");
    }

    public void runPredictionContainer(String imageName, Path predictionDataset, Path modelFile, Path outputDir) {
        log.info("🚀 Starting prediction container with image={}", imageName);
        log.info("Inside docker run container input file: {}", predictionDataset);
        log.info("Inside docker run container model file: {}", modelFile);
        log.info("Inside docker run container output dir: {}", outputDir);

        HostConfig hostConfig = HostConfig.newHostConfig().withBinds(
                new Bind(predictionDataset.toString(), new Volume("/data/predict.csv")),
                new Bind(modelFile.toString(), new Volume("/model/model.pkl")),
                new Bind(outputDir.toString(), new Volume("/model"))
        );

        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withHostConfig(hostConfig)
                .withCmd("predict.py")
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
        } catch (IOException e) {
            throw new FileProcessingException("❌ Failed to load Docker TAR", e);
        }

        String imageId = getLatestImageId();
        tagImage(imageId, dockerImageTag);
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