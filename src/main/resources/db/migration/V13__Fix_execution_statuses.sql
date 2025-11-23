-- ============================================
-- V13: Fix Execution Statuses
-- Purpose: Update NULL status_id to COMPLETED
-- ============================================

DO $$
    DECLARE
        exec_status_completed_id INT;
    BEGIN
        -- Get COMPLETED status ID (not SUCCESS)
        SELECT id INTO exec_status_completed_id FROM const_model_exec_statuses WHERE name = 'COMPLETED' LIMIT 1;

        -- Update all NULL status_id to COMPLETED
        UPDATE models_executions
        SET status_id = exec_status_completed_id
        WHERE status_id IS NULL;

        RAISE NOTICE '✅ Fixed execution statuses (NULL → COMPLETED)';

    END $$;
