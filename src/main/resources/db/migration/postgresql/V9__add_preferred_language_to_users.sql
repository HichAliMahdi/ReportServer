ALTER TABLE users
    ADD COLUMN preferred_language VARCHAR(10) NOT NULL DEFAULT 'en';

UPDATE users
SET preferred_language = 'en'
WHERE preferred_language IS NULL OR BTRIM(preferred_language) = '';