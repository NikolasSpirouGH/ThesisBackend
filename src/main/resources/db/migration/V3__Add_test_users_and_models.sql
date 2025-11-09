-- ============================================
-- V3: Add Test Users and Models with Different Dates
-- ============================================

-- Add two more regular users
INSERT INTO users (id, username, first_name, last_name, email, password, age, profession, country, status_id)
VALUES
    (gen_random_uuid(), 'emma', 'Emma', 'Johnson', 'emma@test.com',
     '$argon2id$v=19$m=16384,t=2,p=1$ueeWp8dM+qkXeygJ01I2Hw$BaT7xKcCBmXNE0j8UqIwpIkgwwCiIGL7mc33FEcA2B0',
     28, 'Data Scientist', 'USA',
     (SELECT id FROM const_user_statuses WHERE name = 'ACTIVE' LIMIT 1)),
    (gen_random_uuid(), 'david', 'David', 'Smith', 'david@test.com',
     '$argon2id$v=19$m=16384,t=2,p=1$ueeWp8dM+qkXeygJ01I2Hw$BaT7xKcCBmXNE0j8UqIwpIkgwwCiIGL7mc33FEcA2B0',
     32, 'ML Engineer', 'Canada',
     (SELECT id FROM const_user_statuses WHERE name = 'ACTIVE' LIMIT 1))
ON CONFLICT (email) DO NOTHING;

-- Assign USER role to new users
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'emma' AND r.name = 'USER'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'david' AND r.name = 'USER'
ON CONFLICT DO NOTHING;

-- ============================================
-- Create Test Categories with Hierarchy
-- ============================================

-- Create parent categories
INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Machine Learning', 'General machine learning algorithms and models', u.id, false
FROM users u WHERE u.username = 'bigspy' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Computer Vision', 'Image and video processing models', u.id, false
FROM users u WHERE u.username = 'bigspy' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Natural Language Processing', 'Text analysis and language models', u.id, false
FROM users u WHERE u.username = 'bigspy' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Time Series Analysis', 'Temporal data and forecasting models', u.id, false
FROM users u WHERE u.username = 'bigspy' LIMIT 1
ON CONFLICT (name) DO NOTHING;

-- Create child categories for Machine Learning
INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Classification', 'Classification algorithms', u.id, false
FROM users u WHERE u.username = 'emma' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Regression', 'Regression algorithms', u.id, false
FROM users u WHERE u.username = 'emma' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Clustering', 'Clustering and unsupervised learning', u.id, false
FROM users u WHERE u.username = 'david' LIMIT 1
ON CONFLICT (name) DO NOTHING;

-- Create child categories for Computer Vision
INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Object Detection', 'Object detection and localization', u.id, false
FROM users u WHERE u.username = 'david' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Image Classification', 'Image categorization and recognition', u.id, false
FROM users u WHERE u.username = 'emma' LIMIT 1
ON CONFLICT (name) DO NOTHING;

-- Create child categories for NLP
INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Sentiment Analysis', 'Opinion mining and sentiment classification', u.id, false
FROM users u WHERE u.username = 'nickriz' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Text Classification', 'Document and text categorization', u.id, false
FROM users u WHERE u.username = 'nickriz' LIMIT 1
ON CONFLICT (name) DO NOTHING;

-- Create category hierarchy relationships
-- Machine Learning children
INSERT INTO category_hierarchy (parent_category_id, child_category_id)
SELECT p.id, c.id
FROM categories p, categories c
WHERE p.name = 'Machine Learning' AND c.name = 'Classification'
ON CONFLICT DO NOTHING;

INSERT INTO category_hierarchy (parent_category_id, child_category_id)
SELECT p.id, c.id
FROM categories p, categories c
WHERE p.name = 'Machine Learning' AND c.name = 'Regression'
ON CONFLICT DO NOTHING;

INSERT INTO category_hierarchy (parent_category_id, child_category_id)
SELECT p.id, c.id
FROM categories p, categories c
WHERE p.name = 'Machine Learning' AND c.name = 'Clustering'
ON CONFLICT DO NOTHING;

-- Computer Vision children
INSERT INTO category_hierarchy (parent_category_id, child_category_id)
SELECT p.id, c.id
FROM categories p, categories c
WHERE p.name = 'Computer Vision' AND c.name = 'Object Detection'
ON CONFLICT DO NOTHING;

INSERT INTO category_hierarchy (parent_category_id, child_category_id)
SELECT p.id, c.id
FROM categories p, categories c
WHERE p.name = 'Computer Vision' AND c.name = 'Image Classification'
ON CONFLICT DO NOTHING;

-- NLP children
INSERT INTO category_hierarchy (parent_category_id, child_category_id)
SELECT p.id, c.id
FROM categories p, categories c
WHERE p.name = 'Natural Language Processing' AND c.name = 'Sentiment Analysis'
ON CONFLICT DO NOTHING;

INSERT INTO category_hierarchy (parent_category_id, child_category_id)
SELECT p.id, c.id
FROM categories p, categories c
WHERE p.name = 'Natural Language Processing' AND c.name = 'Text Classification'
ON CONFLICT DO NOTHING;

-- ============================================
-- Create Test Trainings and Models
-- ============================================

-- Helper: Get IDs for reference
DO $$
DECLARE
    emma_id UUID;
    david_id UUID;
    bigspy_id UUID;
    nickriz_id UUID;
    dataset_id INT;
    algorithm_id INT;
    algorithm_type_id INT;
    model_type_id INT;
    model_status_id INT;
    model_accessibility_public_id INT;
    model_accessibility_private_id INT;
    category_text_classification_id INT;
    category_classification_id INT;
    category_sentiment_analysis_id INT;
    category_image_classification_id INT;
    training_status_completed_id INT;
    dataset_config_id INT;
    algo_config_id INT;
    training_id_1 INT;
    training_id_2 INT;
    training_id_3 INT;
    training_id_4 INT;
    training_id_5 INT;
BEGIN
    -- Get user IDs
    SELECT id INTO emma_id FROM users WHERE username = 'emma' LIMIT 1;
    SELECT id INTO david_id FROM users WHERE username = 'david' LIMIT 1;
    SELECT id INTO bigspy_id FROM users WHERE username = 'bigspy' LIMIT 1;
    SELECT id INTO nickriz_id FROM users WHERE username = 'nickriz' LIMIT 1;

    -- Get reference IDs (use first available)
    SELECT id INTO dataset_id FROM datasets LIMIT 1;
    SELECT id INTO algorithm_id FROM algorithms LIMIT 1;
    SELECT id INTO algorithm_type_id FROM const_algorithm_types LIMIT 1;
    SELECT id INTO model_type_id FROM const_model_types LIMIT 1;
    SELECT id INTO model_status_id FROM const_model_statuses WHERE name = 'FINISHED' LIMIT 1;
    SELECT id INTO model_accessibility_public_id FROM const_model_accessibilites WHERE name = 'PUBLIC' LIMIT 1;
    SELECT id INTO model_accessibility_private_id FROM const_model_accessibilites WHERE name = 'PRIVATE' LIMIT 1;
    SELECT id INTO category_text_classification_id FROM categories WHERE name = 'Text Classification' LIMIT 1;
    SELECT id INTO category_classification_id FROM categories WHERE name = 'Classification' LIMIT 1;
    SELECT id INTO category_sentiment_analysis_id FROM categories WHERE name = 'Sentiment Analysis' LIMIT 1;
    SELECT id INTO category_image_classification_id FROM categories WHERE name = 'Image Classification' LIMIT 1;
    SELECT id INTO training_status_completed_id FROM const_training_statuses WHERE name = 'COMPLETED' LIMIT 1;

    -- If no dataset or algorithm exists, skip model creation
    IF dataset_id IS NULL OR algorithm_id IS NULL THEN
        RAISE NOTICE 'No dataset or algorithm found, skipping model creation';
        RETURN;
    END IF;

    -- Create dataset configuration for all trainings (reusable)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_id, 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    -- Training 1: Emma - Spam Classification Model (Jan 2024)
    -- Create algorithm configuration for Emma
    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, emma_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (emma_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-01-10 09:00:00'::timestamp, '2024-01-10 10:30:00'::timestamp)
    RETURNING id INTO training_id_1;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id_1, model_type_id, model_status_id, model_accessibility_public_id,
            'Spam Classification Model',
            'Email spam detection using Naive Bayes classifier',
            'Trained on 10k emails dataset',
            '2024-01-10 10:30:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-01-15 14:00:00'::timestamp AT TIME ZONE 'UTC',
            category_text_classification_id, 0, 'models/spam-classifier-v1.model', 'metrics/spam-classifier-v1.json');

    -- Training 2: David - Customer Churn Prediction (March 2024)
    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, david_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (david_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-03-20 11:00:00'::timestamp, '2024-03-20 13:45:00'::timestamp)
    RETURNING id INTO training_id_2;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id_2, model_type_id, model_status_id, model_accessibility_private_id,
            'Customer Churn Prediction',
            'Predicts customer churn using Random Forest',
            'Historical customer data with churn labels',
            '2024-03-20 13:45:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-03-22 16:30:00'::timestamp AT TIME ZONE 'UTC',
            category_classification_id, 0, 'models/churn-predictor-v1.model', 'metrics/churn-predictor-v1.json');

    -- Training 3: Admin (bigspy) - Sentiment Analysis (June 2024)
    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, bigspy_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (bigspy_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-06-15 08:30:00'::timestamp, '2024-06-15 11:20:00'::timestamp)
    RETURNING id INTO training_id_3;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id_3, model_type_id, model_status_id, model_accessibility_public_id,
            'Sentiment Analysis Model',
            'Twitter sentiment classification with SVM',
            'Social media posts labeled with sentiment',
            '2024-06-15 11:20:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-06-20 09:00:00'::timestamp AT TIME ZONE 'UTC',
            category_sentiment_analysis_id, 0, 'models/sentiment-analysis-v1.model', 'metrics/sentiment-analysis-v1.json');

    -- Training 4: Emma - Image Classifier (September 2024)
    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, emma_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (emma_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-09-10 13:00:00'::timestamp, '2024-09-10 16:45:00'::timestamp)
    RETURNING id INTO training_id_4;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id_4, model_type_id, model_status_id, model_accessibility_public_id,
            'Image Classifier CNN',
            'Convolutional Neural Network for image classification',
            'CIFAR-10 image dataset with 10 classes',
            '2024-09-10 16:45:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-09-12 10:00:00'::timestamp AT TIME ZONE 'UTC',
            category_image_classification_id, 0, 'models/image-classifier-v1.model', 'metrics/image-classifier-v1.json');

    -- Training 5: nickriz - Fraud Detection (October 2024)
    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, nickriz_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (nickriz_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-10-20 10:00:00'::timestamp, '2024-10-20 14:30:00'::timestamp)
    RETURNING id INTO training_id_5;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id_5, model_type_id, model_status_id, model_accessibility_private_id,
            'Fraud Detection System',
            'Credit card fraud detection using ensemble methods',
            'Transaction data with fraud indicators',
            '2024-10-20 14:30:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-10-25 11:00:00'::timestamp AT TIME ZONE 'UTC',
            category_classification_id, 0, 'models/fraud-detector-v1.model', 'metrics/fraud-detector-v1.json');

END $$;
