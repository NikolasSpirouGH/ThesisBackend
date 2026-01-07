-- ====================================================
-- V2: Initialize Reference/Lookup Data (Consolidated)
-- Purpose: Set up all CONST tables and system users
-- ====================================================

-- ============================================
-- 1. USER STATUSES
-- ============================================
INSERT INTO const_user_statuses (name, description) VALUES
    ('ACTIVE', 'User account is active'),
    ('INACTIVE', 'User account is inactive'),
    ('BANNED', 'User account is banned')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 2. USER ROLES
-- ============================================
INSERT INTO roles (name, description) VALUES
    ('USER', 'Regular user role'),
    ('ADMIN', 'Administrator role'),
    ('GROUP_LEADER', 'Group leader role'),
    ('GROUP_MEMBER', 'Group member role'),
    ('DATASET_MANAGER', 'Dataset manager role'),
    ('ALGORITHM_MANAGER', 'Algorithm manager role'),
    ('CATEGORY_MANAGER', 'Category manager role'),
    ('TRAINING_MODEL_MANAGER', 'Training model manager role')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 3. TRAINING STATUSES
-- ============================================
INSERT INTO const_training_statuses (name, description) VALUES
    ('REQUESTED', 'Training has been requested'),
    ('RUNNING', 'Training is in progress'),
    ('COMPLETED', 'Training completed successfully'),
    ('FAILED', 'Training failed')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 4. MODEL STATUSES
-- ============================================
INSERT INTO const_model_statuses (name, description) VALUES
    ('REQUESTED', 'Model is requested'),
    ('FINISHED', 'Model is finished'),
    ('IN_PROGRESS', 'Model is in progress'),
    ('FAILED', 'Model is failed'),
    ('CANCELED', 'Model has been canceled')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 5. DATASET ACCESSIBILITY
-- ============================================
INSERT INTO const_dataset_accessibilities (name, description) VALUES
    ('PUBLIC', 'Dataset is publicly accessible'),
    ('PRIVATE', 'Dataset is private'),
    ('RESTRICTED', 'Dataset has restricted access')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 6. MODEL ACCESSIBILITY
-- ============================================
INSERT INTO const_model_accessibilites (name, description) VALUES
    ('PUBLIC', 'Model is publicly accessible'),
    ('PRIVATE', 'Model is private'),
    ('RESTRICTED', 'Model has restricted access')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 7. MODEL EXECUTION ACCESSIBILITY
-- ============================================
INSERT INTO const_model_execution_accessibilities (name, description) VALUES
    ('PUBLIC', 'Execution results are publicly accessible'),
    ('PRIVATE', 'Execution results are private'),
    ('RESTRICTED', 'Execution results have restricted access')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 8. ALGORITHM ACCESSIBILITY
-- ============================================
INSERT INTO const_algorithm_accessibilities (name, description) VALUES
    ('PUBLIC', 'Algorithm is publicly accessible'),
    ('PRIVATE', 'Algorithm is private')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 9. ALGORITHM TYPES
-- ============================================
INSERT INTO const_algorithm_types (name) VALUES
    ('CLASSIFICATION'),
    ('CLUSTERING'),
    ('REGRESSION')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 10. MODEL TYPES
-- ============================================
INSERT INTO const_model_types (name) VALUES
    ('CUSTOM'),
    ('PREDEFINED')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 11. MODEL EXECUTION STATUSES
-- ============================================
INSERT INTO const_model_exec_statuses (name, description) VALUES
    ('PENDING', 'Execution is pending'),
    ('RUNNING', 'Execution is in progress'),
    ('COMPLETED', 'Execution completed successfully'),
    ('FAILED', 'Execution failed')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 12. CATEGORY REQUEST STATUSES
-- ============================================
INSERT INTO const_category_request_statuses (name, description) VALUES
    ('PENDING', 'The request is Pending'),
    ('APPROVED', 'The request is Approved'),
    ('REJECTED', 'The request is Rejected')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 13. DATASET SHARE ACTION TYPES
-- ============================================
INSERT INTO const_dataset_share_action_types (name, description) VALUES
    ('SHARED', 'Dataset was shared'),
    ('UNSHARED', 'Dataset sharing was revoked')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 14. MODEL SHARE ACTION TYPES
-- ============================================
INSERT INTO const_model_share_action_types (name, description) VALUES
    ('SHARED', 'Model was shared'),
    ('UNSHARED', 'Model sharing was revoked')
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 15. SYSTEM ADMIN USERS
-- ============================================
-- Passwords:
--   bigspy & johnken: "adminPassword"
--   nickriz: "userPassword"

INSERT INTO users (id, username, first_name, last_name, email, password, age, profession, country, status_id)
VALUES
    -- Admin: bigspy (adminPassword)
    (gen_random_uuid(), 'bigspy', 'Nikolas', 'Spirou', 'nikolas@gmail.com',
     '$argon2id$v=19$m=16384,t=2,p=1$ueeWp8dM+qkXeygJ01I2Hw$BaT7xKcCBmXNE0j8UqIwpIkgwwCiIGL7mc33FEcA2B0',
     27, 'Senior SWE', 'Greece',
     (SELECT id FROM const_user_statuses WHERE name = 'ACTIVE' LIMIT 1)),

    -- User: nickriz (userPassword)
    (gen_random_uuid(), 'nickriz', 'Nikos', 'Rizogiannis', 'rizo@gmail.com',
     '$argon2id$v=19$m=16384,t=2,p=1$L0EFr1Wd2P6HbXwEKqSgdw$ZiO4DjoyAKI8sHY+QcWLEpMjLylVDvlDtAcs+Hk0qd0',
     27, 'Senior SWE', 'Greece',
     (SELECT id FROM const_user_statuses WHERE name = 'ACTIVE' LIMIT 1)),

    -- Admin: johnken (adminPassword)
    (gen_random_uuid(), 'johnken', 'John', 'Kennedy', 'john@gmail.com',
     '$argon2id$v=19$m=16384,t=2,p=1$ueeWp8dM+qkXeygJ01I2Hw$BaT7xKcCBmXNE0j8UqIwpIkgwwCiIGL7mc33FEcA2B0',
     27, 'Senior SWE', 'Greece',
     (SELECT id FROM const_user_statuses WHERE name = 'ACTIVE' LIMIT 1))
ON CONFLICT (email) DO NOTHING;

-- Assign roles to users
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'bigspy' AND r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'nickriz' AND r.name = 'USER'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'johnken' AND r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- ============================================
-- 16. DEFAULT CATEGORY
-- ============================================
INSERT INTO categories (id, name, description, created_by, deleted)
VALUES (
    1,
    'Default',
    'Fallback category for datasets.',
    (SELECT id FROM users WHERE username = 'bigspy' LIMIT 1),
    false
)
ON CONFLICT (id) DO NOTHING;

-- Reset the sequence to avoid conflicts with future inserts
SELECT setval('categories_id_seq', (SELECT MAX(id) FROM categories));

-- ====================================================
-- END OF REFERENCE DATA
-- ====================================================
