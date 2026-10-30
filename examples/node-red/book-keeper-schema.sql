-- Book Keeper (Node-RED + Telegram): dados do bot num schema próprio, no mesmo PostgreSQL do Agent Server.
-- psql "$DATABASE_URL" -f book-keeper-schema.sql
CREATE SCHEMA IF NOT EXISTS bookkeeper;

-- Usuários do Telegram e a thread do Agent Server de cada um
CREATE TABLE IF NOT EXISTS bookkeeper.users (
    id          bigserial   PRIMARY KEY,
    telegram_id bigint      NOT NULL UNIQUE,
    name        text,
    thread_id   text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

-- Catálogo consultado pela tool get_books (fictício)
CREATE TABLE IF NOT EXISTS bookkeeper.books (
    id       bigserial PRIMARY KEY,
    title    text          NOT NULL,
    author   text          NOT NULL,
    category text          NOT NULL,
    price    numeric(10,2) NOT NULL
);

INSERT INTO bookkeeper.books (title, author, category, price)
SELECT * FROM (VALUES
    ('A Torre das Brumas', 'Autor Exemplo A', 'fantasia', 59.90),
    ('O Último Dragão do Norte', 'Autor Exemplo B', 'fantasia', 72.50),
    ('Crime na Rua das Flores', 'Autor Exemplo C', 'suspense', 44.00),
    ('Estrelas de Outra Era', 'Autor Exemplo D', 'ficção científica', 65.00)
) AS v(title, author, category, price)
WHERE NOT EXISTS (SELECT 1 FROM bookkeeper.books);
