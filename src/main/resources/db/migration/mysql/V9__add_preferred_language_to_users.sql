ALTER TABLE users
    ADD COLUMN preferred_language VARCHAR(10) NOT NULL DEFAULT 'en' AFTER role;

UPDATE users
SET preferred_language = 'en'
WHERE preferred_language IS NULL OR TRIM(preferred_language) = '';