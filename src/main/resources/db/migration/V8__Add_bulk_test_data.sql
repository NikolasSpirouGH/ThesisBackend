-- ============================================
-- V8: Add Bulk Test Data for Pagination Testing
-- Purpose: Add 20 datasets, 20 models/trainings, and additional categories
-- ============================================

-- ============================================
-- 1. Add More Categories
-- ============================================

-- Add parent categories
INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Deep Learning', 'Neural networks and deep learning architectures', u.id, false
FROM users u WHERE u.username = 'bigspy' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Reinforcement Learning', 'Agent-based learning and decision making', u.id, false
FROM users u WHERE u.username = 'bigspy' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Anomaly Detection', 'Outlier and anomaly detection methods', u.id, false
FROM users u WHERE u.username = 'emma' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Recommendation Systems', 'Collaborative filtering and content-based recommendations', u.id, false
FROM users u WHERE u.username = 'david' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Audio Processing', 'Speech recognition and audio analysis', u.id, false
FROM users u WHERE u.username = 'nickriz' LIMIT 1
ON CONFLICT (name) DO NOTHING;

-- Add child categories for Deep Learning
INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Convolutional Networks', 'CNN architectures for image processing', u.id, false
FROM users u WHERE u.username = 'emma' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Recurrent Networks', 'RNN and LSTM for sequential data', u.id, false
FROM users u WHERE u.username = 'david' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Transformers', 'Attention-based transformer models', u.id, false
FROM users u WHERE u.username = 'nickriz' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Generative Models', 'GANs and VAEs for generation tasks', u.id, false
FROM users u WHERE u.username = 'emma' LIMIT 1
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description, created_by, deleted)
SELECT 'Transfer Learning', 'Pre-trained models and fine-tuning', u.id, false
FROM users u WHERE u.username = 'david' LIMIT 1
ON CONFLICT (name) DO NOTHING;

-- Create category hierarchy for Deep Learning
INSERT INTO category_hierarchy (parent_category_id, child_category_id)
SELECT p.id, c.id
FROM categories p, categories c
WHERE p.name = 'Deep Learning' AND c.name = 'Convolutional Networks'
ON CONFLICT DO NOTHING;

INSERT INTO category_hierarchy (parent_category_id, child_category_id)
SELECT p.id, c.id
FROM categories p, categories c
WHERE p.name = 'Deep Learning' AND c.name = 'Recurrent Networks'
ON CONFLICT DO NOTHING;

INSERT INTO category_hierarchy (parent_category_id, child_category_id)
SELECT p.id, c.id
FROM categories p, categories c
WHERE p.name = 'Deep Learning' AND c.name = 'Transformers'
ON CONFLICT DO NOTHING;

INSERT INTO category_hierarchy (parent_category_id, child_category_id)
SELECT p.id, c.id
FROM categories p, categories c
WHERE p.name = 'Deep Learning' AND c.name = 'Generative Models'
ON CONFLICT DO NOTHING;

INSERT INTO category_hierarchy (parent_category_id, child_category_id)
SELECT p.id, c.id
FROM categories p, categories c
WHERE p.name = 'Deep Learning' AND c.name = 'Transfer Learning'
ON CONFLICT DO NOTHING;

-- ============================================
-- 2. Add 20 Datasets
-- ============================================

DO $$
DECLARE
    emma_id UUID;
    david_id UUID;
    bigspy_id UUID;
    nickriz_id UUID;
    public_access_id INT;
    private_access_id INT;
    restricted_access_id INT;
    category_ml_id INT;
    category_cv_id INT;
    category_nlp_id INT;
    category_dl_id INT;
BEGIN
    -- Get user IDs
    SELECT id INTO emma_id FROM users WHERE username = 'emma' LIMIT 1;
    SELECT id INTO david_id FROM users WHERE username = 'david' LIMIT 1;
    SELECT id INTO bigspy_id FROM users WHERE username = 'bigspy' LIMIT 1;
    SELECT id INTO nickriz_id FROM users WHERE username = 'nickriz' LIMIT 1;

    -- Get accessibility IDs
    SELECT id INTO public_access_id FROM const_dataset_accessibilities WHERE name = 'PUBLIC' LIMIT 1;
    SELECT id INTO private_access_id FROM const_dataset_accessibilities WHERE name = 'PRIVATE' LIMIT 1;
    SELECT id INTO restricted_access_id FROM const_dataset_accessibilities WHERE name = 'RESTRICTED' LIMIT 1;

    -- Get category IDs
    SELECT id INTO category_ml_id FROM categories WHERE name = 'Machine Learning' LIMIT 1;
    SELECT id INTO category_cv_id FROM categories WHERE name = 'Computer Vision' LIMIT 1;
    SELECT id INTO category_nlp_id FROM categories WHERE name = 'Natural Language Processing' LIMIT 1;
    SELECT id INTO category_dl_id FROM categories WHERE name = 'Deep Learning' LIMIT 1;

    -- Insert 20 datasets with varied metadata
    INSERT INTO datasets (user_id, original_file_name, file_name, file_path, file_size, content_type, upload_date, accessibility_id, category_id, description)
    VALUES
        -- Datasets 1-5 (Emma's datasets)
        (emma_id, 'iris_flowers.csv', 'iris_flowers_20240101.csv', 'datasets/iris_flowers_20240101.csv', 4608, 'text/csv', '2024-01-01 10:00:00', public_access_id, category_ml_id, 'Classic Iris flower dataset for classification'),
        (emma_id, 'customer_data.csv', 'customer_data_20240115.csv', 'datasets/customer_data_20240115.csv', 524288, 'text/csv', '2024-01-15 14:30:00', private_access_id, category_ml_id, 'Customer purchase history and demographics'),
        (emma_id, 'mnist_digits.csv', 'mnist_digits_20240201.csv', 'datasets/mnist_digits_20240201.csv', 11534336, 'text/csv', '2024-02-01 09:15:00', public_access_id, category_cv_id, 'Handwritten digits dataset for image classification'),
        (emma_id, 'product_reviews.csv', 'product_reviews_20240210.csv', 'datasets/product_reviews_20240210.csv', 2097152, 'text/csv', '2024-02-10 16:45:00', public_access_id, category_nlp_id, 'Amazon product reviews with ratings'),
        (emma_id, 'stock_prices.csv', 'stock_prices_20240301.csv', 'datasets/stock_prices_20240301.csv', 1048576, 'text/csv', '2024-03-01 08:00:00', private_access_id, category_ml_id, 'Historical stock market data for prediction'),

        -- Datasets 6-10 (David's datasets)
        (david_id, 'cifar10_images.tar.gz', 'cifar10_images_20240315.tar.gz', 'datasets/cifar10_images_20240315.tar.gz', 170498071, 'application/gzip', '2024-03-15 11:20:00', public_access_id, category_cv_id, 'CIFAR-10 image classification dataset'),
        (david_id, 'house_prices.csv', 'house_prices_20240320.csv', 'datasets/house_prices_20240320.csv', 458752, 'text/csv', '2024-03-20 13:30:00', public_access_id, category_ml_id, 'Boston housing prices for regression'),
        (david_id, 'medical_records.csv', 'medical_records_20240405.csv', 'datasets/medical_records_20240405.csv', 3145728, 'text/csv', '2024-04-05 10:00:00', restricted_access_id, category_ml_id, 'Medical patient records (anonymized)'),
        (david_id, 'twitter_sentiment.csv', 'twitter_sentiment_20240410.csv', 'datasets/twitter_sentiment_20240410.csv', 5242880, 'text/csv', '2024-04-10 15:45:00', public_access_id, category_nlp_id, 'Twitter posts labeled with sentiment'),
        (david_id, 'credit_default.csv', 'credit_default_20240501.csv', 'datasets/credit_default_20240501.csv', 921600, 'text/csv', '2024-05-01 09:30:00', private_access_id, category_ml_id, 'Credit card default prediction dataset'),

        -- Datasets 11-15 (bigspy's datasets)
        (bigspy_id, 'imagenet_subset.tar.gz', 'imagenet_subset_20240515.tar.gz', 'datasets/imagenet_subset_20240515.tar.gz', 536870912, 'application/gzip', '2024-05-15 12:00:00', public_access_id, category_cv_id, 'ImageNet subset for transfer learning'),
        (bigspy_id, 'wine_quality.csv', 'wine_quality_20240520.csv', 'datasets/wine_quality_20240520.csv', 327680, 'text/csv', '2024-05-20 14:15:00', public_access_id, category_ml_id, 'Wine quality ratings for classification'),
        (bigspy_id, 'news_articles.csv', 'news_articles_20240601.csv', 'datasets/news_articles_20240601.csv', 10485760, 'text/csv', '2024-06-01 10:45:00', public_access_id, category_nlp_id, 'News articles for topic classification'),
        (bigspy_id, 'sales_forecast.csv', 'sales_forecast_20240615.csv', 'datasets/sales_forecast_20240615.csv', 716800, 'text/csv', '2024-06-15 16:30:00', private_access_id, category_ml_id, 'Retail sales time series data'),
        (bigspy_id, 'face_recognition.zip', 'face_recognition_20240701.zip', 'datasets/face_recognition_20240701.zip', 104857600, 'application/zip', '2024-07-01 11:00:00', restricted_access_id, category_cv_id, 'Facial recognition training images'),

        -- Datasets 16-20 (nickriz's datasets)
        (nickriz_id, 'spam_emails.csv', 'spam_emails_20240715.csv', 'datasets/spam_emails_20240715.csv', 2621440, 'text/csv', '2024-07-15 09:45:00', public_access_id, category_nlp_id, 'Email spam detection dataset'),
        (nickriz_id, 'network_traffic.csv', 'network_traffic_20240801.csv', 'datasets/network_traffic_20240801.csv', 15728640, 'text/csv', '2024-08-01 13:20:00', private_access_id, category_ml_id, 'Network intrusion detection data'),
        (nickriz_id, 'fashion_mnist.csv', 'fashion_mnist_20240815.csv', 'datasets/fashion_mnist_20240815.csv', 9437184, 'text/csv', '2024-08-15 10:30:00', public_access_id, category_cv_id, 'Fashion MNIST clothing classification'),
        (nickriz_id, 'diabetes_prediction.csv', 'diabetes_prediction_20240901.csv', 'datasets/diabetes_prediction_20240901.csv', 98304, 'text/csv', '2024-09-01 14:00:00', public_access_id, category_ml_id, 'Diabetes prediction from medical features'),
        (nickriz_id, 'sentiment_140.csv', 'sentiment_140_20240915.csv', 'datasets/sentiment_140_20240915.csv', 238936064, 'text/csv', '2024-09-15 08:30:00', public_access_id, category_nlp_id, 'Sentiment140 Twitter dataset with 1.6M tweets');
END $$;

-- ============================================
-- 3. Add 20 Models with Trainings
-- ============================================

DO $$
DECLARE
    emma_id UUID;
    david_id UUID;
    bigspy_id UUID;
    nickriz_id UUID;
    dataset_ids INT[];
    algorithm_id INT;
    algorithm_type_id INT;
    model_type_id INT;
    model_status_finished_id INT;
    model_status_inprogress_id INT;
    model_accessibility_public_id INT;
    model_accessibility_private_id INT;
    category_classification_id INT;
    category_regression_id INT;
    category_clustering_id INT;
    category_text_classification_id INT;
    category_sentiment_analysis_id INT;
    category_image_classification_id INT;
    category_object_detection_id INT;
    category_cnn_id INT;
    category_rnn_id INT;
    category_transformer_id INT;
    training_status_completed_id INT;
    training_status_running_id INT;
    training_status_failed_id INT;
    dataset_config_id INT;
    algo_config_id INT;
    training_id INT;
    i INT;
BEGIN
    -- Get user IDs
    SELECT id INTO emma_id FROM users WHERE username = 'emma' LIMIT 1;
    SELECT id INTO david_id FROM users WHERE username = 'david' LIMIT 1;
    SELECT id INTO bigspy_id FROM users WHERE username = 'bigspy' LIMIT 1;
    SELECT id INTO nickriz_id FROM users WHERE username = 'nickriz' LIMIT 1;

    -- Get dataset IDs (grab first 20 datasets)
    SELECT ARRAY_AGG(id ORDER BY id) INTO dataset_ids FROM datasets LIMIT 20;

    -- Get reference IDs
    SELECT id INTO algorithm_id FROM algorithms LIMIT 1;
    SELECT id INTO algorithm_type_id FROM const_algorithm_types LIMIT 1;
    SELECT id INTO model_type_id FROM const_model_types WHERE name = 'CUSTOM' LIMIT 1;
    SELECT id INTO model_status_finished_id FROM const_model_statuses WHERE name = 'FINISHED' LIMIT 1;
    SELECT id INTO model_status_inprogress_id FROM const_model_statuses WHERE name = 'IN_PROGRESS' LIMIT 1;
    SELECT id INTO model_accessibility_public_id FROM const_model_accessibilites WHERE name = 'PUBLIC' LIMIT 1;
    SELECT id INTO model_accessibility_private_id FROM const_model_accessibilites WHERE name = 'PRIVATE' LIMIT 1;
    SELECT id INTO training_status_completed_id FROM const_training_statuses WHERE name = 'COMPLETED' LIMIT 1;
    SELECT id INTO training_status_running_id FROM const_training_statuses WHERE name = 'RUNNING' LIMIT 1;
    SELECT id INTO training_status_failed_id FROM const_training_statuses WHERE name = 'FAILED' LIMIT 1;

    -- Get category IDs
    SELECT id INTO category_classification_id FROM categories WHERE name = 'Classification' LIMIT 1;
    SELECT id INTO category_regression_id FROM categories WHERE name = 'Regression' LIMIT 1;
    SELECT id INTO category_clustering_id FROM categories WHERE name = 'Clustering' LIMIT 1;
    SELECT id INTO category_text_classification_id FROM categories WHERE name = 'Text Classification' LIMIT 1;
    SELECT id INTO category_sentiment_analysis_id FROM categories WHERE name = 'Sentiment Analysis' LIMIT 1;
    SELECT id INTO category_image_classification_id FROM categories WHERE name = 'Image Classification' LIMIT 1;
    SELECT id INTO category_object_detection_id FROM categories WHERE name = 'Object Detection' LIMIT 1;
    SELECT id INTO category_cnn_id FROM categories WHERE name = 'Convolutional Networks' LIMIT 1;
    SELECT id INTO category_rnn_id FROM categories WHERE name = 'Recurrent Networks' LIMIT 1;
    SELECT id INTO category_transformer_id FROM categories WHERE name = 'Transformers' LIMIT 1;

    -- Check if we have required data
    IF algorithm_id IS NULL OR array_length(dataset_ids, 1) < 20 THEN
        RAISE NOTICE 'Insufficient datasets or algorithms, skipping model creation';
        RETURN;
    END IF;

    -- Model 1: Iris Classifier (Emma)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[1], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, emma_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (emma_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-01-02 10:00:00'::timestamp, '2024-01-02 10:15:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_public_id,
            'Iris Species Classifier',
            'Multi-class classification of iris flowers',
            'Trained on iris dataset with 150 samples',
            '2024-01-02 10:15:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-01-03 09:00:00'::timestamp AT TIME ZONE 'UTC',
            category_classification_id, 0, 'models/iris-classifier-v1.model', 'metrics/iris-classifier-v1.json');

    -- Model 2: Customer Segmentation (Emma)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[2], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, emma_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (emma_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-01-16 11:00:00'::timestamp, '2024-01-16 12:30:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_private_id,
            'Customer Segmentation Model',
            'K-means clustering for customer groups',
            'Customer purchase patterns and demographics',
            '2024-01-16 12:30:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-01-18 10:00:00'::timestamp AT TIME ZONE 'UTC',
            category_clustering_id, 0, 'models/customer-segments-v1.model', 'metrics/customer-segments-v1.json');

    -- Model 3: MNIST Digit Recognizer (Emma)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[3], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, emma_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (emma_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-02-02 09:00:00'::timestamp, '2024-02-02 11:45:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_public_id,
            'MNIST Digit Recognizer',
            'Convolutional neural network for digit classification',
            'MNIST dataset with 60k training images',
            '2024-02-02 11:45:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-02-05 14:00:00'::timestamp AT TIME ZONE 'UTC',
            category_cnn_id, 0, 'models/mnist-cnn-v1.model', 'metrics/mnist-cnn-v1.json');

    -- Model 4: Product Review Sentiment (Emma)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[4], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, emma_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (emma_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-02-11 14:00:00'::timestamp, '2024-02-11 15:30:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_public_id,
            'Product Review Sentiment Analyzer',
            'Binary sentiment classification for product reviews',
            'Amazon reviews with positive/negative labels',
            '2024-02-11 15:30:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-02-12 10:00:00'::timestamp AT TIME ZONE 'UTC',
            category_sentiment_analysis_id, 0, 'models/review-sentiment-v1.model', 'metrics/review-sentiment-v1.json');

    -- Model 5: Stock Price Predictor (Emma)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[5], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, emma_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (emma_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-03-02 08:30:00'::timestamp, '2024-03-02 10:00:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_private_id,
            'Stock Price Predictor',
            'LSTM for time series stock price forecasting',
            'Historical stock data with technical indicators',
            '2024-03-02 10:00:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-03-04 11:00:00'::timestamp AT TIME ZONE 'UTC',
            category_rnn_id, 0, 'models/stock-predictor-v1.model', 'metrics/stock-predictor-v1.json');

    -- Model 6: CIFAR-10 Image Classifier (David)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[6], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, david_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (david_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-03-16 10:00:00'::timestamp, '2024-03-16 14:30:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_public_id,
            'CIFAR-10 Classifier',
            'Deep CNN for 10-class image classification',
            'CIFAR-10 dataset with 60k color images',
            '2024-03-16 14:30:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-03-18 09:00:00'::timestamp AT TIME ZONE 'UTC',
            category_image_classification_id, 0, 'models/cifar10-classifier-v1.model', 'metrics/cifar10-classifier-v1.json');

    -- Model 7: House Price Estimator (David)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[7], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, david_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (david_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-03-21 11:00:00'::timestamp, '2024-03-21 12:15:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_public_id,
            'House Price Estimator',
            'Gradient boosting for house price regression',
            'Boston housing features and prices',
            '2024-03-21 12:15:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-03-22 10:00:00'::timestamp AT TIME ZONE 'UTC',
            category_regression_id, 0, 'models/house-prices-v1.model', 'metrics/house-prices-v1.json');

    -- Model 8: Medical Diagnosis Assistant (David)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[8], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, david_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (david_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-04-06 09:30:00'::timestamp, '2024-04-06 11:45:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_private_id,
            'Medical Diagnosis Assistant',
            'Random forest for disease prediction',
            'Anonymized patient medical records',
            '2024-04-06 11:45:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-04-08 14:00:00'::timestamp AT TIME ZONE 'UTC',
            category_classification_id, 0, 'models/medical-diagnosis-v1.model', 'metrics/medical-diagnosis-v1.json');

    -- Model 9: Twitter Sentiment Monitor (David)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[9], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, david_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (david_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-04-11 13:00:00'::timestamp, '2024-04-11 15:30:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_public_id,
            'Twitter Sentiment Monitor',
            'Real-time sentiment analysis for social media',
            'Labeled Twitter posts with sentiment scores',
            '2024-04-11 15:30:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-04-12 10:00:00'::timestamp AT TIME ZONE 'UTC',
            category_sentiment_analysis_id, 0, 'models/twitter-sentiment-v1.model', 'metrics/twitter-sentiment-v1.json');

    -- Model 10: Credit Default Predictor (David)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[10], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, david_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (david_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-05-02 10:00:00'::timestamp, '2024-05-02 12:00:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_private_id,
            'Credit Default Predictor',
            'Binary classification for credit default risk',
            'Credit card transaction and payment history',
            '2024-05-02 12:00:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-05-03 11:00:00'::timestamp AT TIME ZONE 'UTC',
            category_classification_id, 0, 'models/credit-default-v1.model', 'metrics/credit-default-v1.json');

    -- Model 11: ImageNet Transfer Learning (bigspy)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[11], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, bigspy_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (bigspy_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-05-16 09:00:00'::timestamp, '2024-05-16 18:30:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_public_id,
            'ImageNet Transfer Model',
            'Pre-trained ResNet fine-tuned on custom dataset',
            'ImageNet subset with custom classes',
            '2024-05-16 18:30:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-05-18 10:00:00'::timestamp AT TIME ZONE 'UTC',
            category_cnn_id, 0, 'models/imagenet-transfer-v1.model', 'metrics/imagenet-transfer-v1.json');

    -- Model 12: Wine Quality Classifier (bigspy)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[12], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, bigspy_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (bigspy_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-05-21 11:00:00'::timestamp, '2024-05-21 11:45:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_public_id,
            'Wine Quality Classifier',
            'Multi-class classification of wine quality ratings',
            'Chemical properties and quality scores',
            '2024-05-21 11:45:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-05-22 09:00:00'::timestamp AT TIME ZONE 'UTC',
            category_classification_id, 0, 'models/wine-quality-v1.model', 'metrics/wine-quality-v1.json');

    -- Model 13: News Topic Classifier (bigspy)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[13], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, bigspy_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (bigspy_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-06-02 10:00:00'::timestamp, '2024-06-02 13:15:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_public_id,
            'News Topic Classifier',
            'Multi-label classification for news categories',
            'News articles with topic labels',
            '2024-06-02 13:15:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-06-03 10:00:00'::timestamp AT TIME ZONE 'UTC',
            category_text_classification_id, 0, 'models/news-topics-v1.model', 'metrics/news-topics-v1.json');

    -- Model 14: Sales Forecaster (bigspy)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[14], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, bigspy_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (bigspy_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-06-16 14:00:00'::timestamp, '2024-06-16 15:30:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_private_id,
            'Sales Forecaster',
            'Time series prediction with Prophet',
            'Retail sales data with seasonal trends',
            '2024-06-16 15:30:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-06-17 10:00:00'::timestamp AT TIME ZONE 'UTC',
            category_regression_id, 0, 'models/sales-forecast-v1.model', 'metrics/sales-forecast-v1.json');

    -- Model 15: Face Recognition System (bigspy) - IN PROGRESS
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[15], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, bigspy_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (bigspy_id, dataset_config_id, algo_config_id, training_status_running_id,
            '2024-07-02 10:00:00'::timestamp, NULL)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_inprogress_id, model_accessibility_private_id,
            'Face Recognition System',
            'Deep learning model for facial recognition',
            'Facial images with identity labels',
            '2024-07-02 10:00:00'::timestamp AT TIME ZONE 'UTC',
            false,
            NULL,
            category_cnn_id, 0, NULL, NULL);

    -- Model 16: Spam Email Detector (nickriz)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[16], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, nickriz_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (nickriz_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-07-16 10:00:00'::timestamp, '2024-07-16 11:30:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_public_id,
            'Spam Email Detector',
            'Naive Bayes classifier for spam detection',
            'Email corpus with spam/ham labels',
            '2024-07-16 11:30:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-07-17 09:00:00'::timestamp AT TIME ZONE 'UTC',
            category_text_classification_id, 0, 'models/spam-detector-v1.model', 'metrics/spam-detector-v1.json');

    -- Model 17: Network Intrusion Detector (nickriz)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[17], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, nickriz_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (nickriz_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-08-02 12:00:00'::timestamp, '2024-08-02 14:45:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_private_id,
            'Network Intrusion Detector',
            'Anomaly detection for network security',
            'Network traffic patterns with attack labels',
            '2024-08-02 14:45:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-08-03 10:00:00'::timestamp AT TIME ZONE 'UTC',
            category_classification_id, 0, 'models/intrusion-detector-v1.model', 'metrics/intrusion-detector-v1.json');

    -- Model 18: Fashion Item Classifier (nickriz)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[18], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, nickriz_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (nickriz_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-08-16 09:00:00'::timestamp, '2024-08-16 11:30:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_public_id,
            'Fashion Item Classifier',
            'CNN for clothing and accessory classification',
            'Fashion MNIST with 10 clothing categories',
            '2024-08-16 11:30:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-08-17 10:00:00'::timestamp AT TIME ZONE 'UTC',
            category_image_classification_id, 0, 'models/fashion-classifier-v1.model', 'metrics/fashion-classifier-v1.json');

    -- Model 19: Diabetes Risk Predictor (nickriz)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[19], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, nickriz_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (nickriz_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-09-02 13:00:00'::timestamp, '2024-09-02 13:45:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_public_id,
            'Diabetes Risk Predictor',
            'Logistic regression for diabetes prediction',
            'Medical indicators and diabetes outcomes',
            '2024-09-02 13:45:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-09-03 10:00:00'::timestamp AT TIME ZONE 'UTC',
            category_classification_id, 0, 'models/diabetes-predictor-v1.model', 'metrics/diabetes-predictor-v1.json');

    -- Model 20: Large Scale Sentiment Analyzer (nickriz)
    INSERT INTO dataset_configurations (dataset_id, status, upload_date)
    VALUES (dataset_ids[20], 'DEFAULT', NOW())
    RETURNING id INTO dataset_config_id;

    INSERT INTO algorithm_configurations (algorithm_id, algorithm_type_id, user_id)
    VALUES (algorithm_id, algorithm_type_id, nickriz_id)
    RETURNING id INTO algo_config_id;

    INSERT INTO trainings (user_id, dataset_id, algorithm_configuration_id, status_id, started_date, finished_date)
    VALUES (nickriz_id, dataset_config_id, algo_config_id, training_status_completed_id,
            '2024-09-16 08:00:00'::timestamp, '2024-09-16 16:30:00'::timestamp)
    RETURNING id INTO training_id;

    INSERT INTO models (training_id, model_type_id, status_id, accessibility_id, model_name, model_description,
                        data_description, created_at, finalized, finalization_date, category_id, version, model_url, metrics_url)
    VALUES (training_id, model_type_id, model_status_finished_id, model_accessibility_public_id,
            'Large Scale Sentiment Analyzer',
            'Transformer-based sentiment classification',
            'Sentiment140 with 1.6M labeled tweets',
            '2024-09-16 16:30:00'::timestamp AT TIME ZONE 'UTC',
            true,
            '2024-09-18 10:00:00'::timestamp AT TIME ZONE 'UTC',
            category_transformer_id, 0, 'models/sentiment140-v1.model', 'metrics/sentiment140-v1.json');

END $$;

-- ============================================
-- Summary
-- ============================================
-- Added:
-- - 10 new categories (5 parent + 5 child)
-- - 20 datasets with varied sizes, types, and accessibility
-- - 20 models with 20 corresponding trainings
-- - Distributed across 4 users (emma, david, bigspy, nickriz)
-- - Mix of completed (19) and in-progress (1) trainings
-- - Dates spanning from Jan 2024 to Sep 2024
-- ============================================
