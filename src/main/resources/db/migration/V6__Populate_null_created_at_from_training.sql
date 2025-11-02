-- Set createdAt based on the associated training's finished date for existing models
UPDATE models m
SET created_at = (SELECT t.finished_date FROM trainings t WHERE t.id = m.training_id)
WHERE m.created_at IS NULL AND m.training_id IS NOT NULL;

-- For any models without a training_id, set to current timestamp
UPDATE models m
SET created_at = NOW()
WHERE m.created_at IS NULL;
