-- V22: garantiza unicidad case-insensitive en usuarios.email en PostgreSQL
-- Evita creación concurrente o manual de duplicados solo diferenciados por mayúsculas/minúsculas.
CREATE UNIQUE INDEX IF NOT EXISTS uk_usuarios_email_lower ON usuarios (lower(email));
