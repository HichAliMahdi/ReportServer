ALTER TABLE installation_state
    ADD COLUMN IF NOT EXISTS default_language VARCHAR(10) NOT NULL DEFAULT 'en';
