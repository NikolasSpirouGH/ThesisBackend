package com.cloud_ml_app_thesis.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import lombok.extern.slf4j.Slf4j;

/**
 * Kubernetes-based implementation of ContainerRunner.
 * Runs ML training and predictions as Kubernetes Jobs instead of Docker containers.
 */
@Slf4j
@Component("kubernetesRunner")
public class KubernetesJobRunner implements ContainerRunner {

    private final KubernetesClient kubernetesClient;
    private final String namespace;
    private final String pvcName;
    private final Path sharedRoot;

    public KubernetesJobRunner() {
        // Auto-detect if running in Kubernetes cluster
        Config config;
        if (isRunningInKubernetes()) {
            log.info("☸️ Running inside Kubernetes - using in-cluster config");
            config = new ConfigBuilder().build();
        } else {
            log.info("☸️ Running outside Kubernetes - using kubeconfig");
            config = Config.autoConfigure(null);
        }

        this.kubernetesClient = new KubernetesClientBuilder()
                .withConfig(config)
                .build();

        this.namespace = System.getenv().getOrDefault("K8S_NAMESPACE", "thesisapp");
        this.pvcName = System.getenv().getOrDefault("K8S_SHARED_PVC", "shared-pvc");

        String sharedEnv = System.getenv().getOrDefault("SHARED_VOLUME", "/app/shared");
        this.sharedRoot = Paths.get(sharedEnv).toAbsolutePath().normalize();

        log.info("☸️ Kubernetes Job Runner initialized: namespace={}, pvc={}, shared={}",
                namespace, pvcName, sharedRoot);
    }

    private boolean isRunningInKubernetes() {
        return Files.exists(Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/token"));
    }

    @Override
    public void runTrainingContainer(String imageName, Path containerDataDir, Path containerModelDir) {
        log.info("☸️ Starting Kubernetes training job with image={}", imageName);

        Path expectedDataset = containerDataDir.resolve("dataset.csv");
        if (!Files.exists(expectedDataset)) {
            throw new IllegalStateException("❌ Missing dataset.csv at: " + expectedDataset);
        }

        if (!Files.exists(containerModelDir)) {
            throw new IllegalStateException("❌ Output directory (modelDir) does not exist: " + containerModelDir);
        }

        // Calculate relative paths within shared volume
        Path relativeData = sharedRoot.relativize(containerDataDir.toAbsolutePath().normalize());
        Path relativeModel = sharedRoot.relativize(containerModelDir.toAbsolutePath().normalize());

        String dataPath = "/shared/" + relativeData.toString().replace('\\', '/');
        String modelPath = "/shared/" + relativeModel.toString().replace('\\', '/');

        String jobName = "training-" + System.currentTimeMillis();

        Job job = new JobBuilder()
                .withNewMetadata()
                    .withName(jobName)
                    .withNamespace(namespace)
                    .withLabels(Collections.singletonMap("app", "ml-training"))
                .endMetadata()
                .withNewSpec()
                    .withBackoffLimit(0)  // Don't retry on failure
                    .withTtlSecondsAfterFinished(300)  // Auto-cleanup after 5 minutes
                    .withNewTemplate()
                        .withNewMetadata()
                            .withLabels(Collections.singletonMap("job-name", jobName))
                        .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName("trainer")
                                .withImage(imageName)
                                .withImagePullPolicy("IfNotPresent")
                                .withCommand("sh", "-c", "python " + dataPath + "/train.py")
                                .withEnv(
                                    new EnvVar("DATA_DIR", dataPath, null),
                                    new EnvVar("MODEL_DIR", modelPath, null)
                                )
                                .withVolumeMounts(new VolumeMountBuilder()
                                        .withName("shared-storage")
                                        .withMountPath("/shared")
                                        .build())
                                .withNewResources()
                                    .withRequests(Collections.singletonMap("memory", new Quantity("1Gi")))
                                    .withLimits(Collections.singletonMap("memory", new Quantity("2Gi")))
                                .endResources()
                            .endContainer()
                            .withRestartPolicy("Never")
                            .addNewVolume()
                                .withName("shared-storage")
                                .withNewPersistentVolumeClaim()
                                    .withClaimName(pvcName)
                                .endPersistentVolumeClaim()
                            .endVolume()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();

        log.info("🚀 Creating Kubernetes Job: {}", jobName);
        kubernetesClient.batch().v1().jobs().inNamespace(namespace).create(job);

        // Wait for job to complete and stream logs
        waitForJobCompletion(jobName, Duration.ofMinutes(10));
    }

    @Override
    public void runPredictionContainer(String imageName, Path containerDataDir, Path containerModelDir) {
        log.info("☸️ Starting Kubernetes prediction job with image={}", imageName);

        Path relativeData = sharedRoot.relativize(containerDataDir.toAbsolutePath().normalize());
        Path relativeModel = sharedRoot.relativize(containerModelDir.toAbsolutePath().normalize());

        String dataPath = "/shared/" + relativeData.toString().replace('\\', '/');
        String modelPath = "/shared/" + relativeModel.toString().replace('\\', '/');

        String jobName = "prediction-" + System.currentTimeMillis();

        Job job = new JobBuilder()
                .withNewMetadata()
                    .withName(jobName)
                    .withNamespace(namespace)
                    .withLabels(Collections.singletonMap("app", "ml-prediction"))
                .endMetadata()
                .withNewSpec()
                    .withBackoffLimit(0)
                    .withTtlSecondsAfterFinished(300)
                    .withNewTemplate()
                        .withNewMetadata()
                            .withLabels(Collections.singletonMap("job-name", jobName))
                        .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName("predictor")
                                .withImage(imageName)
                                .withImagePullPolicy("IfNotPresent")
                                .withCommand("sh", "-c", "python " + dataPath + "/predict.py")
                                .withEnv(
                                    new EnvVar("DATA_DIR", dataPath, null),
                                    new EnvVar("MODEL_DIR", modelPath, null)
                                )
                                .withVolumeMounts(new VolumeMountBuilder()
                                        .withName("shared-storage")
                                        .withMountPath("/shared")
                                        .build())
                                .withNewResources()
                                    .withRequests(Collections.singletonMap("memory", new Quantity("1Gi")))
                                    .withLimits(Collections.singletonMap("memory", new Quantity("2Gi")))
                                .endResources()
                            .endContainer()
                            .withRestartPolicy("Never")
                            .addNewVolume()
                                .withName("shared-storage")
                                .withNewPersistentVolumeClaim()
                                    .withClaimName(pvcName)
                                .endPersistentVolumeClaim()
                            .endVolume()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();

        log.info("🚀 Creating Kubernetes Job: {}", jobName);
        kubernetesClient.batch().v1().jobs().inNamespace(namespace).create(job);

        waitForJobCompletion(jobName, Duration.ofMinutes(10));
    }

    private void waitForJobCompletion(String jobName, Duration timeout) {
        log.info("⌛ Waiting for job {} to complete...", jobName);

        long startTime = System.currentTimeMillis();
        long timeoutMs = timeout.toMillis();

        // Wait for pod to be created
        Pod jobPod = null;
        while (jobPod == null && (System.currentTimeMillis() - startTime) < timeoutMs) {
            var pods = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withLabel("job-name", jobName)
                    .list()
                    .getItems();

            if (!pods.isEmpty()) {
                jobPod = pods.get(0);
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for job pod", e);
            }
        }

        if (jobPod == null) {
            throw new RuntimeException("Job pod not created within timeout");
        }

        String podName = jobPod.getMetadata().getName();
        log.info("📡 Attaching to pod logs: {}", podName);

        // Stream logs
        try (LogWatch logWatch = kubernetesClient.pods()
                .inNamespace(namespace)
                .withName(podName)
                .watchLog();
             java.io.BufferedReader reader = new java.io.BufferedReader(
                     new java.io.InputStreamReader(logWatch.getOutput()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[K8S JOB LOG] {}", line);
            }
        } catch (Exception e) {
            log.warn("⚠️ Error streaming logs: {}", e.getMessage());
        }

        // Wait for job to complete
        Job completedJob = kubernetesClient.batch().v1().jobs()
                .inNamespace(namespace)
                .withName(jobName)
                .waitUntilCondition(
                        job -> job.getStatus() != null &&
                               (job.getStatus().getSucceeded() != null && job.getStatus().getSucceeded() > 0 ||
                                job.getStatus().getFailed() != null && job.getStatus().getFailed() > 0),
                        timeoutMs - (System.currentTimeMillis() - startTime),
                        TimeUnit.MILLISECONDS
                );

        if (completedJob == null || completedJob.getStatus().getFailed() != null && completedJob.getStatus().getFailed() > 0) {
            log.error("❌ Job {} failed", jobName);
            throw new RuntimeException("Kubernetes job failed: " + jobName);
        }

        log.info("✅ Job {} completed successfully", jobName);
    }

    @Override
    public void loadImageFromTar(Path tarPath, String expectedTag) {
        log.info("☸️ Loading Docker image from TAR in Kubernetes: {}", tarPath);

        if (!Files.exists(tarPath)) {
            throw new IllegalArgumentException("TAR file does not exist: " + tarPath);
        }

        // Check if image already exists (optimization to skip re-loading)
        try {
            String checkPodName = "image-check-" + System.currentTimeMillis();
            Pod checkPod = new PodBuilder()
                    .withNewMetadata()
                        .withName(checkPodName)
                        .withNamespace(namespace)
                    .endMetadata()
                    .withNewSpec()
                        .addNewContainer()
                            .withName("checker")
                            .withImage("docker:24-cli")
                            .withCommand("sh", "-c", "docker images " + expectedTag + " | grep -v REPOSITORY")
                            .addNewVolumeMount()
                                .withName("docker-socket")
                                .withMountPath("/var/run/docker.sock")
                            .endVolumeMount()
                            .withNewSecurityContext()
                                .withPrivileged(true)
                            .endSecurityContext()
                        .endContainer()
                        .withRestartPolicy("Never")
                        .addNewVolume()
                            .withName("docker-socket")
                            .withNewHostPath()
                                .withPath("/var/run/docker.sock")
                                .withType("Socket")
                            .endHostPath()
                        .endVolume()
                    .endSpec()
                    .build();

            kubernetesClient.pods().inNamespace(namespace).create(checkPod);
            Pod completed = kubernetesClient.pods().inNamespace(namespace).withName(checkPodName)
                    .waitUntilCondition(p -> p.getStatus() != null && p.getStatus().getPhase() != null
                            && (p.getStatus().getPhase().equals("Succeeded") || p.getStatus().getPhase().equals("Failed")),
                            30, TimeUnit.SECONDS);

            String checkLogs = kubernetesClient.pods().inNamespace(namespace).withName(checkPodName).getLog();
            kubernetesClient.pods().inNamespace(namespace).withName(checkPodName).delete();

            if (checkLogs != null && !checkLogs.trim().isEmpty() && checkLogs.contains(expectedTag)) {
                log.info("✅ Image {} already exists in Kubernetes cluster, skipping load", expectedTag);
                return;
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not check if image exists, will proceed with load: {}", e.getMessage());
        }

        // Copy TAR to shared volume first
        Path relativeTar = sharedRoot.relativize(tarPath.toAbsolutePath().normalize());
        String tarPathInShared = "/shared/" + relativeTar.toString().replace('\\', '/');

        String podName = "image-loader-" + System.currentTimeMillis();

        // Create a privileged pod with Docker socket access to load the image
        Pod loaderPod = new PodBuilder()
                .withNewMetadata()
                    .withName(podName)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .addNewContainer()
                        .withName("loader")
                        .withImage("docker:24-cli")  // Official Docker CLI image
                        .withCommand("sh", "-c",
                            "OUTPUT=$(docker load -i " + tarPathInShared + ") && " +
                            "echo \"$OUTPUT\" && " +
                            "LOADED_IMAGE=$(echo \"$OUTPUT\" | grep -oE 'Loaded image: .+' | sed 's/Loaded image: //') && " +
                            "if [ -z \"$LOADED_IMAGE\" ]; then echo 'Failed to extract image name'; exit 1; fi && " +
                            "echo \"Tagging $LOADED_IMAGE as " + expectedTag + "\" && " +
                            "docker tag \"$LOADED_IMAGE\" " + expectedTag)
                        .withVolumeMounts(
                            new VolumeMountBuilder()
                                .withName("docker-socket")
                                .withMountPath("/var/run/docker.sock")
                                .build(),
                            new VolumeMountBuilder()
                                .withName("shared-storage")
                                .withMountPath("/shared")
                                .build()
                        )
                        .withNewSecurityContext()
                            .withPrivileged(true)  // Required for Docker socket access
                        .endSecurityContext()
                    .endContainer()
                    .withRestartPolicy("Never")
                    .addNewVolume()
                        .withName("docker-socket")
                        .withNewHostPath()
                            .withPath("/var/run/docker.sock")
                            .withType("Socket")
                        .endHostPath()
                    .endVolume()
                    .addNewVolume()
                        .withName("shared-storage")
                        .withNewPersistentVolumeClaim()
                            .withClaimName(pvcName)
                        .endPersistentVolumeClaim()
                    .endVolume()
                .endSpec()
                .build();

        try {
            log.info("🚀 Creating image loader pod: {}", podName);
            kubernetesClient.pods().inNamespace(namespace).create(loaderPod);

            // Wait for pod to complete
            log.info("⌛ Waiting for image to load...");
            Pod completedPod = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .waitUntilCondition(
                        pod -> {
                            if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null
                                    || pod.getStatus().getContainerStatuses().isEmpty()) {
                                return false;
                            }
                            ContainerStatus status = pod.getStatus().getContainerStatuses().get(0);
                            if (status.getState() == null) {
                                return false;
                            }
                            return status.getState().getTerminated() != null;
                        },
                        5,
                        TimeUnit.MINUTES
                    );

            // Stream logs
            try {
                String logs = kubernetesClient.pods()
                        .inNamespace(namespace)
                        .withName(podName)
                        .getLog();
                log.info("[IMAGE LOADER LOG] {}", logs);
            } catch (Exception e) {
                log.warn("⚠️ Could not retrieve loader logs: {}", e.getMessage());
            }

            // Check exit code
            if (completedPod != null && completedPod.getStatus() != null
                    && completedPod.getStatus().getContainerStatuses() != null
                    && !completedPod.getStatus().getContainerStatuses().isEmpty()) {
                ContainerStatus status = completedPod.getStatus().getContainerStatuses().get(0);
                if (status.getState() != null && status.getState().getTerminated() != null) {
                    Integer exitCode = status.getState().getTerminated().getExitCode();
                    if (exitCode != null && exitCode != 0) {
                        log.error("❌ Image loader pod exited with code: {}", exitCode);
                        throw new RuntimeException("Failed to load image from TAR (exitCode=" + exitCode + ")");
                    }
                }
            } else {
                log.warn("⚠️ Pod completed but container statuses are not available. Pod phase: {}",
                        completedPod != null && completedPod.getStatus() != null ?
                                completedPod.getStatus().getPhase() : "UNKNOWN");
            }

            log.info("✅ Successfully loaded image: {}", expectedTag);

        } catch (Exception e) {
            log.error("❌ Failed to load image from TAR: {}", e.getMessage());
            throw new RuntimeException("Failed to load image from TAR: " + e.getMessage(), e);
        } finally {
            // Cleanup loader pod
            try {
                kubernetesClient.pods().inNamespace(namespace).withName(podName).delete();
                log.info("🗑️ Cleaned up loader pod: {}", podName);
            } catch (Exception e) {
                log.warn("⚠️ Failed to cleanup loader pod: {}", e.getMessage());
            }
        }
    }

    @Override
    public void copyFileFromImage(String imageName, String sourcePathInImage, Path destPathOnHost) {
        log.warn("⚠️ copyFileFromImage using temporary pod...");

        String podName = "copy-" + System.currentTimeMillis();

        Pod pod = new PodBuilder()
                .withNewMetadata()
                    .withName(podName)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .addNewContainer()
                        .withName("copier")
                        .withImage(imageName)
                        .withCommand("sleep", "3600")
                    .endContainer()
                .endSpec()
                .build();

        try {
            kubernetesClient.pods().inNamespace(namespace).create(pod);

            // Wait for pod to be ready
            kubernetesClient.pods().inNamespace(namespace).withName(podName)
                    .waitUntilReady(60, TimeUnit.SECONDS);

            // Copy file from pod
            kubernetesClient.pods().inNamespace(namespace).withName(podName)
                    .file(sourcePathInImage)
                    .copy(destPathOnHost);

            log.info("✅ Copied {} from image to {}", sourcePathInImage, destPathOnHost);

        } catch (Exception e) {
            throw new RuntimeException("Failed to copy file from image: " + e.getMessage(), e);
        } finally {
            // Cleanup
            kubernetesClient.pods().inNamespace(namespace).withName(podName).delete();
        }
    }

    @Override
    public void pullImage(String imageName) {
        log.info("ℹ️ Image pulling in Kubernetes is handled by kubelet automatically");
        log.info("   Ensure imagePullPolicy is set correctly and image is accessible from the cluster");
        // No-op in Kubernetes - images are pulled automatically by kubelet
    }
}
