package com.cloud_ml_app_thesis.dto.train;

import lombok.Data;

import java.util.List;

@Data
public class RegressionEvaluationResult implements TrainMetricResult {
    private final double rmse;
    private final double mae;
    private final double rSquared;
    private final String summary;

    private final List<Double> actualValues;
    private final List<Double> predictedValues;

    @Override
    public String getSummary() {
        return summary;
    }
}