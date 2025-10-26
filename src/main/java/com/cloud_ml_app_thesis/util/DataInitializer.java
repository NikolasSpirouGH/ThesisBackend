package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.entity.Algorithm;
import com.cloud_ml_app_thesis.entity.AlgorithmType;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import com.cloud_ml_app_thesis.repository.AlgorithmRepository;
import com.cloud_ml_app_thesis.repository.AlgorithmTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import weka.classifiers.Classifier;
import weka.clusterers.Clusterer;
import weka.core.*;

import java.lang.reflect.Method;
import java.util.*;

/**
 * DataInitializer - Handles dynamic initialization that cannot be done in SQL
 *
 * NOTE: All static reference data (roles, statuses, users, etc.) is now managed
 * by Flyway migrations in db/migration/V1__Init_reference_data.sql
 *
 * This class ONLY handles:
 * - Dynamic Weka algorithm discovery (requires Java reflection)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AlgorithmRepository algorithmRepository;
    private final AlgorithmTypeRepository algorithmTypeRepository;

    @Override
    public void run(String... args) {
        log.info("🚀 Starting DataInitializer - Weka Algorithm Discovery");
        initializeWekaAlgorithms();
        log.info("✅ DataInitializer completed successfully");
    }

    /**
     * Discovers and initializes Weka algorithms from classpath
     * This cannot be done in SQL as it requires Java reflection
     */
    private void initializeWekaAlgorithms() {
        log.info("📦 Scanning classpath for Weka algorithms...");

        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage("weka.classifiers"))
                .addUrls(ClasspathHelper.forPackage("weka.clusterers"))
                .setScanners(new SubTypesScanner()));

        Set<Class<? extends Classifier>> classifierClasses = reflections.getSubTypesOf(Classifier.class);
        Set<Class<? extends Clusterer>> clustererClasses = reflections.getSubTypesOf(Clusterer.class);

        List<Algorithm> algorithmInfos = new ArrayList<>();

        processAlgorithmClasses(classifierClasses, algorithmInfos, Classifier.class);
        processAlgorithmClasses(clustererClasses, algorithmInfos, Clusterer.class);

        saveAlgorithms(algorithmInfos);

        log.info("✅ Found and processed {} Weka algorithms", algorithmInfos.size());
    }

    /**
     * Process algorithm classes using reflection
     */
    private <T> void processAlgorithmClasses(Set<Class<? extends T>> classes, List<Algorithm> algorithmInfos, Class<T> type) {
        for (Class<? extends T> cls : classes) {
            try {
                // Skip abstract classes, interfaces, or inner classes
                if (java.lang.reflect.Modifier.isAbstract(cls.getModifiers()) || cls.isInterface() || cls.isMemberClass()) {
                    continue;
                }

                T instance = cls.getDeclaredConstructor().newInstance();
                String className = cls.getSimpleName();

                // Retrieve the package path relative to "weka.classifiers" or other packages
                Package pkg = cls.getPackage();
                String packageName = pkg.getName();
                String relativePath = packageName.substring(packageName.indexOf("weka.") + "weka.".length());

                // Check if globalInfo method is available
                Method globalInfoMethod = null;
                try {
                    globalInfoMethod = cls.getMethod("globalInfo");
                } catch (NoSuchMethodException e) {
                    // globalInfo method is not available
                }

                String classInfo = "(no description available)";
                if (globalInfoMethod != null) {
                    classInfo = (String) globalInfoMethod.invoke(instance);
                }

                String[] optionsDefaultArr = new String[]{};

                // Listing options if the algorithm implements OptionHandler
                if (instance instanceof OptionHandler) {
                    OptionHandler optionHandler = (OptionHandler) instance;
                    Enumeration<Option> options = optionHandler.listOptions();
                    StringBuilder optionsStr = new StringBuilder();
                    StringBuilder optionsDescrStr = new StringBuilder();

                    while (options.hasMoreElements()) {
                        Option option = options.nextElement();
                        if (option.name() != null && !option.name().isBlank()) {
                            optionsStr.append(option.name()).append(",");
                            optionsDescrStr.append(option.description()).append("->");
                        }
                        optionsDefaultArr = optionHandler.getOptions();
                    }

                    if (!optionsStr.isEmpty()) {
                        optionsStr.deleteCharAt(optionsStr.length() - 1); // Remove trailing comma
                    }
                    if (optionsDescrStr.length() > 1) {
                        optionsDescrStr.delete(optionsDescrStr.length() - 2, optionsDescrStr.length()); // Remove trailing "->"
                    }

                    String defaultOptionsFinal = String.join(" ", optionsDefaultArr);

                    // Determine algorithm type
                    AlgorithmTypeEnum typeEnum;
                    if (Clusterer.class.isAssignableFrom(cls)) {
                        typeEnum = AlgorithmTypeEnum.CLUSTERING;
                    } else if (Classifier.class.isAssignableFrom(cls)) {
                        typeEnum = AlgorithmTypeEnum.CLASSIFICATION;
                    } else {
                        throw new IllegalArgumentException("❌ Unknown algorithm type: " + cls.getName());
                    }

                    AlgorithmType algorithmType = algorithmTypeRepository.findByName(typeEnum)
                            .orElseThrow(() -> new EntityNotFoundException("Algorithm Type not found: " + typeEnum));

                    Algorithm algorithm = new Algorithm(
                            null,
                            className,
                            classInfo.replaceAll("[\\t\\n]", ""),
                            algorithmType,
                            optionsStr.toString().replaceAll("[\\t\\n]", "").replace(",,", ","),
                            optionsDescrStr.toString().replaceAll("[\\t\\n]", "").replace("->->", "->"),
                            defaultOptionsFinal,
                            cls.getName());
                    algorithmInfos.add(algorithm);
                }
            } catch (Exception e) {
                log.error("Error processing class {}: {}", cls.getName(), e.getMessage());
            }
        }
    }

    /**
     * Save discovered algorithms to database
     */
    private void saveAlgorithms(List<Algorithm> algorithmInfos) {
        Algorithm currentAlgorithm = null;
        int savedCount = 0;
        int skippedCount = 0;

        try {
            for (Algorithm algorithm : algorithmInfos) {
                currentAlgorithm = algorithm;

                // Check if algorithm already exists by className
                if (!algorithmRepository.existsByClassName(algorithm.getClassName())) {
                    algorithmRepository.save(algorithm);
                    savedCount++;
                } else {
                    skippedCount++;
                }
            }

            log.info("💾 Saved {} new algorithms, skipped {} existing", savedCount, skippedCount);

        } catch (DataIntegrityViolationException e) {
            log.error("❌ Failed to save algorithm with name: {}, Description length: {}, Options Description length: {}",
                    currentAlgorithm != null ? currentAlgorithm.getName() : "unknown",
                    currentAlgorithm != null ? currentAlgorithm.getDescription().length() : "unknown",
                    currentAlgorithm != null ? currentAlgorithm.getOptionsDescription().length() : "unknown");
        }
    }

    /**
     * Helper method to determine if a classifier is a regressor
     */
    public static boolean isRegressor(Classifier classifier) {
        try {
            // 1. Create dummy dataset
            ArrayList<Attribute> attributes = new ArrayList<>();
            attributes.add(new Attribute("feature1"));
            attributes.add(new Attribute("feature2"));
            attributes.add(new Attribute("target")); // numeric class

            Instances data = new Instances("dummy", attributes, 0);
            data.setClassIndex(data.numAttributes() - 1);

            // Add dummy data
            double[] vals1 = {1.0, 2.0, 3.0};
            double[] vals2 = {2.0, 3.0, 4.0};
            double[] target = {10.0, 15.0, 20.0};

            for (int i = 0; i < vals1.length; i++) {
                double[] instance = {vals1[i], vals2[i], target[i]};
                data.add(new DenseInstance(1.0, instance));
            }

            // 2. Try to build model
            classifier.buildClassifier(data);

            // 3. Check if classAttribute is numeric
            return data.classAttribute().isNumeric();
        } catch (Exception e) {
            return false;
        }
    }
}
