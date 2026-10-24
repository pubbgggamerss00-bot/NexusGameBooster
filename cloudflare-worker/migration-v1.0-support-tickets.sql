-- Nexus Game Booster v1.0 support-ticket migration
-- Run once in Cloudflare D1 Console against nexus-gamebooster-db.
CREATE TABLE IF NOT EXISTS support_tickets (
  id TEXT PRIMARY KEY, license_id TEXT NOT NULL, device_hash TEXT NOT NULL, key_hint TEXT NOT NULL,
  subject TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'open', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_support_tickets_license_device ON support_tickets(license_id, device_hash, updated_at);
CREATE INDEX IF NOT EXISTS idx_support_tickets_status_updated ON support_tickets(status, updated_at);
CREATE TABLE IF NOT EXISTS support_ticket_messages (
  id TEXT PRIMARY KEY, ticket_id TEXT NOT NULL, sender TEXT NOT NULL CHECK(sender IN ('user','admin')),
  message TEXT NOT NULL, created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_support_ticket_messages_ticket ON support_ticket_messages(ticket_id, created_at);
