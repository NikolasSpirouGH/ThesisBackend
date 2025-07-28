package com.cloud_ml_app_thesis.dto.train;

import lombok.Data;

import java.awt.geom.Point2D;
import java.util.List;

@Data
public class ClusterEvaluationResult implements TrainMetricResult {

    private final int numClusters;
    private final double logLikelihood;
    private final String[] clusterAssignments;
    private final String summary;
    private final List<Point2D> projection2D;

    @Override
    public String getSummary() {
        return summary;
    }
}
