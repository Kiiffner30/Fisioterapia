-- V3: Registro de auditoria (acciones sensibles)
CREATE TABLE audit_logs (
    id             UUID         PRIMARY KEY,
    user_id        UUID,
    action         VARCHAR(80)  NOT NULL,
    entity         VARCHAR(120) NOT NULL,
    entity_id      VARCHAR(120),
    payload_before JSONB,
    payload_after  JSONB,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_user ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);