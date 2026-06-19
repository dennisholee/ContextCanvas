CREATE TABLE IF NOT EXISTS contacts (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id  INTEGER NOT NULL REFERENCES clients(id),
    full_name  TEXT    NOT NULL,
    title      TEXT,
    email      TEXT,
    phone      TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);