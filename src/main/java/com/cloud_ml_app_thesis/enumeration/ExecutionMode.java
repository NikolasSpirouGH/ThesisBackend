package com.cloud_ml_app_thesis.enumeration;

/**
 * Defines the execution mode for custom algorithms.
 * <ul>
 *   <li>PYTHON_TEMPLATE: Platform injects train.py/predict.py templates and extracts algorithm.py from the image.
 *       Container command is overridden to run 'python train.py'. (Current/legacy behavior)</li>
 *   <li>GENERIC_BYOC: Container runs its own ENTRYPOINT/CMD. No scripts are injected or extracted.
 *       Platform only provides dataset, params, and environment variables.</li>
 * </ul>
 */
public enum ExecutionMode {
    PYTHON_TEMPLATE,
    GENERIC_BYOC
}
