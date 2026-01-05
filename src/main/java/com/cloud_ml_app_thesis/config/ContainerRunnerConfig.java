package com.cloud_ml_app_thesis.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.cloud_ml_app_thesis.util.ContainerRunner;
import com.cloud_ml_app_thesis.util.DockerContainerRunner;
import com.cloud_ml_app_thesis.util.KubernetesJobRunner;

import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for choosing between Docker and Kubernetes container runners.
 *
 * Set environment variable CONTAINER_RUNTIME to switch:
 * - "docker" (default): Uses Docker API via docker.sock
 * - "kubernetes": Uses Kubernetes Jobs API
 *
 * Or set RUNNING_IN_K8S=true to auto-detect Kubernetes
 */
@Slf4j
@Configuration
public class ContainerRunnerConfig {

    @Bean
    @Primary
    public ContainerRunner containerRunner(
            @Qualifier("dockerRunner") DockerContainerRunner dockerRunner,
            @Qualifier("kubernetesRunner") KubernetesJobRunner kubernetesRunner) {

        String runtime = System.getenv().getOrDefault("CONTAINER_RUNTIME", "auto");
        boolean runningInK8s = "true".equalsIgnoreCase(System.getenv("RUNNING_IN_K8S"));
        boolean runningInDocker = "true".equalsIgnoreCase(System.getenv("RUNNING_IN_DOCKER"));

        // Auto-detect based on environment
        if ("auto".equalsIgnoreCase(runtime)) {
            if (runningInK8s) {
                log.info("☸️ Auto-detected Kubernetes environment - using KubernetesJobRunner");
                return kubernetesRunner;
            } else if (runningInDocker) {
                log.info("🐳 Auto-detected Docker environment - using DockerContainerRunner");
                return dockerRunner;
            } else {
                log.info("🐳 Default to DockerContainerRunner (set CONTAINER_RUNTIME=kubernetes to override)");
                return dockerRunner;
            }
        }

        // Manual selection
        if ("kubernetes".equalsIgnoreCase(runtime) || "k8s".equalsIgnoreCase(runtime)) {
            log.info("☸️ Using KubernetesJobRunner (CONTAINER_RUNTIME={})", runtime);
            return kubernetesRunner;
        } else {
            log.info("🐳 Using DockerContainerRunner (CONTAINER_RUNTIME={})", runtime);
            return dockerRunner;
        }
    }
}
