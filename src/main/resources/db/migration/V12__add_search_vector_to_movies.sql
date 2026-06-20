-- Migration to add search_vector column and trigger if not present (aligning with legacy v4 migration for production environment)
ALTER TABLE movies ADD COLUMN IF NOT EXISTS search_vector tsvector;

CREATE OR REPLACE FUNCTION public.update_movie_search_vector() 
RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        to_tsvector(
            'english',
            COALESCE(NEW.title, '') || ' ' ||
            COALESCE(NEW.description, '')
        );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_update_movie_search ON public.movies;
CREATE TRIGGER trg_update_movie_search BEFORE INSERT OR UPDATE ON public.movies 
FOR EACH ROW EXECUTE FUNCTION public.update_movie_search_vector();

-- Populate search_vector for existing records
UPDATE movies SET search_vector = to_tsvector('english', COALESCE(title, '') || ' ' || COALESCE(description, '')) WHERE search_vector IS NULL;
