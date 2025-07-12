package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.entity.AlgorithmParameter;
import com.cloud_ml_app_thesis.entity.CustomAlgorithm;
import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.service.MinioService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class FileUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String generateUniqueFilename(String originalFilename, String username){
        String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());
        String[] filenameArr = originalFilename.split("\\.");
        String uniqueFilename = filenameArr[0].replaceAll("\\s+", "_").concat("_").concat(timestamp);

        return  uniqueFilename + "_" + username + "." + filenameArr[1];
    }

    public static Path getSharedPathRoot() {
        // Αν εντοπιστεί περιβάλλον Docker/Kubernetes, χρησιμοποίησε το /shared
        if (System.getenv("KUBERNETES_SERVICE_HOST") != null || System.getenv("RUNNING_IN_DOCKER") != null) {
            return Path.of("/shared");
        }

        // Διαφορετικά, είσαι σε τοπικό περιβάλλον
        return Path.of(System.getProperty("java.io.tmpdir"));
    }

    public static Map<String, Object> loadUserParamsFromMinio(MinioService minioService, String key, String bucket) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(bucket)) {
            log.info("📭 No params.json provided.");
            return new LinkedHashMap<>();
        }

        try {
            Path userParamsPath = minioService.downloadObjectToTempFile(bucket, key);
            try (InputStream is = Files.newInputStream(userParamsPath)) {
                List<AlgorithmParameter> userParams = objectMapper.readValue(is, new TypeReference<>() {});
                log.info("✅ Loaded {} user parameters from MinIO", userParams.size());

                return userParams.stream().collect(Collectors.toMap(
                        AlgorithmParameter::getName,
                        param -> {
                            try {
                                return parseValue(param.getValue(), param.getType());
                            } catch (Exception e) {
                                log.warn("⚠️ Could not parse '{}', keeping raw value", param.getName());
                                return param.getValue();
                            }
                        },
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
            }
        } catch (IOException e) {
            throw new FileProcessingException("Failed to load params.json from MinIO", e);
        }
    }


    public static void writeParamsJson(Map<String, Object> params, DatasetConfiguration config, Path outputDir) {
        Map<String, Object> jsonMap = new LinkedHashMap<>(params);
        jsonMap.put("targetColumn", config.getTargetColumn());
        jsonMap.put("featureColumns", config.getBasicAttributesColumns());

        File jsonFile = outputDir.resolve("params.json").toFile();
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, jsonMap);
            log.info("✅ Wrote params.json to: {}", jsonFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write params.json", e);
        }
    }

    public static Map<String, Object> convertDefaults(List<AlgorithmParameter> defaults){
        Map<String, Object> map = new LinkedHashMap<>();
        for (AlgorithmParameter param : defaults) {
            try {
                map.put(param.getName(), parseValue(param.getValue(), param.getType()));
            }catch(Exception e) {
                map.put(param.getName(), param.getValue());
            }
        }
        return map;
    }

    public static Map<String, Object> mergeParams(Map<String, Object> defaults, Map<String, Object> overrides){
        Map<String, Object> merged = new LinkedHashMap<>(defaults);
        if (overrides != null) {
            overrides.forEach(merged::put); // replaces defaults with overrides if same key
        }
        return merged;
    }

    public static Object parseValue(String value, String type) {
        return switch (type.toLowerCase()) {
            case "int", "integer" -> Integer.parseInt(value);
            case "float", "double" -> Double.parseDouble(value);
            case "boolean", "bool" -> Boolean.parseBoolean(value);
            default -> value;
        };
    }

}
