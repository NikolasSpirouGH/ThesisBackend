-- Add execution_mode column to custom_algorithms table
-- PYTHON_TEMPLATE = current behavior (injected train.py/predict.py + algorithm.py extraction)
-- GENERIC_BYOC = black-box container execution (container's own ENTRYPOINT)
ALTER TABLE custom_algorithms
    ADD COLUMN execution_mode VARCHAR(30) NOT NULL DEFAULT 'PYTHON_TEMPLATE';
