package com.cloud_ml_app_thesis.dto.train;

import lombok.Data;

import java.util.List;

@Data
public class EvaluationResult implements TrainMetricResult{
        private final String accuracy;
        private final String precision;
        private final String recall;
        private final String f1Score;
        private final String summary;
        private final List<List<Integer>> confusionMatrix;
        private final List<String> classLabels;

        @Override
        public String getSummary() {
        return summary;
}
}