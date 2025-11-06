-- Add created_at column to models table
ALTER TABLE models
ADD COLUMN created_at TIMESTAMP(6) WITH TIME ZONE;
