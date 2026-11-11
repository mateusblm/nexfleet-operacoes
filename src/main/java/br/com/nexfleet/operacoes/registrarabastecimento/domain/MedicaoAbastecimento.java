package br.com.nexfleet.operacoes.registrarabastecimento.domain;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Combustível, litros, valor e quilometragem de um abastecimento, sempre válidos: combustível
 * sem espaços nas pontas e em maiúsculas, litros e valor positivos com no máximo duas casas e
 * quilometragem positiva.
 */
public record MedicaoAbastecimento(String combustivel, BigDecimal litros, BigDecimal valorTotal,
    long quilometragem) {

  public static final String PREENCHA = "Preencha todos os dados do abastecimento.";

  public MedicaoAbastecimento {
    if (incompleta(combustivel, litros, valorTotal, quilometragem))
      throw new AbastecimentoInvalido(PREENCHA);
    validarDecimal(litros, "Litros");
    validarDecimal(valorTotal, "Valor total");
    if (quilometragem <= 0)
      throw new AbastecimentoInvalido("A quilometragem deve ser positiva.");
    combustivel = combustivel.trim().toUpperCase(Locale.ROOT);
  }

  /** Valida a quilometragem informada (pode faltar) antes das demais regras. */
  public static MedicaoAbastecimento de(String combustivel, BigDecimal litros,
      BigDecimal valorTotal, Long quilometragem) {
    if (quilometragem == null) throw new AbastecimentoInvalido(PREENCHA);
    return new MedicaoAbastecimento(combustivel, litros, valorTotal, quilometragem);
  }

  /** Falta algum dado obrigatório ou o combustível passa de 40 caracteres. */
  static boolean incompleta(String combustivel, BigDecimal litros, BigDecimal valorTotal,
      Long quilometragem) {
    return combustivel == null || combustivel.isBlank() || combustivel.trim().length() > 40
        || litros == null || valorTotal == null || quilometragem == null;
  }

  private static void validarDecimal(BigDecimal valor, String campo) {
    if (valor.signum() <= 0 || valor.scale() > 2 || valor.precision() - valor.scale() > 10)
      throw new AbastecimentoInvalido(
          campo + " deve ser positivo e possuir no máximo duas casas decimais.");
  }
}
