package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.service.DatasetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class DatasetUtil {
    private static final Logger logger = LoggerFactory.getLogger(DatasetService.class);

    public static Instances selectColumns(Instances data, String basicAttributesColumns, String targetClassColumn, int prediction) throws Exception {
        List<String> columnNames = new ArrayList<>();

        // Log the original dataset attributes
        logger.info("Original dataset attributes: ");
        for (int i = 0; i < data.numAttributes(); i++) {
            logger.info("Attribute {}: {}", i + 1, data.attribute(i).name());
        }

        // Default to all columns except the last one if basic attributes columns are not provided
        if (basicAttributesColumns == null || basicAttributesColumns.isEmpty() || basicAttributesColumns.equals("DEFAULT")) {
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
            if (targetClassColumn == null || targetClassColumn.isEmpty() || targetClassColumn.equals("DEFAULT")) {
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
            filteredData.setClassIndex(-1);
        }
        return filteredData;
    }

    public static String csvToArff(InputStream inputStream, String fileReference) {
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
   /* public static Instances loadPredictionDataset(MultipartFile file, String filename) throws Exception {
        InputStream datasetStream = file.getInputStream();

        // Convert the dataset to ARFF format if it is in CSV or Excel format
        String fileExtension = getFileExtension(filename);
        if (fileExtension.equalsIgnoreCase(".csv")) {
            String arffFilePath = csvToArff(datasetStream, filename);
            datasetStream = Files.newInputStream(Paths.get(arffFilePath));
        }

        Instances data = new ConverterUtils.DataSource(datasetStream).getDataSet();
        int prediction = 1;
        data = selectColumns(data, datasetConfiguration.getBasicAttributesColumns(), null, prediction); // No target column for prediction
        data.setClassIndex(-1);
        return data;
    }*/
}
