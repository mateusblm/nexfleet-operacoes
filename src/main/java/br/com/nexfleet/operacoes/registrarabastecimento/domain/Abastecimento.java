package br.com.nexfleet.operacoes.registrarabastecimento.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Regras do cadastro, do hodômetro e da versão do abastecimento. */
public final class Abastecimento {
  /** Versão de um abastecimento recém-registrado. */
  public static final int VERSAO_INICIAL = 1;

  private Abastecimento() {}

  /**
   * Valida o cadastro: todos os dados presentes, data que não está no futuro em relação a
   * {@code agora} e medição válida.
   */
  public static MedicaoAbastecimento validarCadastro(UUID veiculoId, UUID motoristaId,
      Instant realizadoEm, String combustivel, BigDecimal litros, BigDecimal valorTotal,
      Long quilometragem, Instant agora) {
    if (veiculoId == null || motoristaId == null || realizadoEm == null
        || MedicaoAbastecimento.incompleta(combustivel, litros, valorTotal, quilometragem))
      throw new AbastecimentoInvalido(MedicaoAbastecimento.PREENCHA);
    if (realizadoEm.isAfter(agora))
      throw new AbastecimentoInvalido("A data do abastecimento não pode estar no futuro.");
    return MedicaoAbastecimento.de(combustivel, litros, valorTotal, quilometragem);
  }

  /** Um novo abastecimento não pode registrar quilometragem abaixo do hodômetro do veículo. */
  public static void validarContraHodometro(Long hodometroAtual, long quilometragem) {
    if (hodometroAtual != null && quilometragem < hodometroAtual)
      throw new AbastecimentoInvalido(
          "A quilometragem não pode ser menor que o hodômetro atual de " + hodometroAtual
              + " km.");
  }

  /** Versão que o abastecimento passa a ter após uma edição ou exclusão. */
  public static int versaoSeguinte(int versaoAtual) {
    return versaoAtual + 1;
  }
}
