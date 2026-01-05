-- V16: Fix datasets sequence that got out of sync
-- This ensures the next INSERT will use the correct ID

SELECT setval('datasets_id_seq', COALESCE((SELECT MAX(id) FROM datasets), 1), true);

-- Log the result
DO $$
BEGIN
    RAISE NOTICE '✅ datasets_id_seq fixed to: %', (SELECT last_value FROM datasets_id_seq);
END $$;
