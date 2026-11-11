package br.com.nexfleet.operacoes.registrarmanutencao.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados editáveis de uma manutenção, sempre válidos: descrição e oficina sem espaços nas pontas,
 * quilometragem não negativa, valor com no máximo duas casas e próxima manutenção posterior à
 * realizada.
 */
public record DetalhesManutencao(TipoManutencao tipo, LocalDate realizadaEm, long quilometragem,
    String descricao, String oficina, BigDecimal valor, LocalDate proximaManutencao) {

  public static final String PREENCHA = "Preencha todos os dados da manutenção.";

  public DetalhesManutencao {
    if (tipo == null || realizadaEm == null || descricao == null || descricao.isBlank()
        || oficina == null || oficina.isBlank() || valor == null)
      throw new ManutencaoInvalida(PREENCHA);
    if (quilometragem < 0)
      throw new ManutencaoInvalida("A quilometragem não pode ser negativa.");
    descricao = descricao.trim();
    oficina = oficina.trim();
    if (descricao.length() > 500 || oficina.length() > 150)
      throw new ManutencaoInvalida("Descrição ou oficina excede o limite permitido.");
    if (valor.signum() < 0 || valor.scale() > 2 || valor.precision() - valor.scale() > 10)
      throw new ManutencaoInvalida(
          "O valor não pode ser negativo e deve ter no máximo duas casas decimais.");
    if (proximaManutencao != null && !proximaManutencao.isAfter(realizadaEm))
      throw new ManutencaoInvalida(
          "A próxima manutenção deve ser posterior à manutenção registrada.");
  }

  /** Valida a quilometragem informada (pode faltar) antes das demais regras. */
  public static DetalhesManutencao de(TipoManutencao tipo, LocalDate realizadaEm,
      Long quilometragem, String descricao, String oficina, BigDecimal valor,
      LocalDate proximaManutencao) {
    if (quilometragem == null) throw new ManutencaoInvalida(PREENCHA);
    return new DetalhesManutencao(tipo, realizadaEm, quilometragem, descricao, oficina, valor,
        proximaManutencao);
  }
}
