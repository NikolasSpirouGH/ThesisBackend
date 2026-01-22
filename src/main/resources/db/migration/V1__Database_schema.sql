-- =====================================================
-- V1: Complete Database Schema (Consolidated)
-- =====================================================
-- This migration creates all tables, constraints, and indexes
-- for the Cloud ML Application database schema.
-- Consolidates all schema changes from previous migrations.
-- =====================================================

-- Enable UUID extension for PostgreSQL
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =====================================================
-- CONST TABLES (Reference/Lookup Tables)
-- =====================================================

-- User Statuses
CREATE TABLE IF NOT EXISTS CONST_USER_STATUSES (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- Algorithm Types
CREATE TABLE IF NOT EXISTS CONST_ALGORITHM_TYPES (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Model Types
CREATE TABLE IF NOT EXISTS CONST_MODEL_TYPES (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Training Statuses
CREATE TABLE IF NOT EXISTS CONST_TRAINING_STATUSES (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1000)
);

-- Model Statuses
CREATE TABLE IF NOT EXISTS CONST_MODEL_STATUSES (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1000)
);

-- Model Execution Statuses
CREATE TABLE IF NOT EXISTS CONST_MODEL_EXEC_STATUSES (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1000)
);

-- Model Execution Accessibilities
CREATE TABLE IF NOT EXISTS CONST_MODEL_EXECUTION_ACCESSIBILITIES (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

-- Category Request Statuses
CREATE TABLE IF NOT EXISTS CONST_CATEGORY_REQUEST_STATUSES (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1000)
);

-- Dataset Accessibilities
CREATE TABLE IF NOT EXISTS CONST_DATASET_ACCESSIBILITIES (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- Model Accessibilities
CREATE TABLE IF NOT EXISTS CONST_MODEL_ACCESSIBILITES (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

-- Algorithm Accessibilities
CREATE TABLE IF NOT EXISTS CONST_ALGORITHM_ACCESSIBILITIES (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- Dataset Share Action Types
CREATE TABLE IF NOT EXISTS CONST_DATASET_SHARE_ACTION_TYPES (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

-- Model Share Action Types
CREATE TABLE IF NOT EXISTS CONST_MODEL_SHARE_ACTION_TYPES (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

-- =====================================================
-- CORE ENTITY TABLES
-- =====================================================

-- Roles Table
CREATE TABLE IF NOT EXISTS roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- Users Table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(255) UNIQUE,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255) UNIQUE,
    password VARCHAR(255),
    age INTEGER,
    profession VARCHAR(255),
    country VARCHAR(255),
    status_id INTEGER,
    CONSTRAINT fk_users_status FOREIGN KEY (status_id) REFERENCES CONST_USER_STATUSES(id)
);

-- User Roles Join Table (Many-to-Many)
CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL,
    role_id INTEGER NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- JWT Tokens Table (with CASCADE DELETE)
CREATE TABLE IF NOT EXISTS jwt_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    token VARCHAR(1000) NOT NULL,
    user_id UUID,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    expired BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    revoked_at TIMESTAMP,
    CONSTRAINT fk_jwt_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Password Reset Tokens Table (with CASCADE DELETE)
CREATE TABLE IF NOT EXISTS tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    CONSTRAINT fk_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Categories Table (created_by is nullable per V17)
CREATE TABLE IF NOT EXISTS categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(2000),
    created_by UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_categories_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);

-- Category Hierarchy Join Table (Self-referencing Many-to-Many)
CREATE TABLE IF NOT EXISTS category_hierarchy (
    child_category_id INTEGER NOT NULL,
    parent_category_id INTEGER NOT NULL,
    PRIMARY KEY (child_category_id, parent_category_id),
    CONSTRAINT fk_category_hierarchy_child FOREIGN KEY (child_category_id) REFERENCES categories(id),
    CONSTRAINT fk_category_hierarchy_parent FOREIGN KEY (parent_category_id) REFERENCES categories(id)
);

-- Category Requests Table
CREATE TABLE IF NOT EXISTS category_requests (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(5000),
    status_id INTEGER NOT NULL,
    requested_by UUID NOT NULL,
    processed_by UUID,
    approved_category_id INTEGER,
    rejection_reason VARCHAR(5000),
    requested_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    CONSTRAINT fk_category_requests_status FOREIGN KEY (status_id) REFERENCES CONST_CATEGORY_REQUEST_STATUSES(id),
    CONSTRAINT fk_category_requests_requested_by FOREIGN KEY (requested_by) REFERENCES users(id),
    CONSTRAINT fk_category_requests_processed_by FOREIGN KEY (processed_by) REFERENCES users(id),
    CONSTRAINT fk_category_requests_approved_category FOREIGN KEY (approved_category_id) REFERENCES categories(id)
);

-- Category Request Parents Join Table (Many-to-Many)
CREATE TABLE IF NOT EXISTS category_request_parents (
    category_request_id INTEGER NOT NULL,
    parent_category_id INTEGER NOT NULL,
    PRIMARY KEY (category_request_id, parent_category_id),
    CONSTRAINT fk_category_request_parents_request FOREIGN KEY (category_request_id) REFERENCES category_requests(id),
    CONSTRAINT fk_category_request_parents_category FOREIGN KEY (parent_category_id) REFERENCES categories(id)
);

-- Category History Table
CREATE TABLE IF NOT EXISTS category_history (
    id SERIAL PRIMARY KEY,
    category_id INTEGER NOT NULL,
    edited_by UUID NOT NULL,
    edited_at TIMESTAMP NOT NULL,
    old_values VARCHAR(5000),
    new_values VARCHAR(5000),
    comments VARCHAR(100),
    initial BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_category_history_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT fk_category_history_edited_by FOREIGN KEY (edited_by) REFERENCES users(id)
);

-- Algorithms Table
CREATE TABLE IF NOT EXISTS algorithms (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE,
    description VARCHAR(5000),
    type_id INTEGER NOT NULL,
    options VARCHAR(5000),
    options_description VARCHAR(5000),
    default_options VARCHAR(5000),
    class_name VARCHAR(255),
    CONSTRAINT fk_algorithms_type FOREIGN KEY (type_id) REFERENCES CONST_ALGORITHM_TYPES(id)
);

-- Custom Algorithms Table (with CASCADE DELETE)
CREATE TABLE IF NOT EXISTS custom_algorithms (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    accessibility_id INTEGER,
    created_at TIMESTAMP NOT NULL,
    owner_id UUID NOT NULL,
    CONSTRAINT fk_custom_algorithms_accessibility FOREIGN KEY (accessibility_id) REFERENCES CONST_ALGORITHM_ACCESSIBILITIES(id),
    CONSTRAINT fk_custom_algorithms_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Custom Algorithm Keywords Table (Element Collection)
CREATE TABLE IF NOT EXISTS custom_algorithm_keywords (
    algorithm_id INTEGER NOT NULL,
    keyword VARCHAR(255),
    CONSTRAINT fk_custom_algorithm_keywords_algorithm FOREIGN KEY (algorithm_id) REFERENCES custom_algorithms(id)
);

-- Custom Algorithm Images Table
CREATE TABLE IF NOT EXISTS custom_algorithm_images (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    custom_algorithm_id INTEGER NOT NULL,
    docker_hub_url VARCHAR(255),
    docker_tar_key VARCHAR(255),
    uploaded_at TIMESTAMP WITH TIME ZONE,
    version VARCHAR(255),
    is_active BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_custom_algorithm_images_algorithm FOREIGN KEY (custom_algorithm_id) REFERENCES custom_algorithms(id)
);

-- Algorithm Configurations Table (with CASCADE DELETE)
CREATE TABLE IF NOT EXISTS algorithm_configurations (
    id SERIAL PRIMARY KEY,
    algorithm_id INTEGER,
    options VARCHAR(255),
    algorithm_type_id INTEGER,
    user_id UUID NOT NULL,
    CONSTRAINT fk_algorithm_configurations_algorithm FOREIGN KEY (algorithm_id) REFERENCES algorithms(id),
    CONSTRAINT fk_algorithm_configurations_type FOREIGN KEY (algorithm_type_id) REFERENCES CONST_ALGORITHM_TYPES(id),
    CONSTRAINT fk_algorithm_configurations_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Custom Algorithm Configurations Table (with CASCADE DELETE and user_id)
CREATE TABLE IF NOT EXISTS custom_algorithm_configurations (
    id SERIAL PRIMARY KEY,
    algorithm_id INTEGER,
    user_id UUID NOT NULL,
    CONSTRAINT fk_custom_algorithm_configurations_algorithm FOREIGN KEY (algorithm_id) REFERENCES custom_algorithms(id) ON DELETE CASCADE,
    CONSTRAINT fk_custom_algorithm_configurations_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Algorithm Parameters Table
CREATE TABLE IF NOT EXISTS algorithm_parameters (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    value VARCHAR(255),
    description VARCHAR(200),
    range VARCHAR(255),
    algorithm_id INTEGER,
    configuration_id INTEGER,
    CONSTRAINT fk_algorithm_parameters_algorithm FOREIGN KEY (algorithm_id) REFERENCES custom_algorithms(id),
    CONSTRAINT fk_algorithm_parameters_configuration FOREIGN KEY (configuration_id) REFERENCES custom_algorithm_configurations(id)
);

-- Datasets Table (with CASCADE DELETE)
CREATE TABLE IF NOT EXISTS datasets (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL UNIQUE,
    file_path VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    upload_date TIMESTAMP WITH TIME ZONE NOT NULL,
    accessibility_id INTEGER NOT NULL,
    category_id INTEGER,
    description TEXT,
    CONSTRAINT fk_datasets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_datasets_accessibility FOREIGN KEY (accessibility_id) REFERENCES CONST_DATASET_ACCESSIBILITIES(id),
    CONSTRAINT fk_datasets_category FOREIGN KEY (category_id) REFERENCES categories(id)
);

-- Dataset Configurations Table
CREATE TABLE IF NOT EXISTS dataset_configurations (
    id SERIAL PRIMARY KEY,
    basic_attributes_columns VARCHAR(255),
    target_column VARCHAR(255),
    upload_date TIMESTAMP WITH TIME ZONE,
    status VARCHAR(255),
    dataset_id INTEGER,
    CONSTRAINT fk_dataset_configurations_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id)
);

-- Dataset Shares Table
CREATE TABLE IF NOT EXISTS dataset_shares (
    id SERIAL PRIMARY KEY,
    dataset_id INTEGER NOT NULL,
    shared_with_user_id UUID NOT NULL,
    shared_by_user_id UUID NOT NULL,
    shared_at TIMESTAMP WITH TIME ZONE NOT NULL,
    comment TEXT,
    CONSTRAINT fk_dataset_shares_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id),
    CONSTRAINT fk_dataset_shares_shared_with FOREIGN KEY (shared_with_user_id) REFERENCES users(id),
    CONSTRAINT fk_dataset_shares_shared_by FOREIGN KEY (shared_by_user_id) REFERENCES users(id),
    CONSTRAINT uk_dataset_shares UNIQUE (dataset_id, shared_with_user_id)
);

-- Dataset Share History Table
CREATE TABLE IF NOT EXISTS dataset_share_history (
    id SERIAL PRIMARY KEY,
    dataset_id INTEGER NOT NULL,
    target_user_id UUID NOT NULL,
    action_by_user_id UUID NOT NULL,
    action_at TIMESTAMP WITH TIME ZONE,
    action_type INTEGER,
    comment TEXT,
    CONSTRAINT fk_dataset_share_history_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id),
    CONSTRAINT fk_dataset_share_history_target_user FOREIGN KEY (target_user_id) REFERENCES users(id),
    CONSTRAINT fk_dataset_share_history_action_by FOREIGN KEY (action_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_dataset_share_history_action_type FOREIGN KEY (action_type) REFERENCES CONST_DATASET_SHARE_ACTION_TYPES(id)
);

-- Dataset Copies Table
CREATE TABLE IF NOT EXISTS dataset_copies (
    id SERIAL PRIMARY KEY,
    original_dataset_id INTEGER NOT NULL,
    copied_by_user_id UUID NOT NULL,
    copy_operated_by_user_id UUID NOT NULL,
    copy_date TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_dataset_copies_original FOREIGN KEY (original_dataset_id) REFERENCES datasets(id),
    CONSTRAINT fk_dataset_copies_copied_by FOREIGN KEY (copied_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_dataset_copies_operated_by FOREIGN KEY (copy_operated_by_user_id) REFERENCES users(id)
);

-- Trainings Table (with CASCADE DELETE)
CREATE TABLE IF NOT EXISTS trainings (
    id SERIAL PRIMARY KEY,
    started_date TIMESTAMP WITH TIME ZONE,
    finished_date TIMESTAMP WITH TIME ZONE,
    status_id INTEGER NOT NULL,
    algorithm_configuration_id INTEGER,
    custom_algorithm_configuration_id INTEGER,
    user_id UUID NOT NULL,
    dataset_id INTEGER NOT NULL,
    version INTEGER,
    results VARCHAR(3000),
    retrained_from INTEGER,
    CONSTRAINT fk_trainings_status FOREIGN KEY (status_id) REFERENCES CONST_TRAINING_STATUSES(id),
    CONSTRAINT fk_trainings_algorithm_config FOREIGN KEY (algorithm_configuration_id) REFERENCES algorithm_configurations(id),
    CONSTRAINT fk_trainings_custom_algorithm_config FOREIGN KEY (custom_algorithm_configuration_id) REFERENCES custom_algorithm_configurations(id),
    CONSTRAINT fk_trainings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_trainings_dataset_config FOREIGN KEY (dataset_id) REFERENCES dataset_configurations(id),
    CONSTRAINT fk_trainings_retrained_from FOREIGN KEY (retrained_from) REFERENCES trainings(id)
);

-- Models Table (with CASCADE DELETE on training)
CREATE TABLE IF NOT EXISTS models (
    id SERIAL PRIMARY KEY,
    training_id INTEGER NOT NULL UNIQUE,
    model_url VARCHAR(1000),
    model_type_id INTEGER NOT NULL,
    status_id INTEGER,
    version INTEGER,
    accessibility_id INTEGER,
    model_name VARCHAR(255),
    model_description VARCHAR(500),
    data_description VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE,
    finalized BOOLEAN DEFAULT FALSE,
    finalization_date TIMESTAMP WITH TIME ZONE,
    category_id INTEGER NOT NULL,
    metrics_url VARCHAR(255),
    label_mapping_url VARCHAR(1000),
    feature_columns_url VARCHAR(1000),
    CONSTRAINT fk_models_training FOREIGN KEY (training_id) REFERENCES trainings(id) ON DELETE CASCADE,
    CONSTRAINT fk_models_type FOREIGN KEY (model_type_id) REFERENCES CONST_MODEL_TYPES(id),
    CONSTRAINT fk_models_status FOREIGN KEY (status_id) REFERENCES CONST_MODEL_STATUSES(id),
    CONSTRAINT fk_models_accessibility FOREIGN KEY (accessibility_id) REFERENCES CONST_MODEL_ACCESSIBILITES(id),
    CONSTRAINT fk_models_category FOREIGN KEY (category_id) REFERENCES categories(id)
);

-- Model Keywords Table (Element Collection per V4)
CREATE TABLE IF NOT EXISTS model_keywords (
    model_id INTEGER NOT NULL,
    keyword VARCHAR(255),
    CONSTRAINT fk_model_keywords_model FOREIGN KEY (model_id) REFERENCES models(id) ON DELETE CASCADE
);

-- Model Executions Table (with CASCADE DELETE and accessibility)
CREATE TABLE IF NOT EXISTS models_executions (
    id SERIAL PRIMARY KEY,
    model_id INTEGER,
    executed_at TIMESTAMP WITH TIME ZONE,
    status_id INTEGER,
    prediction_result VARCHAR(5000),
    dataset_id INTEGER,
    executed_by_user_id UUID NOT NULL,
    accessibility_id INTEGER,
    CONSTRAINT fk_model_executions_model FOREIGN KEY (model_id) REFERENCES models(id),
    CONSTRAINT fk_model_executions_status FOREIGN KEY (status_id) REFERENCES CONST_MODEL_EXEC_STATUSES(id),
    CONSTRAINT fk_model_executions_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id),
    CONSTRAINT fk_model_executions_user FOREIGN KEY (executed_by_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_model_executions_accessibility FOREIGN KEY (accessibility_id) REFERENCES CONST_MODEL_EXECUTION_ACCESSIBILITIES(id)
);

-- Model Shares Table
CREATE TABLE IF NOT EXISTS model_shares (
    id SERIAL PRIMARY KEY,
    model_id INTEGER NOT NULL,
    shared_with_user_id UUID NOT NULL,
    shared_by_user_id UUID NOT NULL,
    shared_at TIMESTAMP WITH TIME ZONE NOT NULL,
    comment TEXT,
    CONSTRAINT fk_model_shares_model FOREIGN KEY (model_id) REFERENCES models(id),
    CONSTRAINT fk_model_shares_shared_with FOREIGN KEY (shared_with_user_id) REFERENCES users(id),
    CONSTRAINT fk_model_shares_shared_by FOREIGN KEY (shared_by_user_id) REFERENCES users(id),
    CONSTRAINT uk_model_shares UNIQUE (model_id, shared_with_user_id)
);

-- Model Share History Table
CREATE TABLE IF NOT EXISTS model_share_history (
    id BIGSERIAL PRIMARY KEY,
    model_id INTEGER NOT NULL,
    shared_with_user_id UUID NOT NULL,
    action_performed_by UUID NOT NULL,
    action INTEGER NOT NULL,
    action_time TIMESTAMP WITH TIME ZONE NOT NULL,
    comment TEXT,
    CONSTRAINT fk_model_share_history_model FOREIGN KEY (model_id) REFERENCES models(id),
    CONSTRAINT fk_model_share_history_shared_with FOREIGN KEY (shared_with_user_id) REFERENCES users(id),
    CONSTRAINT fk_model_share_history_performed_by FOREIGN KEY (action_performed_by) REFERENCES users(id),
    CONSTRAINT fk_model_share_history_action FOREIGN KEY (action) REFERENCES CONST_MODEL_SHARE_ACTION_TYPES(id)
);

-- Async Task Status Table
CREATE TABLE IF NOT EXISTS async_task_status (
    task_id VARCHAR(255) PRIMARY KEY,
    task_type VARCHAR(255),
    status VARCHAR(255),
    error_message TEXT,
    version INTEGER,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    username VARCHAR(255),
    model_id INTEGER,
    training_id INTEGER,
    execution_id INTEGER,
    stop_requested BOOLEAN NOT NULL DEFAULT FALSE,
    job_name VARCHAR(255)
);

-- =====================================================
-- SEQUENCES FOR JPA GenerationType.AUTO
-- =====================================================
CREATE SEQUENCE IF NOT EXISTS datasets_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS dataset_copies_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS dataset_shares_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS dataset_share_history_seq START WITH 1 INCREMENT BY 50;

-- Sync sequences with existing data (if any)
SELECT setval('datasets_seq', COALESCE((SELECT MAX(id) FROM datasets), 0) + 1, false);
SELECT setval('dataset_copies_seq', COALESCE((SELECT MAX(id) FROM dataset_copies), 0) + 1, false);
SELECT setval('dataset_shares_seq', COALESCE((SELECT MAX(id) FROM dataset_shares), 0) + 1, false);
SELECT setval('dataset_share_history_seq', COALESCE((SELECT MAX(id) FROM dataset_share_history), 0) + 1, false);

-- Fix all SERIAL-created sequences (from V9)
-- These sequences are automatically created by PostgreSQL when using SERIAL PRIMARY KEY
SELECT setval('datasets_id_seq', COALESCE((SELECT MAX(id) FROM datasets), 1), true);
SELECT setval('trainings_id_seq', COALESCE((SELECT MAX(id) FROM trainings), 1), true);
SELECT setval('models_id_seq', COALESCE((SELECT MAX(id) FROM models), 1), true);
SELECT setval('categories_id_seq', COALESCE((SELECT MAX(id) FROM categories), 1), true);
SELECT setval('algorithm_configurations_id_seq', COALESCE((SELECT MAX(id) FROM algorithm_configurations), 1), true);
SELECT setval('dataset_configurations_id_seq', COALESCE((SELECT MAX(id) FROM dataset_configurations), 1), true);
SELECT setval('custom_algorithm_configurations_id_seq', COALESCE((SELECT MAX(id) FROM custom_algorithm_configurations), 1), true);

-- =====================================================
-- INDEXES
-- =====================================================

-- Users indexes
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status_id);

-- Categories indexes
CREATE INDEX IF NOT EXISTS idx_categories_name ON categories(name);
CREATE INDEX IF NOT EXISTS idx_categories_created_by ON categories(created_by);
CREATE INDEX IF NOT EXISTS idx_categories_deleted ON categories(deleted);

-- Category Requests indexes
CREATE INDEX IF NOT EXISTS idx_category_requests_status ON category_requests(status_id);
CREATE INDEX IF NOT EXISTS idx_category_requests_requested_by ON category_requests(requested_by);

-- Algorithms indexes
CREATE INDEX IF NOT EXISTS idx_algorithms_type ON algorithms(type_id);
CREATE INDEX IF NOT EXISTS idx_algorithms_name ON algorithms(name);

-- Custom Algorithms indexes
CREATE INDEX IF NOT EXISTS idx_custom_algorithms_owner ON custom_algorithms(owner_id);
CREATE INDEX IF NOT EXISTS idx_custom_algorithms_accessibility ON custom_algorithms(accessibility_id);

-- Datasets indexes
CREATE INDEX IF NOT EXISTS idx_datasets_user ON datasets(user_id);
CREATE INDEX IF NOT EXISTS idx_datasets_category ON datasets(category_id);
CREATE INDEX IF NOT EXISTS idx_datasets_accessibility ON datasets(accessibility_id);
CREATE INDEX IF NOT EXISTS idx_datasets_file_name ON datasets(file_name);

-- Trainings indexes
CREATE INDEX IF NOT EXISTS idx_trainings_user ON trainings(user_id);
CREATE INDEX IF NOT EXISTS idx_trainings_status ON trainings(status_id);
CREATE INDEX IF NOT EXISTS idx_trainings_algorithm_config ON trainings(algorithm_configuration_id);
CREATE INDEX IF NOT EXISTS idx_trainings_custom_algorithm_config ON trainings(custom_algorithm_configuration_id);
CREATE INDEX IF NOT EXISTS idx_trainings_dataset_config ON trainings(dataset_id);

-- Models indexes
CREATE INDEX IF NOT EXISTS idx_models_training ON models(training_id);
CREATE INDEX IF NOT EXISTS idx_models_status ON models(status_id);
CREATE INDEX IF NOT EXISTS idx_models_category ON models(category_id);
CREATE INDEX IF NOT EXISTS idx_models_accessibility ON models(accessibility_id);
CREATE INDEX IF NOT EXISTS idx_models_finalized ON models(finalized);

-- Model Executions indexes
CREATE INDEX IF NOT EXISTS idx_model_executions_model ON models_executions(model_id);
CREATE INDEX IF NOT EXISTS idx_model_executions_user ON models_executions(executed_by_user_id);
CREATE INDEX IF NOT EXISTS idx_model_executions_status ON models_executions(status_id);
CREATE INDEX IF NOT EXISTS idx_model_executions_accessibility ON models_executions(accessibility_id);

-- Model Keywords indexes
CREATE INDEX IF NOT EXISTS idx_model_keywords_model_id ON model_keywords(model_id);
CREATE INDEX IF NOT EXISTS idx_model_keywords_keyword ON model_keywords(keyword);

-- JWT Tokens indexes
CREATE INDEX IF NOT EXISTS idx_jwt_tokens_user ON jwt_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_jwt_tokens_token ON jwt_tokens(token);
CREATE INDEX IF NOT EXISTS idx_jwt_tokens_revoked ON jwt_tokens(revoked);

-- Async Task Status indexes
CREATE INDEX IF NOT EXISTS idx_async_task_status_username ON async_task_status(username);
CREATE INDEX IF NOT EXISTS idx_async_task_status_status ON async_task_status(status);
CREATE INDEX IF NOT EXISTS idx_async_task_status_training ON async_task_status(training_id);
CREATE INDEX IF NOT EXISTS idx_async_task_status_model ON async_task_status(model_id);

-- =====================================================
-- END OF SCHEMA CREATION
-- =====================================================
