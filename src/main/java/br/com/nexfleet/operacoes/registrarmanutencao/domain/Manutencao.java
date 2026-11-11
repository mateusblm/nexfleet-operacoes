package br.com.nexfleet.operacoes.registrarmanutencao.domain;

import java.util.UUID;

/** Regras do cadastro e da versão da manutenção. */
public final class Manutencao {
  /** Versão de uma manutenção recém-registrada. */
  public static final int VERSAO_INICIAL = 1;

  private Manutencao() {}

  /** O cadastro exige o veículo; os demais dados seguem as regras de {@link DetalhesManutencao}. */
  public static void exigirVeiculo(UUID veiculoId) {
    if (veiculoId == null) throw new ManutencaoInvalida(DetalhesManutencao.PREENCHA);
  }

  /** Versão que a manutenção passa a ter após uma edição ou exclusão. */
  public static int versaoSeguinte(int versaoAtual) {
    return versaoAtual + 1;
  }
}
