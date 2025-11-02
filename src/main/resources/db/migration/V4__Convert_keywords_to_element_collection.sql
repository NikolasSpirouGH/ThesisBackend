-- ============================================
-- V4: Convert keywords from entity to element collection
-- Purpose: Change keywords from a separate entity table to a simple string collection
-- ============================================

-- Drop the old foreign key constraint from model_keywords table
ALTER TABLE model_keywords DROP CONSTRAINT IF EXISTS fk_model_keywords_keyword;
ALTER TABLE model_keywords DROP CONSTRAINT IF EXISTS fk_model_keywords_model;

-- Drop the old model_keywords junction table
DROP TABLE IF EXISTS model_keywords CASCADE;

-- Drop the keywords entity table
DROP TABLE IF EXISTS keywords CASCADE;

-- Create new model_keywords table as an element collection
CREATE TABLE model_keywords (
    model_id INTEGER NOT NULL,
    keyword VARCHAR(255),
    CONSTRAINT fk_model_keywords_model FOREIGN KEY (model_id) REFERENCES models(id) ON DELETE CASCADE
);

-- Create index for better query performance
CREATE INDEX idx_model_keywords_model_id ON model_keywords(model_id);
CREATE INDEX idx_model_keywords_keyword ON model_keywords(keyword);
