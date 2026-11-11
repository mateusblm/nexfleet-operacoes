package br.com.nexfleet.operacoes.registrarabastecimento.domain;

/**
 * Quilometragens do abastecimento anterior e do seguinte do mesmo veículo (por data e depois
 * id); ausente quando não há vizinho. Um abastecimento editado precisa ficar estritamente entre
 * elas.
 */
public record FaixaQuilometragem(Long anterior, Long seguinte) {

  public void validar(long quilometragem) {
    if (anterior != null && quilometragem <= anterior)
      throw new AbastecimentoInvalido(
          "A quilometragem deve ser maior que a do abastecimento anterior, de "
              + anterior + " km.");
    if (seguinte != null && quilometragem >= seguinte)
      throw new AbastecimentoInvalido(
          "A quilometragem deve ser menor que a do abastecimento seguinte, de "
              + seguinte + " km.");
  }
}
