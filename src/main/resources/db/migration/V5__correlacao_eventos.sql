ALTER TABLE operacoes.outbox_evento
  ADD COLUMN correlation_id UUID;

UPDATE operacoes.outbox_evento
SET correlation_id = id
WHERE correlation_id IS NULL;

ALTER TABLE operacoes.outbox_evento
  ALTER COLUMN correlation_id SET NOT NULL;

CREATE INDEX ix_outbox_correlation_id
  ON operacoes.outbox_evento (correlation_id);
