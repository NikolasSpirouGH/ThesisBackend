-- ====================================================
-- Flyway Migration: Add Test Algorithms with Different Dates
-- Purpose: Create sample algorithms for testing date filtering
-- Version: V2
-- ====================================================

-- Insert algorithms with specific dates for testing
INSERT INTO custom_algorithms (name, description, accessibility_id, created_at, owner_id)
VALUES
  -- Algorithm 1: Early 2024 (Jan 15, 2024)
  ('Logistic Regression Optimizer',
   'Optimized logistic regression for binary classification',
   1, -- PUBLIC
   '2024-01-15 10:30:00',
   (SELECT id FROM users WHERE username = 'bigspy' LIMIT 1)),

  -- Algorithm 2: Mid 2024 (June 20, 2024)
  ('Decision Tree Pruner',
   'Automated decision tree with intelligent pruning',
   2, -- PRIVATE
   '2024-06-20 14:45:00',
   (SELECT id FROM users WHERE username = 'nickriz' LIMIT 1)),

  -- Algorithm 3: Recent past (Sept 10, 2025)
  ('Transformer NLP Model',
   'Advanced transformer model for natural language processing',
   1, -- PUBLIC
   '2025-09-10 09:15:00',
   (SELECT id FROM users WHERE username = 'johnken' LIMIT 1)),

  -- Algorithm 4: Last week (Oct 19, 2025)
  ('AutoEncoder Compressor',
   'Neural network autoencoder for data compression',
   2, -- PRIVATE
   '2025-10-19 16:20:00',
   (SELECT id FROM users WHERE username = 'bigspy' LIMIT 1)),

  -- Algorithm 5: Very recent (Oct 25, 2025)
  ('GAN Image Generator',
   'Generative Adversarial Network for synthetic image generation',
   1, -- PUBLIC
   '2025-10-25 11:00:00',
   (SELECT id FROM users WHERE username = 'nickriz' LIMIT 1));

-- Insert keywords for these algorithms
INSERT INTO custom_algorithm_keywords (algorithm_id, keyword)
SELECT id, unnest(ARRAY['logistic-regression', 'classification', 'optimization', 'binary'])
FROM custom_algorithms WHERE name = 'Logistic Regression Optimizer'
UNION ALL
SELECT id, unnest(ARRAY['decision-tree', 'pruning', 'classification'])
FROM custom_algorithms WHERE name = 'Decision Tree Pruner'
UNION ALL
SELECT id, unnest(ARRAY['transformer', 'nlp', 'attention', 'bert'])
FROM custom_algorithms WHERE name = 'Transformer NLP Model'
UNION ALL
SELECT id, unnest(ARRAY['autoencoder', 'compression', 'neural-network'])
FROM custom_algorithms WHERE name = 'AutoEncoder Compressor'
UNION ALL
SELECT id, unnest(ARRAY['gan', 'generative', 'image-generation', 'deep-learning'])
FROM custom_algorithms WHERE name = 'GAN Image Generator';

-- Insert algorithm images (version 1.0.0)
INSERT INTO custom_algorithm_images (custom_algorithm_id, version, is_active, uploaded_at, docker_hub_url)
SELECT id, '1.0.0', true, created_at, 'myrepo/' || lower(replace(name, ' ', '-')) || ':1.0'
FROM custom_algorithms
WHERE name IN (
    'Logistic Regression Optimizer',
    'Decision Tree Pruner',
    'Transformer NLP Model',
    'AutoEncoder Compressor',
    'GAN Image Generator'
);
