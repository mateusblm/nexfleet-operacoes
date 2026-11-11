-- Operacoes no Azure SQL / SQL Server (schema operacoes, criado pelo bootstrap do admin).
-- Recomeco limpo em T-SQL: substitui as migracoes V1..V5 do PostgreSQL.

EXEC sys.sp_addextendedproperty
  @name = N'MS_Description', @value = N'NexFleet: dados exclusivos do servico operacoes',
  @level0type = N'SCHEMA', @level0name = N'operacoes';
GO

CREATE TABLE operacoes.hodometro (
  veiculo_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_hodometro PRIMARY KEY,
  quilometragem BIGINT NOT NULL CONSTRAINT ck_hodometro_quilometragem CHECK (quilometragem >= 0),
  atualizado_em DATETIMEOFFSET(6) NOT NULL
    CONSTRAINT df_hodometro_atualizado_em DEFAULT SYSDATETIMEOFFSET()
);
GO

CREATE TABLE operacoes.abastecimento (
  id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_abastecimento PRIMARY KEY,
  veiculo_id UNIQUEIDENTIFIER NOT NULL,
  motorista_id UNIQUEIDENTIFIER NOT NULL,
  realizado_em DATETIMEOFFSET(6) NOT NULL,
  combustivel NVARCHAR(40) NOT NULL,
  litros NUMERIC(12,2) NOT NULL CONSTRAINT ck_abastecimento_litros CHECK (litros > 0),
  valor_total NUMERIC(12,2) NOT NULL CONSTRAINT ck_abastecimento_valor CHECK (valor_total > 0),
  quilometragem BIGINT NOT NULL CONSTRAINT ck_abastecimento_quilometragem CHECK (quilometragem > 0),
  criado_por UNIQUEIDENTIFIER NOT NULL,
  criado_em DATETIMEOFFSET(6) NOT NULL
    CONSTRAINT df_abastecimento_criado_em DEFAULT SYSDATETIMEOFFSET()
);
GO

CREATE INDEX ix_abastecimento_veiculo_data
  ON operacoes.abastecimento (veiculo_id, realizado_em DESC, id);
GO

CREATE TABLE operacoes.manutencao (
  id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_manutencao PRIMARY KEY,
  veiculo_id UNIQUEIDENTIFIER NOT NULL,
  tipo NVARCHAR(30) NOT NULL CONSTRAINT ck_manutencao_tipo CHECK (tipo IN (
    'PREVENTIVA','CORRETIVA','REVISAO','TROCA_OLEO','TROCA_PNEUS','OUTROS')),
  realizada_em DATE NOT NULL,
  quilometragem BIGINT NOT NULL CONSTRAINT ck_manutencao_quilometragem CHECK (quilometragem >= 0),
  descricao NVARCHAR(500) NOT NULL,
  oficina NVARCHAR(150) NOT NULL,
  valor NUMERIC(12,2) NOT NULL CONSTRAINT ck_manutencao_valor CHECK (valor >= 0),
  proxima_manutencao DATE NULL,
  criado_por UNIQUEIDENTIFIER NOT NULL,
  criado_em DATETIMEOFFSET(6) NOT NULL
    CONSTRAINT df_manutencao_criado_em DEFAULT SYSDATETIMEOFFSET(),
  CONSTRAINT ck_manutencao_proxima_data CHECK (
    proxima_manutencao IS NULL OR proxima_manutencao > realizada_em)
);
GO

CREATE INDEX ix_manutencao_veiculo_data
  ON operacoes.manutencao (veiculo_id, realizada_em DESC, id);
GO

CREATE TABLE operacoes.outbox_evento (
  id UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_outbox_evento PRIMARY KEY,
  agregado_tipo NVARCHAR(80) NOT NULL,
  agregado_id UNIQUEIDENTIFIER NOT NULL,
  evento_tipo NVARCHAR(120) NOT NULL,
  evento_versao INT NOT NULL CONSTRAINT ck_outbox_evento_versao CHECK (evento_versao > 0),
  correlation_id UNIQUEIDENTIFIER NOT NULL,
  payload NVARCHAR(MAX) NOT NULL CONSTRAINT ck_outbox_payload_json CHECK (ISJSON(payload) = 1),
  ocorrido_em DATETIMEOFFSET(6) NOT NULL,
  publicado_em DATETIMEOFFSET(6) NULL,
  tentativas INT NOT NULL
    CONSTRAINT df_outbox_tentativas DEFAULT 0
    CONSTRAINT ck_outbox_tentativas CHECK (tentativas >= 0),
  ultimo_erro NVARCHAR(500) NULL,
  proxima_tentativa_em DATETIMEOFFSET(6) NOT NULL
    CONSTRAINT df_outbox_proxima_tentativa DEFAULT SYSDATETIMEOFFSET()
);
GO

CREATE INDEX ix_outbox_pendente
  ON operacoes.outbox_evento (proxima_tentativa_em, ocorrido_em, id)
  WHERE publicado_em IS NULL;
GO

CREATE INDEX ix_outbox_correlation_id
  ON operacoes.outbox_evento (correlation_id);
GO
