package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.ModelAccessibilityEnum;
import com.cloud_ml_app_thesis.exception.BadRequestException;
import com.cloud_ml_app_thesis.repository.DatasetConfigurationRepository;
import com.cloud_ml_app_thesis.repository.dataset.DatasetRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.util.AlgorithmUtil;
import com.cloud_ml_app_thesis.util.DatasetUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.*;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ByteArrayResource;
import weka.clusterers.Clusterer;
import weka.core.Instances;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class VisualizationService {

    private final ModelRepository modelRepository;
    private final MinioService minioService;
    private final BucketResolver bucketResolver;
    private final DatasetService datasetService;
    private final DatasetConfigurationRepository datasetConfigurationRepository;

    //classification
    public ByteArrayResource generateBarChartFromMetricsJson(Integer modelId, User user) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        if(!user.getUsername().equals(model.getTraining().getUser().getUsername()) && model.getAccessibility().getName().equals(ModelAccessibilityEnum.PRIVATE)) {
            throw new AuthorizationDeniedException("User not authorized to train this algorithm");
        }

        String metricsUrl = model.getMetricsUrl();
        if (metricsUrl == null || metricsUrl.isBlank()) {
            throw new IllegalArgumentException("Model does not contain metrics URL");
        }
        log.info("Generating bar chart from metrics URL: {}", metricsUrl);

        String key = minioService.extractMinioKey(metricsUrl);
        String bucket = bucketResolver.resolve(BucketTypeEnum.METRICS);
        log.info("KEY, BUCKET: {} {}", key, bucket);
        byte[] content = minioService.downloadObjectAsBytes(bucket, key);

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(content);

            String accStr = json.get("accuracy").asText().replace("%", "").trim();
            String precStr = json.get("precision").asText().replace("%", "").trim();
            String recStr = json.get("recall").asText().replace("%", "").trim();
            String f1Str = json.get("f1Score").asText().replace("%", "").trim();

            List<String> labels = List.of("Accuracy", "Precision", "Recall", "F1-Score");
            List<Double> values = List.of(
                    Double.parseDouble(accStr),
                    Double.parseDouble(precStr),
                    Double.parseDouble(recStr),
                    Double.parseDouble(f1Str)
            );

            CategoryChart chart = new CategoryChartBuilder()
                    .width(600).height(400)
                    .title("Model Evaluation Metrics")
                    .xAxisTitle("Metric").yAxisTitle("Percentage")
                    .build();

            chart.addSeries("Performance", labels, values);
            chart.getStyler().setDefaultSeriesRenderStyle(CategorySeries.CategorySeriesRenderStyle.Bar);
            chart.getStyler().setYAxisDecimalPattern("##0.00");
            chart.getStyler().setLegendVisible(false);

            // export to byte[]
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);
            return new ByteArrayResource(baos.toByteArray());

        } catch (Exception e) {
            log.error("📉 Failed to generate bar chart for modelId={}", modelId, e);
            throw new RuntimeException("Unable to generate chart", e);
        }
    }

    public ByteArrayResource generateConfusionMatrixChart(Integer modelId, User user) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        if (!user.getUsername().equals(model.getTraining().getUser().getUsername())
                && model.getAccessibility().getName().equals(ModelAccessibilityEnum.PRIVATE)) {
            throw new AuthorizationDeniedException("User not authorized to access this model");
        }

        String metricsUrl = model.getMetricsUrl();
        if (metricsUrl == null || metricsUrl.isBlank()) {
            throw new IllegalArgumentException("Model does not contain metrics URL");
        }

        try {
            String key = minioService.extractMinioKey(metricsUrl);
            String bucket = bucketResolver.resolve(BucketTypeEnum.METRICS);
            byte[] content = minioService.downloadObjectAsBytes(bucket, key);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(content);

            JsonNode matrixNode = json.get("confusionMatrix");
            JsonNode labelsNode = json.get("classLabels");

            if (matrixNode == null || !matrixNode.isArray()) {
                throw new IllegalArgumentException("Confusion matrix not found in metrics.json");
            }

            int rows = matrixNode.size();
            int cols = matrixNode.get(0).size();
            double[][] matrix = new double[rows][cols];

            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    matrix[i][j] = matrixNode.get(i).get(j).asDouble();
                }
            }

            List<String> labels = new ArrayList<>();
            if (labelsNode != null && labelsNode.isArray()) {
                for (JsonNode label : labelsNode) {
                    labels.add(label.asText());
                }
            } else {
                for (int i = 0; i < rows; i++) {
                    labels.add("Class " + i);
                }
            }

            CategoryChart chart = new CategoryChartBuilder()
                    .width(600)
                    .height(600)
                    .title("Confusion Matrix")
                    .xAxisTitle("Predicted")
                    .yAxisTitle("Actual")
                    .build();

            for (int j = 0; j < cols; j++) {
                List<Double> colValues = new ArrayList<>();
                for (int i = 0; i < rows; i++) {
                    colValues.add(matrix[i][j]); // τώρα έχεις όλες τις τιμές για predicted[j]
                }
                chart.addSeries(labels.get(j), labels, colValues); // x-axis = actual, κάθε series = predicted class
            }


            chart.getStyler().setLegendVisible(true);
            chart.getStyler().setDefaultSeriesRenderStyle(CategorySeries.CategorySeriesRenderStyle.Bar);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);

            return new ByteArrayResource(baos.toByteArray());

        } catch (Exception e) {
            log.error("📉 Failed to generate confusion matrix for modelId={}", modelId, e);
            throw new RuntimeException("Unable to generate confusion matrix chart", e);
        }
    }

    //Regression
    public ByteArrayResource generateRegressionScatterPlot(Integer modelId, User user) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        if (!user.getUsername().equals(model.getTraining().getUser().getUsername())
                && model.getAccessibility().getName().equals(ModelAccessibilityEnum.PRIVATE)) {
            throw new AuthorizationDeniedException("User not authorized to access this model");
        }

        String metricsUrl = model.getMetricsUrl();
        if (metricsUrl == null || metricsUrl.isBlank()) {
            throw new IllegalArgumentException("Model does not contain metrics URL");
        }

        try {
            String key = minioService.extractMinioKey(metricsUrl);
            String bucket = bucketResolver.resolve(BucketTypeEnum.METRICS);
            byte[] content = minioService.downloadObjectAsBytes(bucket, key);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(content);

            JsonNode actualNode = json.get("actualValues");
            JsonNode predictedNode = json.get("predictedValues");

            if (actualNode == null || predictedNode == null || !actualNode.isArray() || !predictedNode.isArray()) {
                throw new IllegalArgumentException("Metrics file does not contain valid actual/predicted arrays");
            }

            List<Double> actual = new ArrayList<>();
            List<Double> predicted = new ArrayList<>();

            for (int i = 0; i < actualNode.size(); i++) {
                actual.add(actualNode.get(i).asDouble());
                predicted.add(predictedNode.get(i).asDouble());
            }

            XYChart chart = new XYChartBuilder()
                    .width(600)
                    .height(600)
                    .title("Actual vs Predicted")
                    .xAxisTitle("Actual")
                    .yAxisTitle("Predicted")
                    .build();

            XYSeries predictionSeries = chart.addSeries("Predictions", actual, predicted);
            predictionSeries.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
            predictionSeries.setMarker(SeriesMarkers.CIRCLE);
            predictionSeries.setMarkerColor(Color.BLUE);

            XYSeries identityLine = chart.addSeries("y = x", actual, actual);
            identityLine.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
            identityLine.setLineColor(Color.GRAY);
            identityLine.setMarker(SeriesMarkers.NONE);
            identityLine.setLineWidth(1.0f);

            chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
            chart.getStyler().setMarkerSize(6);
            chart.getStyler().setLegendVisible(true);
            chart.getStyler().setChartBackgroundColor(Color.WHITE);
            chart.getStyler().setPlotGridLinesVisible(true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);

            return new ByteArrayResource(baos.toByteArray());

        } catch (Exception e) {
            log.error("📉 Failed to generate regression scatter plot for modelId={}", modelId, e);
            throw new RuntimeException("Unable to generate scatter plot", e);
        }
    }

    //Regression
    public ByteArrayResource generateResidualPlot(Integer modelId, User user) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        if (!user.getUsername().equals(model.getTraining().getUser().getUsername())
                && model.getAccessibility().getName().equals(ModelAccessibilityEnum.PRIVATE)) {
            throw new AuthorizationDeniedException("User not authorized to access this model");
        }

        String metricsUrl = model.getMetricsUrl();
        if (metricsUrl == null || metricsUrl.isBlank()) {
            throw new IllegalArgumentException("Model does not contain metrics URL");
        }

        try {
            String key = minioService.extractMinioKey(metricsUrl);
            String bucket = bucketResolver.resolve(BucketTypeEnum.METRICS);
            byte[] content = minioService.downloadObjectAsBytes(bucket, key);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(content);

            JsonNode actualNode = json.get("actualValues");
            JsonNode predictedNode = json.get("predictedValues");

            if (actualNode == null || predictedNode == null || !actualNode.isArray() || !predictedNode.isArray()) {
                throw new IllegalArgumentException("Metrics file does not contain valid actual/predicted arrays");
            }

            List<Double> actual = new ArrayList<>();
            List<Double> predicted = new ArrayList<>();
            List<Double> residuals = new ArrayList<>();

            for (int i = 0; i < actualNode.size(); i++) {
                double a = actualNode.get(i).asDouble();
                double p = predictedNode.get(i).asDouble();
                actual.add(a);
                predicted.add(p);
                residuals.add(a - p);
            }

            XYChart chart = new XYChartBuilder()
                    .width(600)
                    .height(600)
                    .title("Residuals vs Predicted")
                    .xAxisTitle("Predicted Values")
                    .yAxisTitle("Residuals (Actual - Predicted)")
                    .build();

            XYSeries residualSeries = chart.addSeries("Residuals", predicted, residuals);
            residualSeries.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
            residualSeries.setMarker(SeriesMarkers.CIRCLE);
            residualSeries.setMarkerColor(Color.RED);

            chart.getStyler().setMarkerSize(6);
            chart.getStyler().setLegendVisible(true);
            chart.getStyler().setChartBackgroundColor(Color.WHITE);
            chart.getStyler().setPlotGridLinesVisible(true);

            // Zero line y = 0
            List<Double> zeroLineX = new ArrayList<>(predicted);
            List<Double> zeroLineY = zeroLineX.stream().map(x -> 0.0).toList();

            XYSeries zeroLine = chart.addSeries("y = 0", zeroLineX, zeroLineY);
            zeroLine.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
            zeroLine.setLineColor(Color.GRAY);
            zeroLine.setMarker(SeriesMarkers.NONE);
            zeroLine.setLineWidth(1.0f);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);

            return new ByteArrayResource(baos.toByteArray());

        } catch (Exception e) {
            log.error("📉 Failed to generate residual plot for modelId={}", modelId, e);
            throw new RuntimeException("Unable to generate residual plot", e);
        }
    }

    public ByteArrayResource generateClusterSizeChart(Integer modelId, User user) {
        AlgorithmUtil.ensureWekaClasspathCompatibility();
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        if (!user.getUsername().equals(model.getTraining().getUser().getUsername())
                && model.getAccessibility().getName().equals(ModelAccessibilityEnum.PRIVATE)) {
            throw new AuthorizationDeniedException("User not authorized to access this model");
        }

        //            if (!AlgorithmUtil.isClustering(data)) {
//                throw new BadRequestException("The model with ID " + modelId + " is not a clustering model.");
//            }

        String metricsUrl = model.getMetricsUrl();
        if (metricsUrl == null || metricsUrl.isBlank()) {
            throw new IllegalArgumentException("Model does not contain metrics URL");
        }

        try {
            String key = minioService.extractMinioKey(metricsUrl);
            String bucket = bucketResolver.resolve(BucketTypeEnum.METRICS);
            byte[] content = minioService.downloadObjectAsBytes(bucket, key);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(content);
            JsonNode assignments = json.get("clusterAssignments");

            if (assignments == null || !assignments.isArray()) {
                throw new IllegalArgumentException("clusterAssignments not found in metrics.json");
            }

            Map<Integer, Integer> clusterCounts = new TreeMap<>();
            for (JsonNode node : assignments) {
                String line = node.asText(); // "Instance 0 assigned to cluster 1"
                int clusterIndex = Integer.parseInt(line.split("cluster")[1].trim());
                clusterCounts.put(clusterIndex, clusterCounts.getOrDefault(clusterIndex, 0) + 1);
            }

            List<Integer> clusterLabels = new ArrayList<>(clusterCounts.keySet());
            List<Integer> clusterSizes = clusterLabels.stream()
                    .map(clusterCounts::get)
                    .toList();

            CategoryChart chart = new CategoryChartBuilder()
                    .width(600).height(400)
                    .title("Cluster Size Distribution")
                    .xAxisTitle("Cluster")
                    .yAxisTitle("Instances")
                    .build();

            chart.addSeries("Size", clusterLabels, clusterSizes);
            chart.getStyler().setDefaultSeriesRenderStyle(CategorySeries.CategorySeriesRenderStyle.Bar);
            chart.getStyler().setLegendVisible(false);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);
            return new ByteArrayResource(baos.toByteArray());

        } catch (Exception e) {
            log.error("📉 Failed to generate cluster size chart for modelId={}", modelId, e);
            throw new RuntimeException("Unable to generate cluster size chart", e);
        }
    }

    public ByteArrayResource generateClusterScatterPlot(Integer modelId, User user) {
        AlgorithmUtil.ensureWekaClasspathCompatibility();
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        if (!user.getUsername().equals(model.getTraining().getUser().getUsername())
                && model.getAccessibility().getName().equals(ModelAccessibilityEnum.PRIVATE)) {
            throw new AuthorizationDeniedException("User not authorized to access this model");
        }

        String metricsUrl = model.getMetricsUrl();

        if (metricsUrl == null || metricsUrl.isBlank()) {
            throw new IllegalArgumentException("Model does not contain metrics URL");
        }
            // 📥 Load cluster assignments
            try {
            String key = minioService.extractMinioKey(metricsUrl);
            String bucket = bucketResolver.resolve(BucketTypeEnum.METRICS);
            byte[] content = minioService.downloadObjectAsBytes(bucket, key);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(content);
            JsonNode assignments = json.get("clusterAssignments");

            if (assignments == null || !assignments.isArray()) {
                throw new IllegalArgumentException("clusterAssignments not found in metrics.json");
            }

            // 👇 Extract assigned cluster per instance
            List<Integer> clusterAssignments = new ArrayList<>();
            for (JsonNode node : assignments) {
                String line = node.asText(); // e.g., "Instance 0 assigned to cluster 1"
                int clusterIndex = Integer.parseInt(line.split("cluster")[1].trim());
                clusterAssignments.add(clusterIndex);
            }

                JsonNode projection = json.get("projection2D");
                if (projection == null || !projection.isArray()) {
                    throw new IllegalArgumentException("projection2D not found in metrics.json");
                }

                Map<Integer, List<Double>> xMap = new HashMap<>();
                Map<Integer, List<Double>> yMap = new HashMap<>();

                for (int i = 0; i < projection.size(); i++) {
                    int cluster = clusterAssignments.get(i);
                    double x = projection.get(i).get("x").asDouble();
                    double y = projection.get(i).get("y").asDouble();

                    xMap.computeIfAbsent(cluster, k -> new ArrayList<>()).add(x);
                    yMap.computeIfAbsent(cluster, k -> new ArrayList<>()).add(y);
                }

                XYChart chart = new XYChartBuilder()
                        .width(600)
                        .height(500)
                        .title("Clustered Instances")
                        .xAxisTitle("X")
                        .yAxisTitle("Y")
                        .build();

                for (Integer clusterId : xMap.keySet()) {
                    chart.addSeries("Cluster " + clusterId, xMap.get(clusterId), yMap.get(clusterId))
                            .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
                }

            chart.getStyler().setLegendVisible(true);
            chart.getStyler().setChartBackgroundColor(Color.WHITE);
            chart.getStyler().setPlotGridLinesVisible(true);
            chart.getStyler().setMarkerSize(6);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);
            return new ByteArrayResource(baos.toByteArray());

        } catch (Exception e) {
            log.error("📉 Failed to generate cluster scatter plot for modelId={}", modelId, e);
            throw new RuntimeException("Unable to generate cluster scatter plot", e);
        }
    }


}
