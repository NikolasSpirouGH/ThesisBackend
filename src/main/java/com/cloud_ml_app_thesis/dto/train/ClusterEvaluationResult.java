package com.cloud_ml_app_thesis.dto.train;

import lombok.Data;

@Data
public class ClusterEvaluationResult implements TrainMetricResult {

    private final int numClusters;
    private final double logLikelihood;
    private final String[] clusterAssignments;
    private final String summary;

    @Override
    public String getSummary() {
        return summary;
    }
}
