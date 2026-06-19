CREATE TABLE IF NOT EXISTS sales (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id   INTEGER NOT NULL REFERENCES clients(id),
    deal_amount REAL    NOT NULL,
    stage       TEXT    CHECK(stage IN ('pipeline', 'negotiation', 'closed_won', 'closed_lost')) DEFAULT 'pipeline',
    close_date  DATE,
    owner       TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);