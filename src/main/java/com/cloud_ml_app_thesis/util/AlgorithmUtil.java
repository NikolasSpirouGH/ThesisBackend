package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.service.AlgorithmService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.clusterers.Clusterer;
import weka.core.Instances;
import weka.core.OptionHandler;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class AlgorithmUtil {

    private static final String WEKA_MAVEN_TEST_PROP = "weka.test.maventest";

    static {
        ensureWekaClasspathCompatibility();
    }
    private static final Logger logger = LoggerFactory.getLogger(AlgorithmUtil.class);

    public static boolean isClassifier(String algorithmClassName) {
        try {
            Class<?> cls = Class.forName(algorithmClassName);
            return Classifier.class.isAssignableFrom(cls);
        } catch (ClassNotFoundException e) {
            logger.error("Classifier class not found for algorithm: {}", algorithmClassName, e);
            return false;
        }
    }

    public static boolean isClusterer(String algorithmClassName) {
        try {
            Class<?> cls = Class.forName(algorithmClassName);
            return Clusterer.class.isAssignableFrom(cls);
        } catch (ClassNotFoundException e) {
            logger.error("Clusterer class not found for algorithm: {}", algorithmClassName, e);
            return false;
        }
    }

    public static boolean isClassification(Instances data) {
        return data.classAttribute().isNominal();
    }

    public static boolean isRegression(Instances data) {
        return data.classAttribute().isNumeric();
    }

    public static boolean isClustering(Instances data) {
        return data.classIndex() == -1;
    }

    public static Classifier getClassifierInstance(String algorithmClassName) throws Exception {
        ensureWekaClasspathCompatibility();
        Class<?> clazz = Class.forName(algorithmClassName);
        Constructor<?> constructor = clazz.getConstructor();
        return (Classifier) constructor.newInstance();
    }

    public static Clusterer getClustererInstance(String algorithmClassName) throws Exception {
        ensureWekaClasspathCompatibility();
        Class<?> clazz = Class.forName(algorithmClassName);
        Constructor<?> constructor = clazz.getConstructor();
        return (Clusterer) constructor.newInstance();
    }

    public static void setClassifierOptions(Classifier classifier, String[] options) throws Exception {
        ensureWekaClasspathCompatibility();
        if (classifier instanceof OptionHandler) {
            ((OptionHandler) classifier).setOptions(options);
        }
    }

    public static void setClustererOptions(Clusterer clusterer, String[] options) throws Exception {
        ensureWekaClasspathCompatibility();
        //TODO if not instanceof ? throw exception?
        if (clusterer instanceof OptionHandler) {
            ((OptionHandler) clusterer).setOptions(options);
        }
    }

    public static String fixNestedOptions(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }

        if (raw.contains("-A weka.core.EuclideanDistance") && raw.contains("-R first-last")) {
            // Fix -R by wrapping as part of -A
            raw = raw.replace("-A weka.core.EuclideanDistance -R first-last", "-A \"weka.core.EuclideanDistance -R first-last\"");
        }

        // Fix incomplete scientific notation (e.g., "1.0E" -> "1.0")
        // Pattern matches: number followed by E/e without exponent
        raw = raw.replaceAll("(\\d+\\.\\d+)[Ee](?![+-]?\\d)", "$1");

        return raw;
    }

    public static void ensureWekaClasspathCompatibility() {
        String currentValue = System.getProperty(WEKA_MAVEN_TEST_PROP);
        if (!"true".equalsIgnoreCase(currentValue)) {
            System.setProperty(WEKA_MAVEN_TEST_PROP, "true");
        }
    }

    public static String resolveAlgorithmName(Training training) {
        if (training.getCustomAlgorithmConfiguration() != null
                && training.getCustomAlgorithmConfiguration().getAlgorithm() != null) {
            return training.getCustomAlgorithmConfiguration().getAlgorithm().getName();
        } else if (training.getAlgorithmConfiguration() != null
                && training.getAlgorithmConfiguration().getAlgorithm() != null) {
            log.info("Algorithm name: {}", training.getAlgorithmConfiguration().getAlgorithm().getName());
            return training.getAlgorithmConfiguration().getAlgorithm().getName();
        }
        return "Unknown";
    }
}
