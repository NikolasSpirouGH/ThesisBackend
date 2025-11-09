-- V10: Fix all table sequences to match existing data
-- This is necessary because previous migrations inserted data with explicit IDs,
-- which doesn't automatically update PostgreSQL sequences

-- Fix datasets sequence (true = is_called, so next value will be MAX+1)
SELECT setval('datasets_id_seq', COALESCE((SELECT MAX(id) FROM datasets), 1), true);

-- Fix trainings sequence
SELECT setval('trainings_id_seq', COALESCE((SELECT MAX(id) FROM trainings), 1), true);

-- Fix models sequence
SELECT setval('models_id_seq', COALESCE((SELECT MAX(id) FROM models), 1), true);

-- Fix categories sequence
SELECT setval('categories_id_seq', COALESCE((SELECT MAX(id) FROM categories), 1), true);

-- Fix algorithm_configurations sequence
SELECT setval('algorithm_configurations_id_seq', COALESCE((SELECT MAX(id) FROM algorithm_configurations), 1), true);

-- Fix dataset_configurations sequence
SELECT setval('dataset_configurations_id_seq', COALESCE((SELECT MAX(id) FROM dataset_configurations), 1), true);

-- Fix custom_algorithm_configurations sequence
SELECT setval('custom_algorithm_configurations_id_seq', COALESCE((SELECT MAX(id) FROM custom_algorithm_configurations), 1), true);

-- Log the results without consuming sequence values
DO $$
BEGIN
    RAISE NOTICE '✅ Sequences fixed to match MAX(id) from their respective tables';
    RAISE NOTICE 'datasets_id_seq: %', (SELECT last_value FROM datasets_id_seq);
    RAISE NOTICE 'trainings_id_seq: %', (SELECT last_value FROM trainings_id_seq);
    RAISE NOTICE 'models_id_seq: %', (SELECT last_value FROM models_id_seq);
END $$;
