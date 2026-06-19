-- Production cleanup and guardrails after the legacy v1-v6 scripts.

ALTER TABLE movies ADD COLUMN IF NOT EXISTS metadata_source VARCHAR(40);
ALTER TABLE movies ADD COLUMN IF NOT EXISTS metadata_verified_at TIMESTAMP;

-- Merge genre names that differ only by case or surrounding whitespace.
WITH ranked AS (
    SELECT genre_id,
           MIN(genre_id) OVER (PARTITION BY LOWER(TRIM(genre_name))) AS keeper_id
    FROM genres
), duplicate_links AS (
    SELECT mg.movie_id, r.keeper_id
    FROM movie_genres mg
    JOIN ranked r ON r.genre_id = mg.genre_id
    WHERE r.genre_id <> r.keeper_id
)
INSERT INTO movie_genres(movie_id, genre_id)
SELECT movie_id, keeper_id FROM duplicate_links
ON CONFLICT (movie_id, genre_id) DO NOTHING;

WITH ranked AS (
    SELECT genre_id,
           MIN(genre_id) OVER (PARTITION BY LOWER(TRIM(genre_name))) AS keeper_id
    FROM genres
)
DELETE FROM movie_genres mg
USING ranked r
WHERE mg.genre_id = r.genre_id AND r.genre_id <> r.keeper_id;

WITH ranked AS (
    SELECT genre_id,
           MIN(genre_id) OVER (PARTITION BY LOWER(TRIM(genre_name))) AS keeper_id
    FROM genres
)
DELETE FROM genres g
USING ranked r
WHERE g.genre_id = r.genre_id AND r.genre_id <> r.keeper_id;

UPDATE genres SET genre_name = TRIM(genre_name);

DELETE FROM movie_genres
WHERE genre_id IN (
    SELECT genre_id FROM genres
    WHERE LOWER(TRIM(genre_name)) IN ('testgenre', 'new genre')
);
DELETE FROM genres
WHERE LOWER(TRIM(genre_name)) IN ('testgenre', 'new genre');

CREATE UNIQUE INDEX IF NOT EXISTS uq_genres_name_normalized
    ON genres (LOWER(TRIM(genre_name)));

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_genres_real_name') THEN
        ALTER TABLE genres ADD CONSTRAINT ck_genres_real_name CHECK (
            LENGTH(TRIM(genre_name)) > 0
            AND LOWER(TRIM(genre_name)) NOT IN ('testgenre', 'new genre')
        );
    END IF;
END $$;

-- Duplicate external IDs are unsafe: quarantine the ambiguous mappings so the
-- importer cannot enrich the wrong movie, then enforce uniqueness going forward.
WITH duplicates AS (
    SELECT tmdb_id FROM links WHERE tmdb_id IS NOT NULL
    GROUP BY tmdb_id HAVING COUNT(*) > 1
)
UPDATE links l SET tmdb_id = NULL
FROM duplicates d WHERE l.tmdb_id = d.tmdb_id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_links_tmdb_id
    ON links (tmdb_id) WHERE tmdb_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_links_imdb_id
    ON links (imdb_id) WHERE imdb_id IS NOT NULL AND TRIM(imdb_id) <> '';

-- Repair the four visible demo records reported during review. Genre links are
-- rebuilt by title so this also fixes databases whose numeric IDs drifted.
INSERT INTO genres(genre_name)
SELECT expected_name
FROM (VALUES
    ('Action'), ('Children'), ('Comedy'), ('Crime'), ('Drama'),
    ('Science Fiction'), ('Thriller')
) AS expected(expected_name)
WHERE NOT EXISTS (
    SELECT 1 FROM genres g
    WHERE LOWER(TRIM(g.genre_name)) = LOWER(expected.expected_name)
);

WITH target_movies AS (
    SELECT movie_id FROM movies
    WHERE LOWER(TRIM(title)) IN (
        'big green, the', 'the big green',
        'shawshank redemption, the', 'the shawshank redemption',
        'matrix, the', 'the matrix'
    ) OR LOWER(TRIM(title)) LIKE 'king kong vs. godzilla%'
)
DELETE FROM movie_genres mg
USING target_movies tm
WHERE mg.movie_id = tm.movie_id;

WITH expected(movie_id, genre_name) AS (
    SELECT m.movie_id, UNNEST(
        CASE
            WHEN LOWER(TRIM(m.title)) IN ('big green, the', 'the big green')
                THEN ARRAY['Children', 'Comedy']
            WHEN LOWER(TRIM(m.title)) IN ('shawshank redemption, the', 'the shawshank redemption')
                THEN ARRAY['Crime', 'Drama']
            WHEN LOWER(TRIM(m.title)) IN ('matrix, the', 'the matrix')
                THEN ARRAY['Action', 'Science Fiction', 'Thriller']
            WHEN LOWER(TRIM(m.title)) LIKE 'king kong vs. godzilla%'
                THEN ARRAY['Action', 'Science Fiction']
            ELSE ARRAY[]::TEXT[]
        END
    )
    FROM movies m
)
INSERT INTO movie_genres(movie_id, genre_id)
SELECT e.movie_id, g.genre_id
FROM expected e JOIN genres g ON LOWER(TRIM(g.genre_name)) = LOWER(e.genre_name)
ON CONFLICT (movie_id, genre_id) DO NOTHING;

UPDATE movies SET
    description = 'A struggling youth soccer team discovers confidence and teamwork under an unconventional new coach.',
    metadata_source = 'MANUAL_CORRECTION', metadata_verified_at = NOW()
WHERE LOWER(TRIM(title)) IN ('big green, the', 'the big green');

UPDATE movies SET
    description = 'Two giant monsters clash after being drawn into a confrontation that threatens Japan.',
    metadata_source = 'MANUAL_CORRECTION', metadata_verified_at = NOW()
WHERE LOWER(TRIM(title)) LIKE 'king kong vs. godzilla%';

UPDATE movies SET
    description = 'A banker sentenced to life in prison forms an enduring friendship and quietly holds on to hope.',
    metadata_source = 'MANUAL_CORRECTION', metadata_verified_at = NOW()
WHERE LOWER(TRIM(title)) IN ('shawshank redemption, the', 'the shawshank redemption');

UPDATE movies SET
    description = 'A computer hacker discovers that his world is a simulation and joins a rebellion against its controllers.',
    metadata_source = 'MANUAL_CORRECTION', metadata_verified_at = NOW()
WHERE LOWER(TRIM(title)) IN ('matrix, the', 'the matrix');

DELETE FROM comments WHERE comment_text IS NULL OR LENGTH(TRIM(comment_text)) = 0;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_comments_content_not_blank') THEN
        ALTER TABLE comments ADD CONSTRAINT ck_comments_content_not_blank
            CHECK (LENGTH(TRIM(comment_text)) > 0);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_movies_title_lower ON movies (LOWER(title));
CREATE INDEX IF NOT EXISTS idx_movies_release_year ON movies (release_year DESC);
CREATE INDEX IF NOT EXISTS idx_movie_genres_genre_movie ON movie_genres (genre_id, movie_id);
CREATE INDEX IF NOT EXISTS idx_ratings_movie ON ratings (movie_id);
CREATE INDEX IF NOT EXISTS idx_ratings_user ON ratings (user_id);
CREATE INDEX IF NOT EXISTS idx_watch_history_user_time ON watch_history (user_id, watched_at DESC);
CREATE INDEX IF NOT EXISTS idx_search_history_query_time
    ON search_history (LOWER(search_query), created_at DESC);
