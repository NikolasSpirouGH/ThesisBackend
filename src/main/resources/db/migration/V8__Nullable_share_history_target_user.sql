-- ====================================================
-- V8: Make target user nullable in share history tables
-- Group shares don't have a specific target user
-- ====================================================

ALTER TABLE dataset_share_history ALTER COLUMN target_user_id DROP NOT NULL;
ALTER TABLE model_share_history ALTER COLUMN shared_with_user_id DROP NOT NULL;
