-- Read-only checks for Nexus Game Booster 2.2.1.
-- This script does not modify or delete any data.
PRAGMA table_info(customer_accounts);
PRAGMA table_info(license_keys);
SELECT COUNT(*) AS registered_accounts FROM customer_accounts;
SELECT COUNT(*) AS total_keys,
       SUM(CASE WHEN key_type='private' THEN 1 ELSE 0 END) AS private_keys,
       SUM(CASE WHEN key_type='public' THEN 1 ELSE 0 END) AS public_keys
FROM license_keys;
