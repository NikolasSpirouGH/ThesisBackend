package com.cloud_ml_app_thesis.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Slf4j
@Component
public class DockerCommandRunner {

    public void runCommand(String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[DOCKER] {}", line);
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String err = output.toString();
                log.error("[DOCKER] ❌ Error during command: {}", err);
                throw new RuntimeException("Non-zero exit from Docker command: " +
                        String.join(" ", args) + "\nOutput:\n" + err);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to execute Docker command: " + String.join(" ", args), e);
        }
    }

}