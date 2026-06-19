CREATE TABLE IF NOT EXISTS clients (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    company_name TEXT    NOT NULL,
    industry     TEXT,
    status       TEXT    CHECK(status IN ('active', 'inactive', 'lead')) DEFAULT 'lead',
    contact_email TEXT,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);