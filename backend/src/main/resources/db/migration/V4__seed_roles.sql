-- V4: Perfiles iniciales (ADMIN, ATENDENTE, FISIOTERAPEUTA)
-- El usuario administrador inicial se crea en el arranque a partir de variables
-- de entorno (BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD) para no versionar
-- credenciales en el repositorio.
INSERT INTO roles (name)
VALUES ('ADMIN'), ('ATENDENTE'), ('FISIOTERAPEUTA')
ON CONFLICT (name) DO NOTHING;