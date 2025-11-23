-- ============================================
-- V11: Add Model Execution Accessibility
-- ============================================

-- 1. Create Model Execution Accessibilities table
CREATE TABLE IF NOT EXISTS CONST_MODEL_EXECUTION_ACCESSIBILITIES (
                                                                     id SERIAL PRIMARY KEY,
                                                                     name VARCHAR(255) NOT NULL UNIQUE,
                                                                     description VARCHAR(255) NOT NULL
);

-- 2. Insert reference data
INSERT INTO CONST_MODEL_EXECUTION_ACCESSIBILITIES (name, description) VALUES
                                                                          ('PUBLIC', 'Execution results are publicly accessible'),
                                                                          ('PRIVATE', 'Execution results are private'),
                                                                          ('RESTRICTED', 'Execution results have restricted access')
ON CONFLICT (name) DO NOTHING;

-- 3. Add accessibility_id column to models_executions table
ALTER TABLE models_executions
    ADD COLUMN IF NOT EXISTS accessibility_id INTEGER;

-- 4. Set default accessibility to PRIVATE for existing executions
UPDATE models_executions
SET accessibility_id = (SELECT id FROM CONST_MODEL_EXECUTION_ACCESSIBILITIES WHERE name = 'PRIVATE')
WHERE accessibility_id IS NULL;

-- 5. Add foreign key constraint (skip if already exists)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_model_executions_accessibility'
    ) THEN
        ALTER TABLE models_executions
            ADD CONSTRAINT fk_model_executions_accessibility
                FOREIGN KEY (accessibility_id)
                    REFERENCES CONST_MODEL_EXECUTION_ACCESSIBILITIES(id);
    END IF;
END $$;

-- 6. Create index for better query performance
CREATE INDEX IF NOT EXISTS idx_model_executions_accessibility
    ON models_executions(accessibility_id);