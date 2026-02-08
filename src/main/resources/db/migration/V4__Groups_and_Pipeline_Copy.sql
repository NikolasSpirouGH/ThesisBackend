-- =====================================================
-- V4: Groups and Pipeline Copy Support
-- =====================================================

-- ============================================
-- 1. GROUPS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS groups (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    leader_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_groups_leader FOREIGN KEY (leader_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Group Members Join Table (Many-to-Many)
CREATE TABLE IF NOT EXISTS group_members (
    group_id INTEGER NOT NULL,
    user_id UUID NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_group_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ============================================
-- 2. DATASET GROUP SHARES (extends sharing to groups)
-- ============================================
CREATE TABLE IF NOT EXISTS dataset_group_shares (
    id SERIAL PRIMARY KEY,
    dataset_id INTEGER NOT NULL,
    group_id INTEGER NOT NULL,
    shared_by_user_id UUID NOT NULL,
    shared_at TIMESTAMP WITH TIME ZONE NOT NULL,
    comment TEXT,
    CONSTRAINT fk_dataset_group_shares_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE,
    CONSTRAINT fk_dataset_group_shares_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_dataset_group_shares_shared_by FOREIGN KEY (shared_by_user_id) REFERENCES users(id),
    CONSTRAINT uk_dataset_group_shares UNIQUE (dataset_id, group_id)
);

-- ============================================
-- 3. MODEL GROUP SHARES (extends sharing to groups)
-- ============================================
CREATE TABLE IF NOT EXISTS model_group_shares (
    id SERIAL PRIMARY KEY,
    model_id INTEGER NOT NULL,
    group_id INTEGER NOT NULL,
    shared_by_user_id UUID NOT NULL,
    shared_at TIMESTAMP WITH TIME ZONE NOT NULL,
    comment TEXT,
    CONSTRAINT fk_model_group_shares_model FOREIGN KEY (model_id) REFERENCES models(id) ON DELETE CASCADE,
    CONSTRAINT fk_model_group_shares_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_model_group_shares_shared_by FOREIGN KEY (shared_by_user_id) REFERENCES users(id),
    CONSTRAINT uk_model_group_shares UNIQUE (model_id, group_id)
);

-- ============================================
-- 4. PIPELINE COPY ACTION TYPES (CONST table)
-- ============================================
CREATE TABLE IF NOT EXISTS CONST_PIPELINE_COPY_ACTION_TYPES (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

INSERT INTO CONST_PIPELINE_COPY_ACTION_TYPES (name, description) VALUES
    ('COPY_INITIATED', 'Pipeline copy was initiated'),
    ('COPY_COMPLETED', 'Pipeline copy completed successfully'),
    ('COPY_FAILED', 'Pipeline copy failed'),
    ('COPY_ROLLED_BACK', 'Pipeline copy was rolled back due to error')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 5. PIPELINE COPY TABLE (main copy record)
-- ============================================
CREATE TABLE IF NOT EXISTS pipeline_copies (
    id SERIAL PRIMARY KEY,
    source_training_id INTEGER NOT NULL,
    target_training_id INTEGER,
    copied_by_user_id UUID NOT NULL,
    copy_for_user_id UUID NOT NULL,
    copy_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS',
    error_message TEXT,
    CONSTRAINT fk_pipeline_copies_source FOREIGN KEY (source_training_id) REFERENCES trainings(id),
    CONSTRAINT fk_pipeline_copies_target FOREIGN KEY (target_training_id) REFERENCES trainings(id),
    CONSTRAINT fk_pipeline_copies_copied_by FOREIGN KEY (copied_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_pipeline_copies_copy_for FOREIGN KEY (copy_for_user_id) REFERENCES users(id)
);

-- ============================================
-- 6. PIPELINE COPY MAPPINGS (provenance: old -> new ID)
-- ============================================
CREATE TABLE IF NOT EXISTS pipeline_copy_mappings (
    id SERIAL PRIMARY KEY,
    pipeline_copy_id INTEGER NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    source_entity_id INTEGER NOT NULL,
    target_entity_id INTEGER,
    minio_source_key VARCHAR(500),
    minio_target_key VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    CONSTRAINT fk_pipeline_copy_mappings_copy FOREIGN KEY (pipeline_copy_id) REFERENCES pipeline_copies(id) ON DELETE CASCADE
);

-- ============================================
-- 7. PIPELINE COPY HISTORY (audit trail)
-- ============================================
CREATE TABLE IF NOT EXISTS pipeline_copy_history (
    id BIGSERIAL PRIMARY KEY,
    pipeline_copy_id INTEGER NOT NULL,
    action_type INTEGER NOT NULL,
    action_by_user_id UUID NOT NULL,
    action_at TIMESTAMP WITH TIME ZONE NOT NULL,
    details TEXT,
    CONSTRAINT fk_pipeline_copy_history_copy FOREIGN KEY (pipeline_copy_id) REFERENCES pipeline_copies(id) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_copy_history_action_type FOREIGN KEY (action_type) REFERENCES CONST_PIPELINE_COPY_ACTION_TYPES(id),
    CONSTRAINT fk_pipeline_copy_history_action_by FOREIGN KEY (action_by_user_id) REFERENCES users(id)
);

-- ============================================
-- 8. EXTEND DATASET SHARE ACTION TYPES
-- ============================================
INSERT INTO CONST_DATASET_SHARE_ACTION_TYPES (name, description) VALUES
    ('GROUP_SHARE', 'Dataset was shared with a group'),
    ('GROUP_UNSHARE', 'Dataset group sharing was revoked')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 9. EXTEND MODEL SHARE ACTION TYPES
-- ============================================
INSERT INTO CONST_MODEL_SHARE_ACTION_TYPES (name, description) VALUES
    ('GROUP_SHARE', 'Model was shared with a group'),
    ('GROUP_UNSHARE', 'Model group sharing was revoked')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 10. INDEXES
-- ============================================
CREATE INDEX IF NOT EXISTS idx_groups_leader ON groups(leader_id);
CREATE INDEX IF NOT EXISTS idx_groups_name ON groups(name);
CREATE INDEX IF NOT EXISTS idx_group_members_group ON group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_group_members_user ON group_members(user_id);
CREATE INDEX IF NOT EXISTS idx_dataset_group_shares_dataset ON dataset_group_shares(dataset_id);
CREATE INDEX IF NOT EXISTS idx_dataset_group_shares_group ON dataset_group_shares(group_id);
CREATE INDEX IF NOT EXISTS idx_model_group_shares_model ON model_group_shares(model_id);
CREATE INDEX IF NOT EXISTS idx_model_group_shares_group ON model_group_shares(group_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_copies_source ON pipeline_copies(source_training_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_copies_copied_by ON pipeline_copies(copied_by_user_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_copies_status ON pipeline_copies(status);
CREATE INDEX IF NOT EXISTS idx_pipeline_copy_mappings_copy ON pipeline_copy_mappings(pipeline_copy_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_copy_mappings_entity_type ON pipeline_copy_mappings(entity_type);
CREATE INDEX IF NOT EXISTS idx_pipeline_copy_history_copy ON pipeline_copy_history(pipeline_copy_id);
