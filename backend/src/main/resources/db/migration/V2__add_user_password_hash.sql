-- =====================================================================
-- V2__add_user_password_hash.sql
-- Adds password storage to app_user to support Stage 1 authentication.
-- =====================================================================

ALTER TABLE app_user
    ADD COLUMN password_hash varchar(255) NOT NULL DEFAULT '';

ALTER TABLE app_user
    ALTER COLUMN password_hash DROP DEFAULT;

