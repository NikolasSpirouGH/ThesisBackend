-- ====================================================
-- V14: Fix Algorithm Configurations with Actual Algorithm IDs
-- Purpose: Update existing algorithm_configurations to use different algorithms
-- ====================================================

DO $$
DECLARE
    algorithm_ids INT[];
    config_ids INT[];
    i INT;
BEGIN
    -- Get first 20 algorithm IDs
    SELECT ARRAY(SELECT id FROM algorithms ORDER BY id LIMIT 20) INTO algorithm_ids;

    -- Get all algorithm configuration IDs (should be 40 from V8 and V10)
    SELECT ARRAY(SELECT id FROM algorithm_configurations ORDER BY id) INTO config_ids;

    -- Check if we have enough algorithms
    IF array_length(algorithm_ids, 1) < 20 THEN
        RAISE NOTICE 'Not enough algorithms (%), skipping fix', COALESCE(array_length(algorithm_ids, 1), 0);
        RETURN;
    END IF;

    -- Check if we have configurations to update
    IF array_length(config_ids, 1) = 0 THEN
        RAISE NOTICE 'No algorithm configurations found, skipping fix';
        RETURN;
    END IF;

    -- Update each configuration with a different algorithm (cycle through the 20 algorithms)
    FOR i IN 1..array_length(config_ids, 1) LOOP
        -- Use modulo to cycle through algorithms (1-20)
        UPDATE algorithm_configurations
        SET algorithm_id = algorithm_ids[((i - 1) % 20) + 1]
        WHERE id = config_ids[i];
    END LOOP;

    RAISE NOTICE 'Updated % algorithm configurations with algorithm IDs', array_length(config_ids, 1);
END $$;
