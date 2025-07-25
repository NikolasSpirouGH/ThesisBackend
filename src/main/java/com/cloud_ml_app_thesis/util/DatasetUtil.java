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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class DatasetUtil {
    private static final Logger logger = LoggerFactory.getLogger(DatasetService.class);
    private static MinioClient minioClient;
    public static Instances selectColumns(Instances data, String basicAttributesColumns, String targetClassColumn, int prediction) throws Exception {
        List<String> columnNames = new ArrayList<>();

        // Log the original dataset attributes
        logger.info("Original dataset attributes: ");
        for (int i = 0; i < data.numAttributes(); i++) {
            logger.info("Attribute {}: {}", i + 1, data.attribute(i).name());
        }

        // Default to all columns except the last one if basic attributes columns are not provided
        if (basicAttributesColumns == null || basicAttributesColumns.isEmpty()) {
            for (int i = 0; i < data.numAttributes(); i++) {
                columnNames.add(data.attribute(i).name());
            }
        } else {
            for (String index : basicAttributesColumns.split(",")) {
                columnNames.add(data.attribute(Integer.parseInt(index) - 1).name());
            }
        }
        logger.info("Selected basic attributes columns: {}", columnNames);

        if (prediction == 0) { // Training
            // Default to the last column if target class column is not provided
            if (targetClassColumn == null || targetClassColumn.isEmpty()) {
                logger.info("I am in train");
                targetClassColumn = data.attribute(data.numAttributes() - 1).name();
            } else {
                logger.info("i am in else");
                targetClassColumn = data.attribute(Integer.parseInt(targetClassColumn) - 1).name();
            }
            logger.info("Target class column: {}", targetClassColumn);

            // Ensure the target class column is included in the selection
            if (!columnNames.contains(targetClassColumn)) {
                columnNames.add(targetClassColumn);
            }
        }

        logger.info("Final columns to keep: {}", columnNames);

        // Create a list of attribute indices to keep
        List<Integer> indicesToKeep = new ArrayList<>();
        for (int i = 0; i < data.numAttributes(); i++) {
            if (columnNames.contains(data.attribute(i).name())) {
                indicesToKeep.add(i);
            }
        }
        logger.info("Indices to keep: {}", indicesToKeep);

        // Configure the Remove filter
        Remove removeFilter = new Remove();
        removeFilter.setAttributeIndicesArray(indicesToKeep.stream().mapToInt(i -> i).toArray());
        removeFilter.setInvertSelection(true); // Keep the specified indices
        removeFilter.setInputFormat(data);

        // Apply the filter to get the selected columns
        Instances filteredData = Filter.useFilter(data, removeFilter);

        if (prediction == 0) { // Training
            // Get the correct index for the target class column after filtering
            int targetIndex = -1;
            for (int i = 0; i < filteredData.numAttributes(); i++) {
                if (filteredData.attribute(i).name().equals(targetClassColumn)) {
                    targetIndex = i;
                    break;
                }
            }
            if (targetIndex == -1) {
                throw new Exception("Target class column not found in filtered data");
            }
            filteredData.setClassIndex(targetIndex);
        }

        logger.info("Filtered dataset attributes: ");
        for (int i = 0; i < filteredData.numAttributes(); i++) {
            logger.info("Attribute {}: {}", i + 1, filteredData.attribute(i).name());
        }
        if(prediction == 1) {
            filteredData.setClassIndex(filteredData.numAttributes() - 1);
        }
        return filteredData;
    }

    public static String csvToArff(InputStream inputStream, String fileReference) {
        logger.info("▶ Entering csvToArff()");
        File tempInputFile = null;
        File tempOutputFile = null;
        try {
            // Create a temporary file for the input data
            tempInputFile = File.createTempFile("input", getFileExtension(fileReference));
            Files.copy(inputStream, tempInputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Determine if the file is CSV or ARFF and load the data accordingly
            Instances data;
            if (getFileExtension(fileReference).equalsIgnoreCase(".arff")) {
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

    public static Instances prepareDataset(MultipartFile file, String filename, DatasetConfiguration datasetConfiguration) throws Exception {
        InputStream datasetStream = file.getInputStream();
        // Convert the dataset to ARFF format if it is in CSV or Excel format
        String fileExtension = getFileExtension(filename);
        if (fileExtension.equalsIgnoreCase(".csv")) {
            String arffFilePath = csvToArff(datasetStream, filename);
            datasetStream = Files.newInputStream(Paths.get(arffFilePath));
        }

        Instances data = new ConverterUtils.DataSource(datasetStream).getDataSet();
        int prediction = 0;
        data = selectColumns(data, datasetConfiguration.getBasicAttributesColumns(), datasetConfiguration.getTargetColumn(), prediction);
        return data;
    }
    public static Instances loadPredictionDataset(Dataset dataset) throws Exception {
        URI datasetUri = new URI(dataset.getFilePath());
        logger.info("Dataset URI: {}", datasetUri);
        String bucketName = Paths.get(datasetUri.getPath()).getName(0).toString();
        String objectName = Paths.get(datasetUri.getPath()).subpath(1, Paths.get(datasetUri.getPath()).getNameCount()).toString();
        logger.info("Bucket Name: {}", bucketName);
        logger.info("Object Name: {}", objectName);

        InputStream datasetStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
        logger.info("Dataset Stream obtained successfully.");

        // Convert the dataset to ARFF format if it is in CSV or Excel format
        String fileExtension = getFileExtension(objectName);
        if (fileExtension.equalsIgnoreCase(".csv")) {
            String arffFilePath = csvToArff(datasetStream, objectName);
            datasetStream = Files.newInputStream(Paths.get(arffFilePath));
        }

        Instances data = new ConverterUtils.DataSource(datasetStream).getDataSet();
        return data;
    }

    public static Instances loadDatasetInstancesByDatasetConfigurationFromMinio(DatasetConfiguration datasetConfiguration) throws Exception {
        URI datasetUri = new URI(datasetConfiguration.getDataset().getFilePath());
        logger.info("Dataset URI: {}", datasetUri);
        String bucketName = Paths.get(datasetUri.getPath()).getName(0).toString();
        String objectName = Paths.get(datasetUri.getPath()).subpath(1, Paths.get(datasetUri.getPath()).getNameCount()).toString();
        logger.info("Bucket Name: {}", bucketName);
        logger.info("Object Name: {}", objectName);

        InputStream datasetStream =  minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
        logger.info("Dataset Stream obtained successfully.");

        // Convert the dataset to ARFF format if it is in CSV or Excel format
        String fileExtension = getFileExtension(objectName);
        if (fileExtension.equalsIgnoreCase(".csv")) {
            String arffFilePath = csvToArff(datasetStream, objectName);
            datasetStream = Files.newInputStream(Paths.get(arffFilePath));
        }

        Instances data = new ConverterUtils.DataSource(datasetStream).getDataSet();
        int prediction = 0;
        data = selectColumns(data, datasetConfiguration.getBasicAttributesColumns(), datasetConfiguration.getTargetColumn(), prediction);
        return data;
    }

    public static byte[] replaceQuestionMarksWithPredictionResultsAsCSV(Instances dataset, List<String> predictions) {
        logger.info("▶ Entering replaceQuestionMarksWithPredictionResultsAsCSV()");
        // Προσθέτουμε νέα στήλη "prediction"
        dataset.insertAttributeAt(new Attribute("prediction", (List<String>) null), dataset.numAttributes());
        int predAttrIndex = dataset.numAttributes() - 1;

        for (int i = 0; i < dataset.numInstances(); i++) {
            dataset.instance(i).setValue(predAttrIndex, predictions.get(i));
        }

        StringBuilder sb = new StringBuilder();

        // Γράφουμε header
        for (int i = 0; i < dataset.numAttributes(); i++) {
            sb.append(dataset.attribute(i).name());
            if (i != dataset.numAttributes() - 1) sb.append(",");
        }
        sb.append("\n");

        // Γράφουμε rows
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

    public static Instances loadPredictionInstancesFromCsv(Path csvPath,
                                                           DatasetConfiguration config,
                                                           AlgorithmType algoType) throws Exception {
        logger.info("📥 Loading CSV file from: {}", csvPath);

        // 1. Αν είναι .csv → μετατροπή σε .arff
        String arffPath = csvToArff(Files.newInputStream(csvPath), csvPath.getFileName().toString());
        logger.info("🔄 Converted to ARFF: {}", arffPath);

        // 2. Φόρτωσε ARFF ως Instances
        Instances data = new ConverterUtils.DataSource(arffPath).getDataSet();
        logger.info("✅ Loaded ARFF with {} instances and {} attributes", data.numInstances(), data.numAttributes());

        // 3. Αν είναι classification, διόρθωσε το class attribute (αν είναι STRING)
        if (algoType.getName().equals(AlgorithmTypeEnum.CLASSIFICATION)) {
            String classAttrName = resolveClassAttributeName(config, data);
            List<String> classValues = extractClassValuesFromTrainingDataset(config);
            forceNominalClassIfNeeded(data, classAttrName, classValues);
        }

        // 4. Επέλεξε τα σωστά attributes
        return selectColumns(
                data,
                config.getBasicAttributesColumns(),
                config.getTargetColumn(),
                1 // prediction mode
        );
    }

    private static String resolveClassAttributeName(DatasetConfiguration config, Instances data) {
        if (config.getTargetColumn() != null) {
            return data.attribute(Integer.parseInt(config.getTargetColumn()) - 1).name();
        } else {
            return data.attribute(data.numAttributes() - 1).name(); // default: last column
        }
    }

    public static List<String> extractClassValuesFromTrainingDataset(DatasetConfiguration config) {
        logger.info("▶ Entering extractClassValuesFromTrainingDataset()");
        try {
            Instances trainingData = loadDatasetInstancesByDatasetConfigurationFromMinio(config);
            Attribute classAttr = resolveClassAttribute(config, trainingData);

            if (!classAttr.isNominal()) {
                throw new IllegalStateException("Class attribute is not nominal.");
            }

            List<String> values = new ArrayList<>();
            for (int i = 0; i < classAttr.numValues(); i++) {
                values.add(classAttr.value(i));
            }
            return values;
        } catch (Exception e) {
            throw new RuntimeException("❌ Failed to extract class values", e);
        }
    }

    private static Attribute resolveClassAttribute(DatasetConfiguration config, Instances data) {
        if (config.getTargetColumn() != null) {
            return data.attribute(Integer.parseInt(config.getTargetColumn()) - 1);
        } else {
            return data.attribute(data.numAttributes() - 1);
        }
    }

    public static void forceNominalClassIfNeeded(Instances data, String classAttrName, List<String> classValues) {
        logger.info("▶ Entering forceNominalClassIfNeeded()");
        Attribute classAttr = data.attribute(classAttrName);
        if (classAttr == null) {
            throw new IllegalStateException("Class attribute '" + classAttrName + "' not found");
        }

        if (classAttr.isString()) {
            logger.warn("🔄 Converting class '{}' from STRING to NOMINAL...", classAttrName);
            Attribute nominalAttr = new Attribute(classAttrName, new ArrayList<>(classValues));
            data.replaceAttributeAt(nominalAttr, classAttr.index());
        } else {
            logger.info("✅ Class '{}' already nominal or numeric", classAttrName);
        }
    }
}
