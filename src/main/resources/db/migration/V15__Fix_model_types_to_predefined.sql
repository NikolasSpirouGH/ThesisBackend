-- ====================================================
-- V15: Fix Model Types from CUSTOM to PREDEFINED
-- Purpose: Update models that use Weka algorithms to be PREDEFINED instead of CUSTOM
-- ====================================================

DO $$
DECLARE
    predefined_type_id INT;
    custom_type_id INT;
BEGIN
    -- Get the type IDs
    SELECT id INTO predefined_type_id FROM const_model_types WHERE name = 'PREDEFINED' LIMIT 1;
    SELECT id INTO custom_type_id FROM const_model_types WHERE name = 'CUSTOM' LIMIT 1;

    -- Check if both types exist
    IF predefined_type_id IS NULL OR custom_type_id IS NULL THEN
        RAISE NOTICE 'Model types not found, skipping fix';
        RETURN;
    END IF;

    -- Update all models that have algorithm_configuration_id (predefined algorithms)
    -- to use PREDEFINED model type instead of CUSTOM
    UPDATE models m
    SET model_type_id = predefined_type_id
    FROM trainings t
    WHERE m.training_id = t.id
      AND t.algorithm_configuration_id IS NOT NULL
      AND m.model_type_id = custom_type_id;

    RAISE NOTICE 'Updated models with predefined algorithms to PREDEFINED type';
END $$;
