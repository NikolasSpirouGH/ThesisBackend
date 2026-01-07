-- ====================================================
-- V3: Test Data (Optional - For Development Only)
-- Purpose: Load test data for development environment
-- ====================================================
-- NOTE: Remove this file or keep it empty for production deployments
-- ====================================================

-- This file is intentionally left minimal.
-- Add test algorithms, datasets, trainings, and models here as needed for development.

-- Example: Add test algorithms
-- INSERT INTO algorithms (name, description, type_id, options, class_name) VALUES
--     ('Random Forest', 'Random Forest Classifier', (SELECT id FROM CONST_ALGORITHM_TYPES WHERE name = 'CLASSIFICATION'), '{}', 'RandomForestClassifier')
-- ON CONFLICT (name) DO NOTHING;

-- Example: Add test categories
-- INSERT INTO categories (name, description, created_by, deleted) VALUES
--     ('Healthcare', 'Medical and healthcare related datasets', (SELECT id FROM users WHERE username = 'bigspy'), false),
--     ('Finance', 'Financial datasets', (SELECT id FROM users WHERE username = 'bigspy'), false)
-- ON CONFLICT (name) DO NOTHING;

-- Add your test data below:
-- ...

-- ====================================================
-- END OF TEST DATA
-- ====================================================
