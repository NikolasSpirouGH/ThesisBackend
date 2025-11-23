-- ============================================
-- V12: Add Test Model Executions
-- Purpose: Create test executions for existing models
-- ============================================

DO $$
    DECLARE
        emma_id UUID;
        david_id UUID;
        bigspy_id UUID;
        nickriz_id UUID;
        model_ids INT[];
        dataset_ids INT[];
        exec_status_success_id INT;
        exec_status_failed_id INT;
        exec_status_running_id INT;
        exec_accessibility_public_id INT;
        exec_accessibility_private_id INT;
        exec_accessibility_restricted_id INT;
        i INT;
        model_id INT;
        dataset_id INT;
        user_id UUID;
        status_id INT;
        accessibility_id INT;
    BEGIN
        -- Get user IDs
        SELECT id INTO emma_id FROM users WHERE username = 'emma' LIMIT 1;
        SELECT id INTO david_id FROM users WHERE username = 'david' LIMIT 1;
        SELECT id INTO bigspy_id FROM users WHERE username = 'bigspy' LIMIT 1;
        SELECT id INTO nickriz_id FROM users WHERE username = 'nickriz' LIMIT 1;

        -- Get model IDs (from V10 migration)
        SELECT ARRAY_AGG(id) INTO model_ids FROM models LIMIT 20;

        -- Get dataset IDs
        SELECT ARRAY_AGG(id) INTO dataset_ids FROM datasets LIMIT 10;

        -- Get execution status IDs
        SELECT id INTO exec_status_success_id FROM const_model_exec_statuses WHERE name = 'SUCCESS' LIMIT 1;
        SELECT id INTO exec_status_failed_id FROM const_model_exec_statuses WHERE name = 'FAILED' LIMIT 1;
        SELECT id INTO exec_status_running_id FROM const_model_exec_statuses WHERE name = 'RUNNING' LIMIT 1;

        -- Get execution accessibility IDs
        SELECT id INTO exec_accessibility_public_id FROM const_model_execution_accessibilities WHERE name = 'PUBLIC' LIMIT 1;
        SELECT id INTO exec_accessibility_private_id FROM const_model_execution_accessibilities WHERE name = 'PRIVATE' LIMIT 1;
        SELECT id INTO exec_accessibility_restricted_id FROM const_model_execution_accessibilities WHERE name = 'RESTRICTED' LIMIT 1;

        -- Create 30 test executions
        FOR i IN 1..30 LOOP
                -- Rotate through users
                CASE (i % 4)
                    WHEN 0 THEN user_id := emma_id;
                    WHEN 1 THEN user_id := david_id;
                    WHEN 2 THEN user_id := bigspy_id;
                    ELSE user_id := nickriz_id;
                    END CASE;

                -- Pick model (rotate through available models)
                model_id := model_ids[(i % array_length(model_ids, 1)) + 1];

                -- Pick dataset (rotate through available datasets)
                IF array_length(dataset_ids, 1) > 0 THEN
                    dataset_id := dataset_ids[(i % array_length(dataset_ids, 1)) + 1];
                ELSE
                    dataset_id := NULL;
                END IF;

                -- Rotate through statuses (mostly SUCCESS, some FAILED)
                CASE (i % 10)
                    WHEN 0 THEN status_id := exec_status_failed_id;
                    WHEN 5 THEN status_id := exec_status_running_id;
                    ELSE status_id := exec_status_success_id;
                    END CASE;

                -- Rotate through accessibility (mostly PRIVATE, some PUBLIC)
                CASE (i % 5)
                    WHEN 0 THEN accessibility_id := exec_accessibility_public_id;
                    WHEN 3 THEN accessibility_id := exec_accessibility_restricted_id;
                    ELSE accessibility_id := exec_accessibility_private_id;
                    END CASE;

                -- Insert execution
                INSERT INTO models_executions (
                    model_id,
                    dataset_id,
                    executed_at,
                    status_id,
                    prediction_result,
                    executed_by_user_id,
                    accessibility_id
                ) VALUES (
                             model_id,
                             dataset_id,
                             NOW() - (i || ' days')::INTERVAL,
                             status_id,
                             CASE
                                 WHEN status_id = exec_status_success_id THEN 'minio://predictions/execution_' || i || '_result.csv'
                                 WHEN status_id = exec_status_failed_id THEN NULL
                                 ELSE NULL
                                 END,
                             user_id,
                             accessibility_id
                         );
            END LOOP;

        RAISE NOTICE '✅ Created 30 test model executions';

    END $$;
