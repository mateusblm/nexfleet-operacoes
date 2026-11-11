-- Expand aditivo: versão do registro para edição e exclusão com eventos versionados.
-- Linhas existentes ficam na versão 1; imagens anteriores ignoram a coluna.

ALTER TABLE operacoes.manutencao ADD versao INT NOT NULL
  CONSTRAINT df_manutencao_versao DEFAULT 1
  CONSTRAINT ck_manutencao_versao CHECK (versao > 0);
GO

ALTER TABLE operacoes.abastecimento ADD versao INT NOT NULL
  CONSTRAINT df_abastecimento_versao DEFAULT 1
  CONSTRAINT ck_abastecimento_versao CHECK (versao > 0);
GO
