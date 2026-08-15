-- Runs only when the named PostgreSQL volume is first initialized.
-- Application schema changes remain owned by Flyway migrations.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;
