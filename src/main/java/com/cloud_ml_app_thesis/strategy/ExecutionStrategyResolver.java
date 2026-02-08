package com.cloud_ml_app_thesis.strategy;

import org.springframework.stereotype.Component;

import com.cloud_ml_app_thesis.enumeration.ExecutionMode;

import lombok.RequiredArgsConstructor;

/**
 * Resolves the appropriate {@link CustomExecutionStrategy} based on the algorithm's {@link ExecutionMode}.
 */
@Component
@RequiredArgsConstructor
public class ExecutionStrategyResolver {

    private final PythonTemplateExecutionStrategy pythonTemplateStrategy;
    private final GenericByocExecutionStrategy genericByocStrategy;

    public CustomExecutionStrategy resolve(ExecutionMode mode) {
        return switch (mode) {
            case PYTHON_TEMPLATE -> pythonTemplateStrategy;
            case GENERIC_BYOC -> genericByocStrategy;
        };
    }
}
