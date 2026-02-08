-- =====================================================
-- V5: Demo Data for Groups and Pipeline Copy Features
-- =====================================================
-- This migration adds sample data for demonstration and testing
-- of the new Groups and Pipeline Copy functionality.
--
-- Existing users (from V2):
--   bigspy (Admin) - password: adminPassword
--   nickriz (User) - password: userPassword
--   johnken (Admin) - password: adminPassword
-- =====================================================

-- ============================================
-- 1. CREATE ADDITIONAL TEST USERS
-- ============================================
-- Password for all new users: "testPassword"
-- Argon2 hash for "testPassword": $argon2id$v=19$m=16384,t=2,p=1$L0EFr1Wd2P6HbXwEKqSgdw$ZiO4DjoyAKI8sHY+QcWLEpMjLylVDvlDtAcs+Hk0qd0

INSERT INTO users (id, username, first_name, last_name, email, password, age, profession, country, status_id)
VALUES
    -- Research Team Members
    (gen_random_uuid(), 'maria_ds', 'Maria', 'DataScientist', 'maria@research.com',
     '$argon2id$v=19$m=16384,t=2,p=1$L0EFr1Wd2P6HbXwEKqSgdw$ZiO4DjoyAKI8sHY+QcWLEpMjLylVDvlDtAcs+Hk0qd0',
     28, 'Data Scientist', 'Greece',
     (SELECT id FROM const_user_statuses WHERE name = 'ACTIVE' LIMIT 1)),

    (gen_random_uuid(), 'alex_ml', 'Alex', 'MLEngineer', 'alex@research.com',
     '$argon2id$v=19$m=16384,t=2,p=1$L0EFr1Wd2P6HbXwEKqSgdw$ZiO4DjoyAKI8sHY+QcWLEpMjLylVDvlDtAcs+Hk0qd0',
     30, 'ML Engineer', 'Greece',
     (SELECT id FROM const_user_statuses WHERE name = 'ACTIVE' LIMIT 1)),

    (gen_random_uuid(), 'elena_ai', 'Elena', 'AIResearcher', 'elena@research.com',
     '$argon2id$v=19$m=16384,t=2,p=1$L0EFr1Wd2P6HbXwEKqSgdw$ZiO4DjoyAKI8sHY+QcWLEpMjLylVDvlDtAcs+Hk0qd0',
     26, 'AI Researcher', 'Greece',
     (SELECT id FROM const_user_statuses WHERE name = 'ACTIVE' LIMIT 1)),

    -- Development Team Members
    (gen_random_uuid(), 'george_dev', 'George', 'Developer', 'george@dev.com',
     '$argon2id$v=19$m=16384,t=2,p=1$L0EFr1Wd2P6HbXwEKqSgdw$ZiO4DjoyAKI8sHY+QcWLEpMjLylVDvlDtAcs+Hk0qd0',
     32, 'Software Developer', 'Greece',
     (SELECT id FROM const_user_statuses WHERE name = 'ACTIVE' LIMIT 1)),

    (gen_random_uuid(), 'sophia_fe', 'Sophia', 'Frontend', 'sophia@dev.com',
     '$argon2id$v=19$m=16384,t=2,p=1$L0EFr1Wd2P6HbXwEKqSgdw$ZiO4DjoyAKI8sHY+QcWLEpMjLylVDvlDtAcs+Hk0qd0',
     25, 'Frontend Developer', 'Greece',
     (SELECT id FROM const_user_statuses WHERE name = 'ACTIVE' LIMIT 1))
ON CONFLICT (email) DO NOTHING;

-- Assign USER role to new users
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username IN ('maria_ds', 'alex_ml', 'elena_ai', 'george_dev', 'sophia_fe')
  AND r.name = 'USER'
ON CONFLICT DO NOTHING;

-- ============================================
-- 2. CREATE DEMO GROUPS
-- ============================================

-- ML Research Team (led by nickriz)
INSERT INTO groups (id, name, description, leader_id, created_at)
SELECT 1, 'ML Research Team', 'Team focused on machine learning research and experiments',
       (SELECT id FROM users WHERE username = 'nickriz'), NOW()
WHERE NOT EXISTS (SELECT 1 FROM groups WHERE id = 1);

-- AI Development Team (led by bigspy)
INSERT INTO groups (id, name, description, leader_id, created_at)
SELECT 2, 'AI Development Team', 'Development team working on AI-powered applications',
       (SELECT id FROM users WHERE username = 'bigspy'), NOW()
WHERE NOT EXISTS (SELECT 1 FROM groups WHERE id = 2);

-- Data Science Lab (led by maria_ds)
INSERT INTO groups (id, name, description, leader_id, created_at)
SELECT 3, 'Data Science Lab', 'Collaborative space for data science projects',
       (SELECT id FROM users WHERE username = 'maria_ds'), NOW()
WHERE NOT EXISTS (SELECT 1 FROM groups WHERE id = 3);

-- Reset sequence
SELECT setval('groups_id_seq', (SELECT COALESCE(MAX(id), 1) FROM groups));

-- ============================================
-- 3. ADD MEMBERS TO GROUPS
-- ============================================

-- ML Research Team members: maria_ds, alex_ml, elena_ai
INSERT INTO group_members (group_id, user_id, joined_at)
SELECT 1, (SELECT id FROM users WHERE username = 'maria_ds'), NOW()
WHERE NOT EXISTS (SELECT 1 FROM group_members WHERE group_id = 1 AND user_id = (SELECT id FROM users WHERE username = 'maria_ds'));

INSERT INTO group_members (group_id, user_id, joined_at)
SELECT 1, (SELECT id FROM users WHERE username = 'alex_ml'), NOW()
WHERE NOT EXISTS (SELECT 1 FROM group_members WHERE group_id = 1 AND user_id = (SELECT id FROM users WHERE username = 'alex_ml'));

INSERT INTO group_members (group_id, user_id, joined_at)
SELECT 1, (SELECT id FROM users WHERE username = 'elena_ai'), NOW()
WHERE NOT EXISTS (SELECT 1 FROM group_members WHERE group_id = 1 AND user_id = (SELECT id FROM users WHERE username = 'elena_ai'));

-- AI Development Team members: nickriz, george_dev, sophia_fe
INSERT INTO group_members (group_id, user_id, joined_at)
SELECT 2, (SELECT id FROM users WHERE username = 'nickriz'), NOW()
WHERE NOT EXISTS (SELECT 1 FROM group_members WHERE group_id = 2 AND user_id = (SELECT id FROM users WHERE username = 'nickriz'));

INSERT INTO group_members (group_id, user_id, joined_at)
SELECT 2, (SELECT id FROM users WHERE username = 'george_dev'), NOW()
WHERE NOT EXISTS (SELECT 1 FROM group_members WHERE group_id = 2 AND user_id = (SELECT id FROM users WHERE username = 'george_dev'));

INSERT INTO group_members (group_id, user_id, joined_at)
SELECT 2, (SELECT id FROM users WHERE username = 'sophia_fe'), NOW()
WHERE NOT EXISTS (SELECT 1 FROM group_members WHERE group_id = 2 AND user_id = (SELECT id FROM users WHERE username = 'sophia_fe'));

-- Data Science Lab members: alex_ml, elena_ai, nickriz
INSERT INTO group_members (group_id, user_id, joined_at)
SELECT 3, (SELECT id FROM users WHERE username = 'alex_ml'), NOW()
WHERE NOT EXISTS (SELECT 1 FROM group_members WHERE group_id = 3 AND user_id = (SELECT id FROM users WHERE username = 'alex_ml'));

INSERT INTO group_members (group_id, user_id, joined_at)
SELECT 3, (SELECT id FROM users WHERE username = 'elena_ai'), NOW()
WHERE NOT EXISTS (SELECT 1 FROM group_members WHERE group_id = 3 AND user_id = (SELECT id FROM users WHERE username = 'elena_ai'));

INSERT INTO group_members (group_id, user_id, joined_at)
SELECT 3, (SELECT id FROM users WHERE username = 'nickriz'), NOW()
WHERE NOT EXISTS (SELECT 1 FROM group_members WHERE group_id = 3 AND user_id = (SELECT id FROM users WHERE username = 'nickriz'));

-- ============================================
-- Summary of Demo Data
-- ============================================
-- USERS:
-- | Username    | Password       | Role  | Description              |
-- |-------------|----------------|-------|--------------------------|
-- | bigspy      | adminPassword  | ADMIN | Admin user               |
-- | johnken     | adminPassword  | ADMIN | Admin user               |
-- | nickriz     | userPassword   | USER  | Regular user             |
-- | maria_ds    | testPassword   | USER  | Data Scientist           |
-- | alex_ml     | testPassword   | USER  | ML Engineer              |
-- | elena_ai    | testPassword   | USER  | AI Researcher            |
-- | george_dev  | testPassword   | USER  | Software Developer       |
-- | sophia_fe   | testPassword   | USER  | Frontend Developer       |
--
-- GROUPS:
-- | ID | Name                | Leader   | Members                         |
-- |----|---------------------|----------|----------------------------------|
-- | 1  | ML Research Team    | nickriz  | maria_ds, alex_ml, elena_ai     |
-- | 2  | AI Development Team | bigspy   | nickriz, george_dev, sophia_fe  |
-- | 3  | Data Science Lab    | maria_ds | alex_ml, elena_ai, nickriz      |
-- ============================================
