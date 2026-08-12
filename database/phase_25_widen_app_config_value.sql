-- Widen config_value to hold large JSON payloads (e.g. ML model allocation arrays).
-- Previous VARCHAR(255) truncates the 7-model allocation JSON (~500+ chars).

ALTER TABLE dbo.app_config
    ALTER COLUMN config_value NVARCHAR(MAX) NOT NULL;
