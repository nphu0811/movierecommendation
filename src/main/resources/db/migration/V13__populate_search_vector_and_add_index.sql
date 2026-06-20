-- Ensure search_vector is fully populated for all existing movies
UPDATE movies SET search_vector = to_tsvector('english', COALESCE(title, '') || ' ' || COALESCE(description, ''));

-- Create the GIN index for search_vector if it does not already exist
CREATE INDEX IF NOT EXISTS idx_movies_search_vector ON movies USING gin(search_vector);
