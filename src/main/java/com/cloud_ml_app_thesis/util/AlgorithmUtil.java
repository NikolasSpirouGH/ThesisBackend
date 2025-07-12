package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.service.AlgorithmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.clusterers.Clusterer;
import weka.core.OptionHandler;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlgorithmUtil {
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


    public static Classifier getClassifierInstance(String algorithmClassName) throws Exception {
        Class<?> clazz = Class.forName(algorithmClassName);
        Constructor<?> constructor = clazz.getConstructor();
        return (Classifier) constructor.newInstance();
    }

    public static Clusterer getClustererInstance(String algorithmClassName) throws Exception {
        Class<?> clazz = Class.forName(algorithmClassName);
        Constructor<?> constructor = clazz.getConstructor();
        return (Clusterer) constructor.newInstance();
    }

    public static void setClassifierOptions(Classifier classifier, String[] options) throws Exception {
        //TODO if not instanceof ? throw exception?
        if (classifier instanceof OptionHandler) {
            ((OptionHandler) classifier).setOptions(options);
        }
    }

    public static void setClustererOptions(Clusterer clusterer, String[] options) throws Exception {
        //TODO if not instanceof ? throw exception?
        if (clusterer instanceof OptionHandler) {
            ((OptionHandler) clusterer).setOptions(options);
        }
    }


    public static String[] mergeUnstructuredUserOptionsToWekaOptions(String userOptions, String defaultOptions) {
        Map<String, String> optionsMap = new HashMap<>();

        // Parse default options
        String[] defaultOptionsArray = defaultOptions.split("[,\\s]+");
        for (int i = 0; i < defaultOptionsArray.length; i++) {
            if (defaultOptionsArray[i].startsWith("-")) {
                String key = defaultOptionsArray[i];
                String value = (i + 1 < defaultOptionsArray.length && !defaultOptionsArray[i + 1].startsWith("-")) ? defaultOptionsArray[++i] : "";
                optionsMap.put(key, value);
            }
        }

        // Parse user options
        if (userOptions != null && !userOptions.trim().isEmpty()) {
            String[] userOptionsArray = userOptions.split(",");
            for (String option : userOptionsArray) {
                String[] keyValue = option.split(":");
                //TODO Maybe must be  >=2
                if (keyValue.length == 2) {
                    String key = "-" + keyValue[0];
                    String value = keyValue[1];
                    optionsMap.put(key, value); // User options override defaults
                }
            }
        }

        // Convert the map to a list of options
        List<String> finalOptions = new ArrayList<>();
        optionsMap.forEach((key, value) -> {
            finalOptions.add(key);
            if (!value.isEmpty()) {
                finalOptions.add(value);
            }
        });

        return finalOptions.toArray(new String[0]);
    }

    public static String formatUserOptionsOptions(String userOptions, String defaultOptions){
        String[] finalOptionsArr = mergeUnstructuredUserOptionsToWekaOptions(userOptions,defaultOptions);
        StringBuilder finalOptions = new StringBuilder();
        // to length -1 in order to not add one extra ',' at the end
        for(int i=0; i < finalOptionsArr.length -1; i++) {
            finalOptions.append(finalOptionsArr[i]);
            finalOptions.append(',');
        }
        //adding the last value
        finalOptions.append(finalOptionsArr[finalOptionsArr.length-1]);
        return finalOptions.toString();
    }
    public static String[] formatUserOptionsToWekaOptions(String userOptions, String defaultOptions){
        return formatUserOptionsOptions(userOptions, defaultOptions).split(",");
    }
}


