-- Disable legacy public demo credentials. An isolated demo environment can
-- explicitly re-enable/rotate them through DEMO_SEED_ENABLED/DEMO_PASSWORD.
UPDATE users
SET is_active = FALSE
WHERE LOWER(email) IN (
    'action.demo@example.com',
    'comedy.demo@example.com',
    'new.demo@example.com'
);

UPDATE users
SET is_active = FALSE
WHERE LOWER(email) LIKE '%@seed.com';

-- Normalize historical outliers before enforcing half-star rating steps.
UPDATE ratings
SET rating = LEAST(5.0, GREATEST(0.5, ROUND(rating * 2) / 2.0));

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_ratings_half_step') THEN
        ALTER TABLE ratings ADD CONSTRAINT ck_ratings_half_step
            CHECK (rating >= 0.5 AND rating <= 5.0 AND MOD(rating * 10, 5) = 0);
    END IF;
END $$;
