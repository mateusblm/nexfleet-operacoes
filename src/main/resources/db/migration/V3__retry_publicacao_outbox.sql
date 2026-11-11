ALTER TABLE operacoes.outbox_evento
  ADD COLUMN proxima_tentativa_em TIMESTAMPTZ NOT NULL DEFAULT now();

DROP INDEX operacoes.ix_outbox_pendente;

CREATE INDEX ix_outbox_pendente
  ON operacoes.outbox_evento (proxima_tentativa_em, ocorrido_em, id)
  WHERE publicado_em IS NULL;
