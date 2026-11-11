CREATE TABLE operacoes.manutencao (
  id UUID PRIMARY KEY,
  veiculo_id UUID NOT NULL,
  tipo VARCHAR(30) NOT NULL CHECK (tipo IN (
    'PREVENTIVA','CORRETIVA','REVISAO','TROCA_OLEO','TROCA_PNEUS','OUTROS')),
  realizada_em DATE NOT NULL,
  quilometragem BIGINT NOT NULL CHECK (quilometragem >= 0),
  descricao VARCHAR(500) NOT NULL,
  oficina VARCHAR(150) NOT NULL,
  valor NUMERIC(12,2) NOT NULL CHECK (valor >= 0),
  proxima_manutencao DATE,
  criado_por UUID NOT NULL,
  criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_manutencao_proxima_data CHECK (
    proxima_manutencao IS NULL OR proxima_manutencao > realizada_em)
);

CREATE INDEX ix_manutencao_veiculo_data
  ON operacoes.manutencao (veiculo_id, realizada_em DESC, id);
