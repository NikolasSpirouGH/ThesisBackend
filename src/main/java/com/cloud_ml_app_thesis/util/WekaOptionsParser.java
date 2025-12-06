package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.dto.weka_algorithm.WekaAlgorithmOptionDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class WekaOptionsParser {

    /**
     * Parses Weka algorithm options from stored format to structured DTOs
     *
     * @param optionsStr Comma-separated option flags (e.g., "C,M,N")
     * @param optionsDescriptionStr Arrow-separated descriptions (e.g., "Confidence factor->Min instances->...")
     * @param defaultOptionsStr Default CLI string (e.g., "-C 0.25 -M 2")
     * @return List of structured option DTOs
     */
    public static List<WekaAlgorithmOptionDTO> parseOptions(
            String optionsStr,
            String optionsDescriptionStr,
            String defaultOptionsStr) {

        List<WekaAlgorithmOptionDTO> options = new ArrayList<>();

        if (optionsStr == null || optionsStr.isBlank()) {
            log.debug("No options to parse");
            return options;
        }

        // Split options and descriptions
        String[] optionFlags = optionsStr.split(",");
        String[] descriptions = optionsDescriptionStr != null && !optionsDescriptionStr.isBlank()
            ? optionsDescriptionStr.split("->")
            : new String[0];

        // Parse default values from CLI string
        Map<String, String> defaultValues = parseDefaultValues(defaultOptionsStr);

        // Build DTOs
        for (int i = 0; i < optionFlags.length; i++) {
            String flag = optionFlags[i].trim();
            if (flag.isEmpty()) {
                continue;
            }

            String description = i < descriptions.length
                ? descriptions[i].trim()
                : "No description available";

            String defaultValue = defaultValues.getOrDefault(flag, "");
            String type = inferType(defaultValue, description);

            WekaAlgorithmOptionDTO option = WekaAlgorithmOptionDTO.builder()
                .flag(flag)
                .description(description)
                .type(type)
                .defaultValue(defaultValue)
                .build();

            options.add(option);
        }

        return options;
    }

    /**
     * Parses default values from Weka CLI string
     * Example: "-C 0.25 -M 2" -> Map { "C": "0.25", "M": "2" }
     */
    private static Map<String, String> parseDefaultValues(String defaultOptionsStr) {
        Map<String, String> defaults = new HashMap<>();

        if (defaultOptionsStr == null || defaultOptionsStr.isBlank()) {
            return defaults;
        }

        // Pattern to match -Flag Value pairs
        Pattern pattern = Pattern.compile("-([A-Za-z0-9]+)\\s+([^-]+)");
        Matcher matcher = pattern.matcher(defaultOptionsStr);

        while (matcher.find()) {
            String flag = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            defaults.put(flag, value);
        }

        // Also handle boolean flags (flags without values)
        Pattern booleanPattern = Pattern.compile("-([A-Za-z0-9]+)(?=\\s+-|$)");
        Matcher booleanMatcher = booleanPattern.matcher(defaultOptionsStr);

        while (booleanMatcher.find()) {
            String flag = booleanMatcher.group(1).trim();
            if (!defaults.containsKey(flag)) {
                defaults.put(flag, "true");
            }
        }

        return defaults;
    }

    /**
     * Infers the type of an option based on its default value and description
     */
    private static String inferType(String defaultValue, String description) {
        if (defaultValue == null || defaultValue.isBlank()) {
            return "string";
        }

        // Check if it's a boolean
        if (defaultValue.equalsIgnoreCase("true") || defaultValue.equalsIgnoreCase("false")) {
            return "boolean";
        }

        // Check if it's numeric (integer or decimal)
        try {
            Double.parseDouble(defaultValue);
            return "numeric";
        } catch (NumberFormatException e) {
            // Not a number
        }

        // Check description for hints
        String descLower = description.toLowerCase();
        if (descLower.contains("number") || descLower.contains("size") ||
            descLower.contains("count") || descLower.contains("factor")) {
            return "numeric";
        }

        return "string";
    }

    /**
     * Converts user-provided option values to Weka CLI string format
     *
     * @param options Map of flag -> value (e.g., {"C": "0.5", "M": "3"})
     * @return CLI string (e.g., "-C 0.5 -M 3")
     */
    public static String toCliString(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return "";
        }

        StringBuilder cliString = new StringBuilder();

        for (Map.Entry<String, String> entry : options.entrySet()) {
            String flag = entry.getKey();
            String value = entry.getValue();

            if (value == null || value.isBlank()) {
                continue;
            }

            cliString.append("-").append(flag).append(" ").append(value).append(" ");
        }

        return cliString.toString().trim();
    }
}
