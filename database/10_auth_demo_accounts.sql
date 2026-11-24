-- Demo accounts for local authentication testing. Apply as a database administrator
-- after migration 09_users.sql. All three use password: Teste@2026!
-- Reapplying this seed does not overwrite an existing account's state or password.
INSERT INTO users (id, name, email, password, update_time, entry_time, role) VALUES
('10000000-0000-4000-8000-000000000001', 'Conta Inativa', 'inativo@marketcontrol.local', '$2a$12$g33yJh5Ay8MPeCsQkmku6u7MuEYBgsWlBIBbivRekTtt3SiHTJOZu', NULL, CURRENT_TIMESTAMP, 1),
('10000000-0000-4000-8000-000000000002', 'Administrador de Teste', 'admin@marketcontrol.local', '$2a$12$g33yJh5Ay8MPeCsQkmku6u7MuEYBgsWlBIBbivRekTtt3SiHTJOZu', NULL, CURRENT_TIMESTAMP, 2),
('10000000-0000-4000-8000-000000000003', 'Operador de Teste', 'operador@marketcontrol.local', '$2a$12$g33yJh5Ay8MPeCsQkmku6u7MuEYBgsWlBIBbivRekTtt3SiHTJOZu', NULL, CURRENT_TIMESTAMP, 3)
ON DUPLICATE KEY UPDATE id = id;
