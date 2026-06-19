-- Core schema for a clean PostgreSQL database. Hibernate currently expands
-- non-core feature tables after Flyway; all integrity-critical tables live here.

CREATE TABLE IF NOT EXISTS users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS genres (
    genre_id SERIAL PRIMARY KEY,
    genre_name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS movies (
    movie_id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    release_year INT,
    poster_url VARCHAR(255),
    trailer_key VARCHAR(50),
    backdrop_url VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS movie_genres (
    movie_id INT NOT NULL REFERENCES movies(movie_id) ON DELETE CASCADE,
    genre_id INT NOT NULL REFERENCES genres(genre_id) ON DELETE CASCADE,
    PRIMARY KEY (movie_id, genre_id)
);

CREATE TABLE IF NOT EXISTS ratings (
    rating_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    movie_id INT NOT NULL REFERENCES movies(movie_id) ON DELETE CASCADE,
    rating NUMERIC(2,1) NOT NULL CHECK (rating BETWEEN 0.5 AND 5.0),
    rated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, movie_id)
);

CREATE TABLE IF NOT EXISTS watch_history (
    history_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    movie_id INT NOT NULL REFERENCES movies(movie_id) ON DELETE CASCADE,
    watched_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, movie_id)
);

CREATE TABLE IF NOT EXISTS watchlist (
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    movie_id INT NOT NULL REFERENCES movies(movie_id) ON DELETE CASCADE,
    added_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, movie_id)
);

CREATE TABLE IF NOT EXISTS comments (
    comment_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    movie_id INT NOT NULL REFERENCES movies(movie_id) ON DELETE CASCADE,
    comment_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS links (
    movie_id INT PRIMARY KEY REFERENCES movies(movie_id) ON DELETE CASCADE,
    imdb_id VARCHAR(20),
    tmdb_id INT
);

CREATE TABLE IF NOT EXISTS tags (
    tag_id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    movie_id INT NOT NULL REFERENCES movies(movie_id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, movie_id, tag)
);

CREATE TABLE IF NOT EXISTS search_history (
    search_id BIGSERIAL PRIMARY KEY,
    user_id INT REFERENCES users(user_id) ON DELETE SET NULL,
    search_query VARCHAR(255) NOT NULL,
    normalized_query VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
