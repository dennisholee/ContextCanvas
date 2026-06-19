-- Seed data for ContextCanvas PoC demonstrations
INSERT INTO clients (id, company_name, industry, status, created_at) VALUES
    (1, 'Acme Corporation', 'Technology', 'active', '2025-01-15'),
    (2, 'BetterCloud Inc', 'Technology', 'lead', '2026-01-01'),
    (3, 'Swift Logistics', 'Logistics', 'lead', '2025-12-15'),
    (4, 'DataStream Analytics', 'Technology', 'active', '2025-06-01'),
    (5, 'GreenLeaf Partners', 'Consulting', 'inactive', '2025-03-20');

INSERT INTO sales (id, client_id, deal_amount, stage, close_date, owner, created_at) VALUES
    (1, 1, 50000, 'negotiation', '2026-09-30', 'Sarah', '2026-06-01'),
    (2, 1, 12000, 'pipeline', NULL, 'Sarah', '2026-06-15'),
    (3, 4, 75000, 'closed_won', '2026-08-01', 'Sarah', '2026-07-01'),
    (4, 2, 15000, 'pipeline', NULL, 'Sarah', '2026-01-01'),
    (5, 3, 8000, 'pipeline', NULL, 'Sarah', '2026-01-01');

INSERT INTO contacts (id, client_id, full_name, title, email) VALUES
    (1, 1, 'John Smith', 'CTO', 'john@acme.com'),
    (2, 1, 'Jane Doe', 'VP Eng', 'jane@acme.com'),
    (3, 4, 'Alice Wu', 'CEO', 'alice@datastream.io'),
    (4, 2, 'Bob Chen', 'Director', 'bob@bettercloud.io'),
    (5, 3, 'Maria Garcia', 'Ops Lead', 'maria@swiftlogistics.com');