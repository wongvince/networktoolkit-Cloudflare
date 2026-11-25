CREATE TABLE IF NOT EXISTS trial_devices (
  device_hash TEXT PRIMARY KEY NOT NULL,
  issued_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_trial_devices_created_at
  ON trial_devices(created_at);
