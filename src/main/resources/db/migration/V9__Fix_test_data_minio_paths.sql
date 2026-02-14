-- ====================================================
-- V9: Fix test data MinIO paths
-- The test data used 'ml-datasets'/'ml-models' prefixes
-- but actual MinIO buckets are 'datasets'/'models'
-- ====================================================

UPDATE datasets SET file_path = REPLACE(file_path, 'ml-datasets/', 'datasets/') WHERE file_path LIKE 'ml-datasets/%';
UPDATE models SET model_url = REPLACE(model_url, 'ml-models/', 'models/') WHERE model_url LIKE 'ml-models/%';
