-- ====================================================
-- V7: Fix share action type names to match Java enums
-- Problem: V2 seeded SHARED/UNSHARED but enums define
--          SHARE/REMOVE/DECLINED/GROUP_SHARE/GROUP_UNSHARE (datasets)
--          SHARE/REVOKE/VIEWED/GROUP_SHARE/GROUP_UNSHARE (models)
-- ====================================================

-- ============================================
-- 1. FIX DATASET SHARE ACTION TYPES
-- ============================================
-- Rename existing values to match DatasetShareActionTypeEnum
UPDATE const_dataset_share_action_types SET name = 'SHARE', description = 'Dataset was shared' WHERE name = 'SHARED';
UPDATE const_dataset_share_action_types SET name = 'REMOVE', description = 'Dataset sharing was removed' WHERE name = 'UNSHARED';

-- Add missing action types
INSERT INTO const_dataset_share_action_types (name, description) VALUES
    ('DECLINED', 'Dataset sharing was declined'),
    ('GROUP_SHARE', 'Dataset was shared with a group'),
    ('GROUP_UNSHARE', 'Dataset group sharing was revoked')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 2. FIX MODEL SHARE ACTION TYPES
-- ============================================
-- Rename existing values to match ModelShareActionTypeEnum
UPDATE const_model_share_action_types SET name = 'SHARE', description = 'Model was shared' WHERE name = 'SHARED';
UPDATE const_model_share_action_types SET name = 'REVOKE', description = 'Model sharing was revoked' WHERE name = 'UNSHARED';

-- Add missing action types
INSERT INTO const_model_share_action_types (name, description) VALUES
    ('VIEWED', 'Model was viewed'),
    ('GROUP_SHARE', 'Model was shared with a group'),
    ('GROUP_UNSHARE', 'Model group sharing was revoked')
ON CONFLICT (name) DO NOTHING;
