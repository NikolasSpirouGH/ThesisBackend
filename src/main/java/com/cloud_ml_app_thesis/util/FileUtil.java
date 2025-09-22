package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.dto.train.EvaluationResult;
import com.cloud_ml_app_thesis.dto.train.TrainMetricResult;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.service.MinioService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        String sharedOverride = System.getenv("SHARED_VOLUME");
        if (sharedOverride != null && !sharedOverride.isBlank()) {
            Path configured = Paths.get(sharedOverride);
            return configured.isAbsolute() ? configured : configured.toAbsolutePath();
        }

        String customTmp = System.getenv("TMPDIR");
        if (customTmp != null && !customTmp.isBlank()) {
            return Paths.get(customTmp).toAbsolutePath();
        }

        String systemTmp = System.getProperty("java.io.tmpdir");
        return Paths.get(systemTmp).toAbsolutePath();
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

    public static Path writeMetricsToJsonFile(TrainMetricResult metrics) throws IOException {
        try {
            Path tempFile = Files.createTempFile("metrics", ".json");
            ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(tempFile.toFile(), metrics);
            return tempFile;
        } catch (IOException e) {
            throw new FileProcessingException("Failed to write metrics file", e);
        }
    }

}
