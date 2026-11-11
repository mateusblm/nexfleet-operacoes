CREATE TABLE operacoes.hodometro (
  veiculo_id UUID PRIMARY KEY,
  quilometragem BIGINT NOT NULL CHECK (quilometragem >= 0),
  atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE operacoes.abastecimento (
  id UUID PRIMARY KEY,
  veiculo_id UUID NOT NULL,
  motorista_id UUID NOT NULL,
  realizado_em TIMESTAMPTZ NOT NULL,
  combustivel VARCHAR(40) NOT NULL,
  litros NUMERIC(12,2) NOT NULL CHECK (litros > 0),
  valor_total NUMERIC(12,2) NOT NULL CHECK (valor_total > 0),
  quilometragem BIGINT NOT NULL CHECK (quilometragem > 0),
  criado_por UUID NOT NULL,
  criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_abastecimento_veiculo_data
  ON operacoes.abastecimento (veiculo_id, realizado_em DESC, id);

CREATE TABLE operacoes.outbox_evento (
  id UUID PRIMARY KEY,
  agregado_tipo VARCHAR(80) NOT NULL,
  agregado_id UUID NOT NULL,
  evento_tipo VARCHAR(120) NOT NULL,
  evento_versao INTEGER NOT NULL CHECK (evento_versao > 0),
  payload JSONB NOT NULL,
  ocorrido_em TIMESTAMPTZ NOT NULL,
  publicado_em TIMESTAMPTZ,
  tentativas INTEGER NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
  ultimo_erro VARCHAR(500)
);

CREATE INDEX ix_outbox_pendente
  ON operacoes.outbox_evento (ocorrido_em, id)
  WHERE publicado_em IS NULL;
