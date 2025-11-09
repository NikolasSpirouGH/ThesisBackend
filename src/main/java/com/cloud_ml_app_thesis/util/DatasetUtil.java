package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.config.MinioConfig;
import com.cloud_ml_app_thesis.entity.AlgorithmType;
import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.service.DatasetService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;
import weka.core.converters.ConverterUtils;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DatasetUtil {
    private static final Logger logger = LoggerFactory.getLogger(DatasetService.class);

    public static Instances selectColumns(Instances data, String basicAttributesColumns, String targetClassColumn, int prediction) throws Exception {
        List<String> columnNames = new ArrayList<>();

        logger.info("Original dataset attributes: ");
        for (int i = 0; i < data.numAttributes(); i++) {
            logger.info("Attribute {}: {}", i + 1, data.attribute(i).name());
        }

        // Step 1: Resolve target class attribute name safely
        String classAttrName = null;
        if (targetClassColumn != null && !targetClassColumn.isEmpty()) {
            int targetIdx = Integer.parseInt(targetClassColumn.trim()) - 1;
            if (targetIdx >= 0 && targetIdx < data.numAttributes()) {
                classAttrName = data.attribute(targetIdx).name();
                logger.info("Target class column: {} (index {})", classAttrName, targetIdx);
            } else {
                logger.warn("⚠️ Target column index {} out of bounds (attributes: {}). Falling back to last attribute", targetIdx + 1, data.numAttributes());
                classAttrName = data.attribute(data.numAttributes() - 1).name();
            }
        }

        // Step 2: Add basic attributes
        if (basicAttributesColumns == null || basicAttributesColumns.isEmpty()) {
            for (int i = 0; i < data.numAttributes(); i++) {
                columnNames.add(data.attribute(i).name());
            }
        } else {
            for (String index : basicAttributesColumns.split(",")) {
                int idx = Integer.parseInt(index.trim()) - 1;
                if (idx >= 0 && idx < data.numAttributes()) {
                    columnNames.add(data.attribute(idx).name());
                } else {
                    logger.warn("⚠️ Ignoring invalid basic attribute index: {}", idx + 1);
                }
            }
        }

        logger.info("Selected basic attributes columns: {}", columnNames);

        // Step 3: Ensure class attribute is kept
        if (classAttrName != null && data.attribute(classAttrName) != null && !columnNames.contains(classAttrName)) {
            columnNames.add(classAttrName);
            logger.info("✅ Added class attribute '{}' to selected columns", classAttrName);
        }

        logger.info("Final columns to keep: {}", columnNames);

        // Step 4: Build indices
        List<Integer> indicesToKeep = new ArrayList<>();
        for (int i = 0; i < data.numAttributes(); i++) {
            if (columnNames.contains(data.attribute(i).name())) {
                indicesToKeep.add(i);
            }
        }

        logger.info("Indices to keep: {}", indicesToKeep);

        // Step 5: Apply filtering
        Remove removeFilter = new Remove();
        removeFilter.setAttributeIndicesArray(indicesToKeep.stream().mapToInt(i -> i).toArray());
        removeFilter.setInvertSelection(true);
        removeFilter.setInputFormat(data);
        Instances filteredData = Filter.useFilter(data, removeFilter);

        // Step 6: Set class index after filtering
        if (classAttrName != null) {
            Attribute classAttr = filteredData.attribute(classAttrName);
            if (classAttr != null) {
                filteredData.setClassIndex(classAttr.index());
                logger.info("🎯 Set class index to: {} ({})", classAttr.index(), classAttr.name());
            } else {
                logger.warn("⚠️ Class attribute '{}' not found in filtered dataset", classAttrName);
            }
        }

        logger.info("Filtered dataset attributes: ");
        for (int i = 0; i < filteredData.numAttributes(); i++) {
            logger.info("Attribute {}: {}", i + 1, filteredData.attribute(i).name());
        }

        if (filteredData.classIndex() < 0 && filteredData.numAttributes() > 0) {
            filteredData.setClassIndex(filteredData.numAttributes() - 1);
            logger.info("🎯 Fallback: Set class index to last attribute: {} ({})",
                    filteredData.classIndex(), filteredData.classAttribute().name());
        }
        return filteredData;
    }

    public static String csvToArff(InputStream inputStream, String fileReference) {
        logger.info("▶ Entering csvToArff() for file: {}", fileReference);
        File tempInputFile = null;
        File tempOutputFile = null;
        File tempCsvFile = null;

        try {
            String fileExtension = getFileExtension(fileReference);

            // If it's an Excel file, first convert to CSV
            if (isExcelFile(fileExtension)) {
                logger.info("📊 Detected Excel file, converting to CSV first...");
                String csvPath = XlsToCsv.convertExcelToCsv(inputStream, fileReference);
                tempCsvFile = new File(csvPath);
                tempInputFile = tempCsvFile;
                fileExtension = ".csv";
                inputStream = Files.newInputStream(tempInputFile.toPath());
            } else {
                // Create a temporary file for the input data
                tempInputFile = File.createTempFile("input", fileExtension);
                Files.copy(inputStream, tempInputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // Determine if the file is CSV or ARFF and load the data accordingly
            Instances data;
            if (fileExtension.equalsIgnoreCase(".arff")) {
                ArffLoader arffLoader = new ArffLoader();
                arffLoader.setSource(tempInputFile);
                data = arffLoader.getDataSet();
            } else {
                CSVLoader csvLoader = new CSVLoader();
                csvLoader.setSource(tempInputFile);
                data = csvLoader.getDataSet();
            }

            // Create a separate temporary file for the ARFF output
            tempOutputFile = File.createTempFile("output", ".arff");

            // Save the data in ARFF format to the output file
            ArffSaver saver = new ArffSaver();
            saver.setInstances(data);
            saver.setFile(tempOutputFile);
            saver.writeBatch();
            logger.info("📄 ARFF file saved to: {}", tempOutputFile.getAbsolutePath());
            logger.info("🧾 ARFF content:\n{}", Files.readString(tempOutputFile.toPath()));
            // Return the path of the output file
            return tempOutputFile.getAbsolutePath();
        } catch (IOException e) {
            throw new FileProcessingException("Failed to convert file to ARFF format", e);
        } finally {
            // Clean up the temporary input file
            if (tempInputFile != null) {
                tempInputFile.delete();
            }
        }
    }

    private static String getFileExtension(String fileName) {
        int lastIndex = fileName.lastIndexOf('.');
        if (lastIndex == -1) {
            return ""; // empty extension
        }
        return fileName.substring(lastIndex);
    }

    /**
     * Check if the file extension is an Excel format
     */
    private static boolean isExcelFile(String fileExtension) {
        return fileExtension.equalsIgnoreCase(".xls") || fileExtension.equalsIgnoreCase(".xlsx");
    }

    public static Instances prepareDataset(MultipartFile file, String filename, DatasetConfiguration datasetConfiguration) throws Exception {
        InputStream datasetStream = file.getInputStream();
        // Convert the dataset to ARFF format if it is in CSV or Excel format
        String fileExtension = getFileExtension(filename);
        if (fileExtension.equalsIgnoreCase(".csv") || isExcelFile(fileExtension)) {
            logger.info("📥 Converting {} file to ARFF format...", fileExtension);
            String arffFilePath = csvToArff(datasetStream, filename);
            datasetStream = Files.newInputStream(Paths.get(arffFilePath));
        }

        Instances data = new ConverterUtils.DataSource(datasetStream).getDataSet();
        int prediction = 0;
        data = selectColumns(data, datasetConfiguration.getBasicAttributesColumns(), datasetConfiguration.getTargetColumn(), prediction);
        return data;
    }


    public static Instances loadDatasetInstancesByDatasetConfigurationFromMinio(DatasetConfiguration datasetConfiguration, InputStream datasetStream, String objectName) throws Exception {

        logger.info("Dataset Stream obtained successfully.");

        // Convert the dataset to ARFF format if it is in CSV or Excel format
        String fileExtension = getFileExtension(objectName);
        if (fileExtension.equalsIgnoreCase(".csv") || isExcelFile(fileExtension)) {
            logger.info("📥 Converting {} file from MinIO to ARFF format...", fileExtension);
            String arffFilePath = csvToArff(datasetStream, objectName);
            datasetStream = Files.newInputStream(Paths.get(arffFilePath));
        }

        Instances data = new ConverterUtils.DataSource(datasetStream).getDataSet();
        int prediction = 0;

        data = selectColumns(data, datasetConfiguration.getBasicAttributesColumns(), datasetConfiguration.getTargetColumn(), prediction);
        return data;
    }

    public static byte[] replaceQuestionMarksWithPredictionResultsAsCSV(Instances dataset, List<String> predictions, boolean isClusterer) {
        logger.info("▶ Entering replaceQuestionMarksWithPredictionResultsAsCSV() with isClusterer = {}", isClusterer);

        if (!isClusterer) {
            int classIndex = dataset.classIndex();
            Attribute classAttr = dataset.classAttribute();

            if (classAttr != null && classAttr.name().equalsIgnoreCase("class") && hasMissingValues(dataset, classIndex)) {
                // 🔁 Replace ? values in class column
                for (int i = 0; i < dataset.numInstances(); i++) {
                    dataset.instance(i).setValue(classIndex, predictions.get(i));
                }
                logger.info("✅ Replaced missing values in class attribute '{}'", classAttr.name());

            } else {
                // ➕ Add new attribute for predictions
                logger.warn("⚠️ Class attribute not found or no missing values. Adding 'prediction' column instead.");
                Attribute predictionAttr = new Attribute("prediction", (List<String>) null);
                dataset.insertAttributeAt(predictionAttr, dataset.numAttributes());
                int predictionIndex = dataset.numAttributes() - 1;

                for (int i = 0; i < dataset.numInstances(); i++) {
                    dataset.instance(i).setValue(predictionIndex, predictions.get(i));
                }
            }

        } else {
            // ➕ Clusterer → Always add "prediction" column
            logger.info("➕ Adding 'prediction' column for clustering results");
            Attribute predictionAttr = new Attribute("prediction", (List<String>) null);
            dataset.insertAttributeAt(predictionAttr, dataset.numAttributes());
            int predictionIndex = dataset.numAttributes() - 1;

            for (int i = 0; i < dataset.numInstances(); i++) {
                dataset.instance(i).setValue(predictionIndex, predictions.get(i));
            }
        }

        // 📝 Build CSV string
        StringBuilder sb = new StringBuilder();

        // Header
        for (int i = 0; i < dataset.numAttributes(); i++) {
            sb.append(dataset.attribute(i).name());
            if (i != dataset.numAttributes() - 1) sb.append(",");
        }
        sb.append("\n");

        // Data rows
        for (int i = 0; i < dataset.numInstances(); i++) {
            Instance instance = dataset.instance(i);
            for (int j = 0; j < dataset.numAttributes(); j++) {
                if (instance.attribute(j).isNumeric()) {
                    sb.append(instance.value(j));
                } else {
                    sb.append(instance.stringValue(j));
                }
                if (j != dataset.numAttributes() - 1) sb.append(",");
            }
            sb.append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String resolveClassAttributeName(DatasetConfiguration config, Instances data) {
        int totalAttributes = data.numAttributes();

        try {
            if (config.getTargetColumn() != null) {
                int requestedIndex = Integer.parseInt(config.getTargetColumn()) - 1;
                logger.info("🔍 Resolving class attribute: requested index = {}, dataset has {} attributes", requestedIndex + 1, totalAttributes);
                if (requestedIndex < 0 || requestedIndex >= totalAttributes) {
                    logger.warn("⚠️ Index out of bounds: using fallback to last attribute '{}'", data.attribute(totalAttributes - 1).name());
                    return data.attribute(totalAttributes - 1).name();
                }
                return data.attribute(requestedIndex).name();
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("❌ Invalid target column index format: " + config.getTargetColumn(), e);
        }

        // fallback: return last column
        String fallback = data.attribute(totalAttributes - 1).name();
        logger.warn("⚠️ Falling back to last attribute as class: {}", fallback);
        return fallback;
    }


    public static Instances injectNominalClassFromTraining(Instances predictionData, String classAttrName, List<String> classLabels) {
        Attribute oldAttr = predictionData.attribute(classAttrName);
        if (oldAttr == null) {
            throw new IllegalArgumentException("Class attribute '" + classAttrName + "' not found in prediction data");
        }

        // Αν είναι ήδη nominal, τέλεια
        if (oldAttr.isNominal()) {
            predictionData.setClassIndex(oldAttr.index());
            return predictionData;
        }

        int oldIndex = oldAttr.index();

        // 1. Δημιουργία νέου nominal attribute (π.χ. class_nominal)
        Attribute nominalAttr = new Attribute(classAttrName + "_nominal", classLabels);
        predictionData.insertAttributeAt(nominalAttr, oldIndex + 1);

        for (int i = 0; i < predictionData.numInstances(); i++) {
            Instance inst = predictionData.instance(i);
            if (inst.isMissing(oldAttr)) {
                inst.setMissing(oldIndex + 1);
            } else {
                String stringVal;

                if (oldAttr.isNominal()) {
                    stringVal = inst.stringValue(oldAttr);
                } else if (oldAttr.isNumeric()) {
                    int intVal = (int) inst.value(oldAttr);  // ή (int) Math.round(...)
                    if (intVal >= 0 && intVal < classLabels.size()) {
                        stringVal = classLabels.get(intVal);
                    } else {
                        stringVal = ""; // ή throw exception
                    }
                } else {
                    stringVal = inst.toString(oldAttr); // fallback
                }

                inst.setValue(oldIndex + 1, stringVal);
            }
        }

        // 3. Αν δεν είναι ήδη class attribute ➤ διαγραφή του παλιού
        if (predictionData.classIndex() != oldIndex) {
            predictionData.deleteAttributeAt(oldIndex);
        }

        // 4. Μετονομασία nominal σε classAttrName (αν χρειαστεί)
        Attribute renamedAttr = predictionData.attribute(classAttrName + "_nominal");
        Attribute finalAttr = new Attribute(classAttrName, classLabels);
        predictionData.insertAttributeAt(finalAttr, predictionData.numAttributes());

        int finalIndex = predictionData.numAttributes() - 1;
        for (int i = 0; i < predictionData.numInstances(); i++) {
            Instance inst = predictionData.instance(i);
            if (inst.isMissing(renamedAttr)) {
                inst.setMissing(finalIndex);
            } else {
                inst.setValue(finalIndex, inst.stringValue(renamedAttr));
            }
        }

        // Διαγραφή του προσωρινού _nominal
        predictionData.deleteAttributeAt(renamedAttr.index());

        // 5. Ορισμός του νέου class index
        Attribute finalClassAttr = predictionData.attribute(classAttrName);
        if (finalClassAttr == null)
            throw new IllegalStateException("❌ Final nominal class attribute not found: " + classAttrName);

        predictionData.setClassIndex(finalClassAttr.index());
        return predictionData;
    }



    public static String[] resolveDatasetMinioInfo(Dataset dataset) {
        String fullMinioPath = dataset.getFilePath();
        String[] minioPathParts = fullMinioPath.split("/");

        if (minioPathParts.length < 2) {
            throw new RuntimeException("Could not retrieve the Dataset Minio information.");
        }

        return minioPathParts;
    }

    public static boolean hasMissingValues(Instances data, int attrIndex) {
        for (int i = 0; i < data.numInstances(); i++) {
            if (data.instance(i).isMissing(attrIndex)) return true;
        }
        return false;
    }

}


