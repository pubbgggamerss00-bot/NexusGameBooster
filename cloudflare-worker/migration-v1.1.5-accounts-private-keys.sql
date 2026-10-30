-- Nexus Game Booster: account-bound private/public license migration
-- Back up D1 before applying. Run once on the existing database.
CREATE TABLE IF NOT EXISTS customer_accounts (
  account_code TEXT PRIMARY KEY,
  device_hash TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL
);
ALTER TABLE license_keys ADD COLUMN key_plain TEXT;
ALTER TABLE license_keys ADD COLUMN key_type TEXT NOT NULL DEFAULT 'public';
ALTER TABLE license_keys ADD COLUMN account_code TEXT;
CREATE INDEX IF NOT EXISTS idx_customer_accounts_device ON customer_accounts(device_hash);
CREATE INDEX IF NOT EXISTS idx_license_account_code ON license_keys(account_code);
