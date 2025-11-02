-- Ensure training_id is NOT NULL and add CASCADE DELETE
-- When a training is deleted, the associated model should also be deleted

-- First, check if there are any models without training_id (there shouldn't be)
-- If any exist, this will fail - which is correct behavior
ALTER TABLE models
    ALTER COLUMN training_id SET NOT NULL;

-- Drop existing foreign key constraint if it exists
ALTER TABLE models
    DROP CONSTRAINT IF EXISTS fk_models_training;

-- Add foreign key with ON DELETE CASCADE
ALTER TABLE models
    ADD CONSTRAINT fk_models_training
    FOREIGN KEY (training_id)
    REFERENCES trainings(id)
    ON DELETE CASCADE;
